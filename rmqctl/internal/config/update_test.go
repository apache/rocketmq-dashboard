/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package config

import (
	"bufio"
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"testing"
	"time"

	"github.com/gofrs/flock"
)

func updateTestPath(t *testing.T) string {
	t.Helper()
	directory := t.TempDir()
	if err := os.Chmod(directory, 0o700); err != nil {
		t.Fatal(err)
	}
	return filepath.Join(directory, "config.yaml")
}

func updateTestContext() Context {
	return Context{Server: "https://studio.example.com", Credential: CredentialRef{AccessKeyRef: "env:AK", SecretKeyRef: "env:SK"}}
}

func TestUpdateRollsBackFailuresAndReleasesLock(t *testing.T) {
	for _, kind := range []string{"callback", "validation", "malformed-yaml"} {
		t.Run(kind, func(t *testing.T) {
			path := updateTestPath(t)
			store := NewStore()
			if err := store.Save(path, Config{Contexts: map[string]Context{"alpha": updateTestContext()}}); err != nil {
				t.Fatal(err)
			}
			if kind == "malformed-yaml" {
				if err := os.WriteFile(path, []byte("contexts: ["), 0o600); err != nil {
					t.Fatal(err)
				}
			}
			before, err := os.ReadFile(path)
			if err != nil {
				t.Fatal(err)
			}
			sentinel := errors.New("mutation rejected")
			invoked := false
			err = store.Update(context.Background(), path, func(cfg *Config) error {
				invoked = true
				cfg.Contexts["beta"] = updateTestContext()
				if kind == "callback" {
					return sentinel
				}
				cfg.CurrentContext = "missing"
				return nil
			})
			if err == nil {
				t.Fatal("invalid transaction succeeded")
			}
			if kind == "callback" && !errors.Is(err, sentinel) {
				t.Fatalf("callback error lost: %v", err)
			}
			if kind == "malformed-yaml" && invoked {
				t.Fatal("callback ran on malformed config")
			}
			after, err := os.ReadFile(path)
			if err != nil {
				t.Fatal(err)
			}
			if !bytes.Equal(before, after) {
				t.Fatal("failed update changed config bytes")
			}
			if kind == "malformed-yaml" {
				if err := store.Save(path, EmptyConfig()); err != nil {
					t.Fatal(err)
				}
			}
			ctx, cancel := context.WithTimeout(context.Background(), time.Second)
			defer cancel()
			if err := store.Update(ctx, path, func(cfg *Config) error { cfg.Contexts["gamma"] = updateTestContext(); return nil }); err != nil {
				t.Fatalf("lock retained after error: %v", err)
			}
		})
	}
}

func TestUpdateCancellationAndDeadlineDoNotMutate(t *testing.T) {
	path := updateTestPath(t)
	store := NewStore()
	held := flock.New(path+".lock", flock.SetPermissions(0o600))
	if err := held.Lock(); err != nil {
		t.Fatal(err)
	}
	defer held.Close()
	for _, cancelFirst := range []bool{false, true} {
		ctx, cancel := context.WithTimeout(context.Background(), 50*time.Millisecond)
		if cancelFirst {
			cancel()
		}
		invoked := false
		err := store.Update(ctx, path, func(*Config) error { invoked = true; return nil })
		cancel()
		expected := context.DeadlineExceeded
		if cancelFirst {
			expected = context.Canceled
		}
		if !errors.Is(err, expected) || invoked {
			t.Fatalf("canceled update: error=%v invoked=%v", err, invoked)
		}
	}
	if _, err := os.Stat(path); !errors.Is(err, os.ErrNotExist) {
		t.Fatalf("canceled update created config: %v", err)
	}
	if err := held.Unlock(); err != nil {
		t.Fatal(err)
	}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	err := store.Update(ctx, path, func(cfg *Config) error { cfg.Contexts["alpha"] = updateTestContext(); cancel(); return nil })
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("cancellation before save: %v", err)
	}
	if _, err := os.Stat(path); !errors.Is(err, os.ErrNotExist) {
		t.Fatal("canceled mutation was saved")
	}
}

