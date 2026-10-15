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
package cmd

import (
	"bufio"
	"bytes"
	"context"
	"fmt"
	"os"
	"os/exec"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/config"
)

// The helper runs the production command parser and handlers in another OS
// process. No load/save hooks or alternative mutation algorithm are injected.
func TestConfigMutationProcessHelper(t *testing.T) {
	if os.Getenv("RMQCTL_CONFIG_PROCESS_TEST") != "1" {
		return
	}
	for i, arg := range os.Args {
		if arg == "--" {
			fmt.Println("ready")
			app := NewApp(os.Stdout, os.Stderr)
			os.Exit(app.Execute(os.Args[i+1:]))
		}
	}
	os.Exit(2)
}

func TestConfigMutationsWaitAndReloadLatestDocument(t *testing.T) {
	for _, operation := range []string{"set-context", "use-context", "delete-context"} {
		t.Run(operation, func(t *testing.T) {
			path := newTestConfigPath(t)
			store := config.NewStore()
			value := config.Context{Server: "https://studio.example.com", Credential: config.CredentialRef{AccessKeyRef: "env:AK", SecretKeyRef: "env:SK"}}
			cfg := config.Config{CurrentContext: "alpha", Contexts: map[string]config.Context{"alpha": value, "beta": value}}
			if err := store.Save(path, cfg); err != nil {
				t.Fatal(err)
			}
			locked := make(chan struct{})
			release := make(chan struct{})
			var releaseOnce sync.Once
			releaseLock := func() { releaseOnce.Do(func() { close(release) }) }
			defer releaseLock()
			parentDone := make(chan error, 1)
			go func() {
				parentDone <- store.Update(context.Background(), path, func(latest *config.Config) error {
					close(locked)
					<-release
					latest.Contexts["delta"] = value
					return nil
				})
			}()
			select {
			case <-locked:
			case err := <-parentDone:
				t.Fatalf("parent transaction: %v", err)
			case <-time.After(10 * time.Second):
				t.Fatal("parent lock timed out")
			}
			args := []string{"-test.run=^TestConfigMutationProcessHelper$", "--", "--config", path, "config", operation}
			switch operation {
			case "set-context":
				args = append(args, "gamma", "--server", value.Server, "--access-key-env", "AK", "--secret-key-env", "SK")
			case "use-context":
				args = append(args, "beta")
			case "delete-context":
				args = append(args, "alpha")
			}
			child := exec.Command(os.Args[0], args...)
			child.Env = append(os.Environ(), "RMQCTL_CONFIG_PROCESS_TEST=1", "GORACE=atexit_sleep_ms=0")
			var stderr bytes.Buffer
			child.Stderr = &stderr
			stdout, err := child.StdoutPipe()
			if err != nil {
				t.Fatal(err)
			}
			if err := child.Start(); err != nil {
				t.Fatal(err)
			}
			defer func() { _ = child.Process.Kill() }()
			scanner := bufio.NewScanner(stdout)
			ready := make(chan bool, 1)
			go func() {
				ready <- scanner.Scan() && scanner.Text() == "ready"
				for scanner.Scan() {
				}
			}()
			select {
			case ok := <-ready:
				if !ok {
					t.Fatalf("child did not start: %s", stderr.String())
				}
			case <-time.After(10 * time.Second):
				t.Fatal("child start timed out")
			}
			done := make(chan error, 1)
			go func() { done <- child.Wait() }()
			// A command must not complete while another transaction owns the
			// lock. Keep the locked update distinct from the child's mutation.
			select {
			case err := <-done:
				t.Fatalf("command completed while config transaction was locked: %v; %s", err, stderr.String())
			case <-time.After(300 * time.Millisecond):
			}
			releaseLock()
			if err := <-parentDone; err != nil {
				t.Fatal(err)
			}
			select {
			case err := <-done:
				if err != nil {
					t.Fatalf("child: %v; %s", err, stderr.String())
				}
			case <-time.After(10 * time.Second):
				t.Fatal("command did not finish after unlock")
			}
			actual, err := store.Load(path)
			if err != nil {
				t.Fatal(err)
			}
			if _, ok := actual.Contexts["delta"]; !ok {
				t.Fatal("command discarded update committed before unlock")
			}
			switch operation {
			case "set-context":
				if _, ok := actual.Contexts["gamma"]; !ok {
					t.Fatal("new context missing")
				}
			case "use-context":
				if actual.CurrentContext != "beta" {
					t.Fatalf("current context=%s", actual.CurrentContext)
				}
			case "delete-context":
				if _, ok := actual.Contexts["alpha"]; ok || actual.CurrentContext != "" {
					t.Fatalf("deleted context/current context retained: %#v", actual)
				}
			}
			raw, err := os.ReadFile(path)
			if err != nil {
				t.Fatal(err)
			}
			if !strings.Contains(string(raw), "env:AK") {
				t.Fatal("credential references were not preserved")
			}
		})
	}
}