func TestUpdateDirectoryAliasesShareLock(t *testing.T) {
	path := updateTestPath(t)
	alias := filepath.Join(t.TempDir(), "alias")
	if err := os.Symlink(filepath.Dir(path), alias); err != nil {
		t.Skipf("directory symlink unavailable: %v", err)
	}
	held := flock.New(path+".lock", flock.SetPermissions(0o600))
	if err := held.Lock(); err != nil {
		t.Fatal(err)
	}
	defer held.Close()
	ctx, cancel := context.WithTimeout(context.Background(), 50*time.Millisecond)
	defer cancel()
	err := NewStore().Update(ctx, filepath.Join(alias, filepath.Base(path)), func(*Config) error { return nil })
	if !errors.Is(err, context.DeadlineExceeded) {
		t.Fatalf("directory alias bypassed lock: %v", err)
	}
}

func TestUpdateRejectsInvalidLockAndKeepsPrivateSidecar(t *testing.T) {
	path := updateTestPath(t)
	if err := os.Mkdir(path+".lock", 0o700); err != nil {
		t.Fatal(err)
	}
	if err := NewStore().Update(context.Background(), path, func(*Config) error { return nil }); err == nil {
		t.Fatal("lock directory accepted")
	}
	if err := os.Remove(path + ".lock"); err != nil {
		t.Fatal(err)
	}
	if err := NewStore().Update(context.Background(), path, func(cfg *Config) error { cfg.Contexts["alpha"] = updateTestContext(); return nil }); err != nil {
		t.Fatal(err)
	}
	info, err := os.Stat(path + ".lock")
	if err != nil {
		t.Fatal("lock sidecar removed")
	}
	if runtime.GOOS != "windows" {
		if info.Mode().Perm() != 0o600 {
			t.Fatalf("lock permissions=%04o", info.Mode().Perm())
		}
		if err := os.Chmod(path+".lock", 0o644); err != nil {
			t.Fatal(err)
		}
		if err := NewStore().Update(context.Background(), path, func(*Config) error { return nil }); err == nil {
			t.Fatal("public lock sidecar accepted")
		}
	}
}

func TestUpdateProcessHelper(t *testing.T) {
	if os.Getenv("RMQCTL_UPDATE_PROCESS_TEST") != "1" {
		return
	}
	path := os.Getenv("RMQCTL_UPDATE_PROCESS_PATH")
	err := NewStore().Update(context.Background(), path, func(cfg *Config) error {
		cfg.Contexts["unsaved"] = updateTestContext()
		fmt.Println("locked")
		_, err := io.ReadFull(os.Stdin, make([]byte, 1))
		return err
	})
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
	os.Exit(0)
}

func TestUpdateProcessDeathReleasesLockWithoutStaleRecovery(t *testing.T) {
	path := updateTestPath(t)
	child := exec.Command(os.Args[0], "-test.run=^TestUpdateProcessHelper$")
	child.Env = append(os.Environ(), "RMQCTL_UPDATE_PROCESS_TEST=1", "RMQCTL_UPDATE_PROCESS_PATH="+path, "GORACE=atexit_sleep_ms=0")
	var stderr bytes.Buffer
	child.Stderr = &stderr
	input, err := child.StdinPipe()
	if err != nil {
		t.Fatal(err)
	}
	defer input.Close()
	stdout, err := child.StdoutPipe()
	if err != nil {
		t.Fatal(err)
	}
	if err := child.Start(); err != nil {
		t.Fatal(err)
	}
	defer func() { _ = child.Process.Kill(); _ = child.Wait() }()
	ready := make(chan bool, 1)
	go func() { scanner := bufio.NewScanner(stdout); ready <- scanner.Scan() && scanner.Text() == "locked" }()
	select {
	case ok := <-ready:
		if !ok {
			t.Fatal("child did not acquire lock")
		}
	case <-time.After(10 * time.Second):
		t.Fatal("child start timed out")
	}
	if err := child.Process.Kill(); err != nil {
		t.Fatal(err)
	}
	_ = child.Wait()
	ctx, cancel := context.WithTimeout(context.Background(), time.Second)
	defer cancel()
	if err := NewStore().Update(ctx, path, func(cfg *Config) error { cfg.Contexts["after-exit"] = updateTestContext(); return nil }); err != nil {
		t.Fatalf("dead process retained lock: %v; %s", err, stderr.String())
	}
	cfg, err := NewStore().Load(path)
	if err != nil {
		t.Fatal(err)
	}
	if len(cfg.Contexts) != 1 || cfg.Contexts["after-exit"].Server == "" {
		t.Fatalf("unexpected config after process death: %#v", cfg)
	}
}
