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
	"bytes"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"reflect"
	"runtime"
	"strings"
	"testing"
	"time"

	"go.yaml.in/yaml/v3"
)

func TestDefault(t *testing.T) {
	want := Config{
		APIVersion:     "rmq.apache.org/v1alpha1",
		CurrentContext: "default",
		Contexts: map[string]Context{
			"default": {
				Server:   "http://127.0.0.1:8888",
				Output:   "table",
				TokenEnv: "RMQCTL_TOKEN",
			},
		},
	}
	got := Default()
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("Default() = %#v, want %#v", got, want)
	}

	contextType := reflect.TypeOf(Context{})
	for _, forbidden := range []string{"Token", "Secret", "Password"} {
		if _, ok := contextType.FieldByName(forbidden); ok {
			t.Fatalf("Context must not contain a %q value field", forbidden)
		}
	}
}

func TestDefaultPath(t *testing.T) {
	home, err := os.UserHomeDir()
	if err != nil {
		t.Fatal(err)
	}
	got, err := DefaultPath()
	if err != nil {
		t.Fatalf("DefaultPath() error = %v", err)
	}
	want := filepath.Join(home, ".rmqctl", "config.yaml")
	if got != want {
		t.Fatalf("DefaultPath() = %q, want %q", got, want)
	}
}

func TestSaveLoadRoundTripAndModes(t *testing.T) {
	path := filepath.Join(t.TempDir(), "nested", "config.yaml")
	want := Default()
	want.Contexts["production"] = Context{
		Server:   "https://rmq.example.com",
		Cluster:  "prod",
		Output:   "json",
		TokenEnv: "PROD_RMQ_TOKEN",
		CAFile:   "/etc/ssl/certs/rmq.pem",
	}
	want.CurrentContext = "production"

	if err := Save(path, want, false); err != nil {
		t.Fatalf("Save() error = %v", err)
	}
	got, err := Load(path)
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("Load() = %#v, want %#v", got, want)
	}

	if runtime.GOOS != "windows" {
		parentInfo, err := os.Stat(filepath.Dir(path))
		if err != nil {
			t.Fatal(err)
		}
		if gotMode := parentInfo.Mode().Perm(); gotMode&0077 != 0 {
			t.Fatalf("parent permissions = %04o, want no group/other bits", gotMode)
		}
		fileInfo, err := os.Stat(path)
		if err != nil {
			t.Fatal(err)
		}
		if gotMode := fileInfo.Mode().Perm(); gotMode&0077 != 0 {
			t.Fatalf("file permissions = %04o, want no group/other bits", gotMode)
		}
	}
}

func TestLoadRejectsUnknownFieldsAndMultipleDocuments(t *testing.T) {
	tests := map[string]string{
		"unknown top-level field": `
apiVersion: rmq.apache.org/v1alpha1
currentContext: default
contexts:
  default:
    server: http://127.0.0.1:8888
    output: table
unexpected: true
`,
		"unknown context field": `
apiVersion: rmq.apache.org/v1alpha1
currentContext: default
contexts:
  default:
    server: http://127.0.0.1:8888
    output: table
    unexpected: true
`,
		"multiple documents": `
apiVersion: rmq.apache.org/v1alpha1
currentContext: default
contexts:
  default:
    server: http://127.0.0.1:8888
    output: table
---
apiVersion: rmq.apache.org/v1alpha1
currentContext: default
contexts:
  default:
    server: http://127.0.0.1:8888
    output: table
`,
	}

	for name, contents := range tests {
		t.Run(name, func(t *testing.T) {
			path := filepath.Join(t.TempDir(), "config.yaml")
			if err := os.WriteFile(path, []byte(contents), 0600); err != nil {
				t.Fatal(err)
			}
			if _, err := Load(path); err == nil {
				t.Fatal("Load() error = nil, want rejection")
			}
		})
	}
}

func TestValidateStructure(t *testing.T) {
	valid := Default()
	tests := map[string]Config{
		"wrong apiVersion": func() Config {
			cfg := cloneConfig(valid)
			cfg.APIVersion = "v1"
			return cfg
		}(),
		"empty contexts": func() Config {
			cfg := cloneConfig(valid)
			cfg.Contexts = map[string]Context{}
			return cfg
		}(),
		"empty context name": func() Config {
			cfg := cloneConfig(valid)
			cfg.Contexts[""] = cfg.Contexts["default"]
			return cfg
		}(),
		"missing current context": func() Config {
			cfg := cloneConfig(valid)
			cfg.CurrentContext = "missing"
			return cfg
		}(),
	}
	for name, cfg := range tests {
		t.Run(name, func(t *testing.T) {
			if err := Validate(cfg); err == nil {
				t.Fatal("Validate() error = nil, want rejection")
			}
		})
	}
}

func TestValidateOutputFormats(t *testing.T) {
	for _, output := range []string{"table", "json", "yaml"} {
		t.Run("accepts "+output, func(t *testing.T) {
			cfg := configWithContext(Context{
				Server: "https://rmq.example.com",
				Output: output,
			})
			if err := Validate(cfg); err != nil {
				t.Fatalf("Validate() error = %v", err)
			}
		})
	}
	for _, output := range []string{"", "text", "JSON"} {
		t.Run("rejects "+output, func(t *testing.T) {
			cfg := configWithContext(Context{
				Server: "https://rmq.example.com",
				Output: output,
			})
			if err := Validate(cfg); err == nil {
				t.Fatal("Validate() error = nil, want rejection")
			}
		})
	}
}

func TestValidateServer(t *testing.T) {
	valid := []string{
		"http://localhost",
		"http://localhost:8888/",
		"http://127.0.0.1",
		"http://127.42.9.3:8888",
		"http://[::1]",
		"http://[::1]:8888/",
		"https://rmq.example.com",
		"https://10.0.0.1:443/",
	}
	for _, server := range valid {
		t.Run("accepts "+server, func(t *testing.T) {
			cfg := configWithContext(Context{Server: server, Output: "table"})
			if err := Validate(cfg); err != nil {
				t.Fatalf("Validate() error = %v", err)
			}
		})
	}

	invalid := []string{
		"http://example.com",
		"http://10.0.0.1",
		"http://128.0.0.1",
		"http://[::2]",
		"http://[::ffff:127.0.0.1]",
		"https://user:pass@example.com",
		"https://@example.com",
		"https://:443",
		"https://:",
		"https://example.com/#fragment",
		"https://example.com/#",
		"https://example.com#",
		"https://example.com/?query=yes",
		"https://example.com?",
		"https://example.com/?",
		"https://example.com/api",
		"https://example.com/%23",
		"https://example.com:bad",
		"ftp://example.com",
		"example.com",
		"/relative",
		"https:///missing-host",
	}
	for _, server := range invalid {
		t.Run("rejects "+server, func(t *testing.T) {
			cfg := configWithContext(Context{Server: server, Output: "table"})
			if err := Validate(cfg); err == nil {
				t.Fatal("Validate() error = nil, want rejection")
			}
		})
	}
}

func TestValidateEncodedHashIsRejectedAsPath(t *testing.T) {
	cfg := configWithContext(Context{
		Server: "https://example.com/%23",
		Output: "table",
	})
	err := Validate(cfg)
	if err == nil {
		t.Fatal("Validate() error = nil, want rejection")
	}
	if !strings.Contains(err.Error(), "path") {
		t.Fatalf("Validate() error = %q, want path rejection", err)
	}
}

func TestValidateServerPorts(t *testing.T) {
	for _, server := range []string{
		"https://example.com",
		"https://example.com:1",
		"https://example.com:443",
		"https://example.com:65535",
		"https://[::1]:65535",
	} {
		t.Run("accepts "+server, func(t *testing.T) {
			cfg := configWithContext(Context{Server: server, Output: "table"})
			if err := Validate(cfg); err != nil {
				t.Fatalf("Validate() error = %v", err)
			}
		})
	}

	for _, server := range []string{
		"https://example.com:",
		"https://example.com:0",
		"https://example.com:65536",
		"https://example.com:99999",
		"https://example.com:bad",
		"https://[::1]:",
	} {
		t.Run("rejects "+server, func(t *testing.T) {
			cfg := configWithContext(Context{Server: server, Output: "table"})
			if err := Validate(cfg); err == nil {
				t.Fatal("Validate() error = nil, want rejection")
			}
		})
	}
}

func TestValidateCAFile(t *testing.T) {
	cfg := configWithContext(Context{
		Server: "https://rmq.example.com",
		Output: "yaml",
		CAFile: "/etc/ssl/certs/custom.pem",
	})
	if err := Validate(cfg); err != nil {
		t.Fatalf("Validate() error = %v", err)
	}
}

func TestValidateTokenEnv(t *testing.T) {
	for _, name := range []string{"", "_TOKEN", "TOKEN", "RMQ_TOKEN_2", "a"} {
		t.Run("accepts "+name, func(t *testing.T) {
			cfg := configWithContext(Context{
				Server:   "https://rmq.example.com",
				Output:   "table",
				TokenEnv: name,
			})
			if err := Validate(cfg); err != nil {
				t.Fatalf("Validate() error = %v", err)
			}
		})
	}
	for _, name := range []string{"2TOKEN", "RMQ-TOKEN", "RMQ TOKEN", "令牌", "TOKEN=value"} {
		t.Run("rejects "+name, func(t *testing.T) {
			cfg := configWithContext(Context{
				Server:   "https://rmq.example.com",
				Output:   "table",
				TokenEnv: name,
			})
			if err := Validate(cfg); err == nil {
				t.Fatal("Validate() error = nil, want rejection")
			}
		})
	}
}

func TestTokenValueNeverLeaks(t *testing.T) {
	const (
		envName  = "RMQCTL_SENTINEL_TOKEN"
		sentinel = "do-not-leak-super-secret-value"
	)
	t.Setenv(envName, sentinel)
	cfg := configWithContext(Context{
		Server:   "https://rmq.example.com",
		Output:   "json",
		TokenEnv: envName,
	})

	jsonBytes, err := json.Marshal(cfg)
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(jsonBytes), sentinel) {
		t.Fatal("JSON contains token value")
	}

	path := filepath.Join(t.TempDir(), "config.yaml")
	if err := Save(path, cfg, false); err != nil {
		t.Fatalf("Save() error = %v", err)
	}
	yamlBytes, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(yamlBytes), sentinel) {
		t.Fatal("YAML file contains token value")
	}
	loaded, err := Load(path)
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}
	if got := loaded.Contexts["default"].TokenEnv; got != envName {
		t.Fatalf("TokenEnv = %q, want %q", got, envName)
	}

	bad := cloneConfig(cfg)
	bad.Contexts["default"] = Context{
		Server:   "http://example.com",
		Output:   "table",
		TokenEnv: envName,
	}
	if err := Validate(bad); err == nil {
		t.Fatal("Validate() error = nil, want rejection")
	} else if strings.Contains(err.Error(), sentinel) {
		t.Fatal("validation error contains token value")
	}
}

func TestSaveOverwriteSemanticsAndAtomicPermissions(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "config.yaml")
	original := []byte("original contents")
	if err := os.WriteFile(path, original, 0644); err != nil {
		t.Fatal(err)
	}

	cfg := Default()
	if err := Save(path, cfg, false); err == nil {
		t.Fatal("Save(force=false) error = nil, want existing-file rejection")
	}
	got, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	if string(got) != string(original) {
		t.Fatalf("existing contents changed to %q", got)
	}

	if err := Save(path, cfg, true); err != nil {
		t.Fatalf("Save(force=true) error = %v", err)
	}
	loaded, err := Load(path)
	if err != nil {
		t.Fatalf("Load() error = %v", err)
	}
	if !reflect.DeepEqual(loaded, cfg) {
		t.Fatalf("Load() = %#v, want %#v", loaded, cfg)
	}
	if runtime.GOOS != "windows" {
		info, err := os.Stat(path)
		if err != nil {
			t.Fatal(err)
		}
		if gotMode := info.Mode().Perm(); gotMode != 0600 {
			t.Fatalf("forced file permissions = %04o, want 0600", gotMode)
		}
	}

	entries, err := os.ReadDir(dir)
	if err != nil {
		t.Fatal(err)
	}
	if len(entries) != 1 || entries[0].Name() != filepath.Base(path) {
		t.Fatalf("temporary files left behind: %v", entries)
	}
}

func TestSavePreservesExistingParentPermissions(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("Unix permission semantics")
	}

	tests := map[string]func(t *testing.T, path string) error{
		"successful save": func(_ *testing.T, path string) error {
			return Save(path, Default(), false)
		},
		"force false existing target": func(t *testing.T, path string) error {
			t.Helper()
			if err := os.WriteFile(path, []byte("existing"), 0600); err != nil {
				t.Fatal(err)
			}
			return Save(path, Default(), false)
		},
		"non-regular target": func(t *testing.T, path string) error {
			t.Helper()
			if err := os.Mkdir(path, 0700); err != nil {
				t.Fatal(err)
			}
			return Save(path, Default(), true)
		},
	}

	for name, action := range tests {
		t.Run(name, func(t *testing.T) {
			parent := filepath.Join(t.TempDir(), "existing-parent")
			if err := os.Mkdir(parent, 0755); err != nil {
				t.Fatal(err)
			}
			if err := os.Chmod(parent, 0755); err != nil {
				t.Fatal(err)
			}

			err := action(t, filepath.Join(parent, "config.yaml"))
			if name == "successful save" && err != nil {
				t.Fatalf("Save() error = %v", err)
			}
			if name != "successful save" && err == nil {
				t.Fatal("Save() error = nil, want rejection")
			}

			info, err := os.Stat(parent)
			if err != nil {
				t.Fatal(err)
			}
			if gotMode := info.Mode().Perm(); gotMode != 0755 {
				t.Fatalf("parent permissions = %04o, want unchanged 0755", gotMode)
			}
		})
	}
}

func TestSaveCreatesPrivateLeafDirectory(t *testing.T) {
	parent := filepath.Join(t.TempDir(), "missing", "leaf")
	path := filepath.Join(parent, "config.yaml")
	if err := Save(path, Default(), false); err != nil {
		t.Fatalf("Save() error = %v", err)
	}
	if runtime.GOOS != "windows" {
		info, err := os.Stat(parent)
		if err != nil {
			t.Fatal(err)
		}
		if gotMode := info.Mode().Perm(); gotMode&0077 != 0 {
			t.Fatalf("leaf directory permissions = %04o, want no group/other bits", gotMode)
		}
	}
}

func TestSaveParentSafetyGate(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("POSIX directory mode gate")
	}

	const (
		tokenEnv      = "RMQCTL_PARENT_GATE_TOKEN"
		sentinelToken = "must-not-appear-in-parent-gate-error"
	)
	t.Setenv(tokenEnv, sentinelToken)
	cfg := Default()
	ctx := cfg.Contexts["default"]
	ctx.TokenEnv = tokenEnv
	cfg.Contexts["default"] = ctx

	tests := []struct {
		name    string
		mode    os.FileMode
		wantErr bool
	}{
		{name: "rejects non-sticky 0777", mode: 0777, wantErr: true},
		{name: "accepts 0755", mode: 0755},
		{name: "accepts 0700", mode: 0700},
		{name: "accepts sticky 01777", mode: 0777 | os.ModeSticky},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			parent := filepath.Join(t.TempDir(), "parent")
			if err := os.Mkdir(parent, 0700); err != nil {
				t.Fatal(err)
			}
			if err := os.Chmod(parent, test.mode); err != nil {
				t.Fatal(err)
			}
			before, err := os.Stat(parent)
			if err != nil {
				t.Fatal(err)
			}
			if test.mode&os.ModeSticky != 0 && before.Mode()&os.ModeSticky == 0 {
				t.Skip("filesystem does not preserve the sticky bit")
			}

			path := filepath.Join(parent, "config.yaml")
			err = Save(path, cfg, false)
			if test.wantErr {
				if err == nil {
					t.Fatal("Save() error = nil, want unsafe-parent rejection")
				}
				if strings.Contains(err.Error(), sentinelToken) {
					t.Fatal("Save() error contains token value")
				}
			} else if err != nil {
				t.Fatalf("Save() error = %v", err)
			}

			after, err := os.Stat(parent)
			if err != nil {
				t.Fatal(err)
			}
			if after.Mode().Perm() != before.Mode().Perm() ||
				after.Mode()&os.ModeSticky != before.Mode()&os.ModeSticky {
				t.Fatalf("parent mode changed from %v to %v", before.Mode(), after.Mode())
			}
			entries, err := os.ReadDir(parent)
			if err != nil {
				t.Fatal(err)
			}
			if test.wantErr {
				if len(entries) != 0 {
					t.Fatalf("unsafe parent contains publication artifacts: %v", entries)
				}
			} else if len(entries) != 1 || entries[0].Name() != filepath.Base(path) {
				t.Fatalf("safe parent contents = %v, want only config", entries)
			}
		})
	}
}

func TestSaveDetectsTemporaryPathSwap(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("requires unlinking an open file")
	}

	for _, force := range []bool{false, true} {
		t.Run(fmt.Sprintf("force=%t", force), func(t *testing.T) {
			cfg := Default()
			ctx := cfg.Contexts["default"]
			ctx.CAFile = "/" + strings.Repeat("a", 32<<20)
			cfg.Contexts["default"] = ctx

			dir := t.TempDir()
			if err := os.Chmod(dir, 0700); err != nil {
				t.Fatal(err)
			}
			path := filepath.Join(dir, "config.yaml")
			saveResult := make(chan error, 1)
			go func() {
				saveResult <- Save(path, cfg, force)
			}()

			tempPath := waitForTemporaryConfig(t, dir, path, saveResult)
			if err := os.Remove(tempPath); err != nil {
				t.Fatalf("remove original temporary config: %v", err)
			}
			const substituted = "attacker substituted this file"
			file, err := os.OpenFile(tempPath, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0600)
			if err != nil {
				t.Fatalf("create substituted temporary config: %v", err)
			}
			if _, err := file.WriteString(substituted); err != nil {
				_ = file.Close()
				t.Fatal(err)
			}
			if err := file.Close(); err != nil {
				t.Fatal(err)
			}

			err = <-saveResult
			if err == nil {
				t.Fatal("Save() error = nil, want publication integrity rejection")
			}
			if !strings.Contains(err.Error(), "publication integrity") {
				t.Fatalf("Save() error = %q, want publication integrity error", err)
			}
			if got, err := os.ReadFile(path); err == nil {
				if string(got) == substituted {
					t.Fatal("Save() left substituted target published")
				}
				t.Fatalf("unexpected published target %q", got)
			} else if !os.IsNotExist(err) {
				t.Fatalf("inspect target: %v", err)
			}
		})
	}
}

func TestSaveJoinsTemporaryCleanupFailure(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("requires unlinking an open file")
	}

	cfg := Default()
	ctx := cfg.Contexts["default"]
	ctx.CAFile = "/" + strings.Repeat("a", 8<<20)
	cfg.Contexts["default"] = ctx

	dir := t.TempDir()
	if err := os.Chmod(dir, 0700); err != nil {
		t.Fatal(err)
	}
	path := filepath.Join(dir, "config.yaml")
	saveResult := make(chan error, 1)
	go func() {
		saveResult <- Save(path, cfg, false)
	}()

	tempPath := waitForTemporaryConfig(t, dir, path, saveResult)
	if err := os.Remove(tempPath); err != nil {
		t.Fatalf("remove original temporary config: %v", err)
	}
	if err := os.Mkdir(tempPath, 0700); err != nil {
		t.Fatalf("replace temporary config with directory: %v", err)
	}
	t.Cleanup(func() {
		_ = os.RemoveAll(tempPath)
	})
	if err := os.WriteFile(filepath.Join(tempPath, "child"), []byte("block cleanup"), 0600); err != nil {
		t.Fatal(err)
	}

	err := <-saveResult
	if err == nil {
		t.Fatal("Save() error = nil, want publish and cleanup errors")
	}
	for _, message := range []string{"publish config", "remove temporary config"} {
		if !strings.Contains(err.Error(), message) {
			t.Fatalf("Save() error = %q, want %q", err, message)
		}
	}
}

func TestSaveWithoutForcePublishesCompleteFileAtomically(t *testing.T) {
	cfg := Default()
	ctx := cfg.Contexts["default"]
	ctx.CAFile = "/" + strings.Repeat("a", 8<<20)
	cfg.Contexts["default"] = ctx
	want, err := yaml.Marshal(cfg)
	if err != nil {
		t.Fatal(err)
	}

	dir := t.TempDir()
	for attempt := 0; attempt < 10; attempt++ {
		path := filepath.Join(dir, fmt.Sprintf("config-%d.yaml", attempt))
		saveResult := make(chan error, 1)
		go func() {
			saveResult <- Save(path, cfg, false)
		}()

		var firstVisible []byte
		deadline := time.Now().Add(10 * time.Second)
		for {
			firstVisible, err = os.ReadFile(path)
			if err == nil {
				break
			}
			if !os.IsNotExist(err) {
				t.Fatalf("ReadFile() error = %v", err)
			}
			if time.Now().After(deadline) {
				t.Fatal("timed out waiting for config publication")
			}
			runtime.Gosched()
		}
		if err := <-saveResult; err != nil {
			t.Fatalf("Save() error = %v", err)
		}
		if !bytes.Equal(firstVisible, want) {
			t.Fatalf("first visible config had %d bytes, want complete %d bytes", len(firstVisible), len(want))
		}
	}
}

func TestSaveWithoutForceDoesNotReplaceConcurrentPublisher(t *testing.T) {
	cfg := Default()
	ctx := cfg.Contexts["default"]
	ctx.CAFile = "/" + strings.Repeat("a", 16<<20)
	cfg.Contexts["default"] = ctx

	dir := t.TempDir()
	path := filepath.Join(dir, "config.yaml")
	saveResult := make(chan error, 1)
	go func() {
		saveResult <- Save(path, cfg, false)
	}()

	tempPrefix := "." + filepath.Base(path) + ".tmp-"
	deadline := time.Now().Add(10 * time.Second)
	for {
		entries, err := os.ReadDir(dir)
		if err != nil {
			t.Fatal(err)
		}
		foundTemp := false
		for _, entry := range entries {
			if strings.HasPrefix(entry.Name(), tempPrefix) {
				foundTemp = true
				break
			}
		}
		if foundTemp {
			break
		}
		select {
		case err := <-saveResult:
			t.Fatalf("Save() completed before atomic publication could be contested: %v", err)
		default:
		}
		if time.Now().After(deadline) {
			t.Fatal("timed out waiting for temporary config")
		}
		runtime.Gosched()
	}

	const competitor = "concurrent publisher"
	file, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0600)
	if err != nil {
		t.Fatalf("create competing config: %v", err)
	}
	if _, err := file.WriteString(competitor); err != nil {
		_ = file.Close()
		t.Fatal(err)
	}
	if err := file.Close(); err != nil {
		t.Fatal(err)
	}

	if err := <-saveResult; err == nil {
		t.Fatal("Save() error = nil, want no-replace publication failure")
	}
	got, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	if string(got) != competitor {
		t.Fatalf("competing config changed to %q", got)
	}
	entries, err := os.ReadDir(dir)
	if err != nil {
		t.Fatal(err)
	}
	if len(entries) != 1 || entries[0].Name() != filepath.Base(path) {
		t.Fatalf("temporary files left behind: %v", entries)
	}
}

func TestSetUseAndCurrent(t *testing.T) {
	cfg := Default()
	production := Context{
		Server:   "https://rmq.example.com",
		Cluster:  "prod",
		Output:   "yaml",
		TokenEnv: "PROD_TOKEN",
	}
	if err := SetContext(&cfg, "production", production); err != nil {
		t.Fatalf("SetContext() error = %v", err)
	}
	if got := cfg.Contexts["production"]; !reflect.DeepEqual(got, production) {
		t.Fatalf("stored context = %#v, want %#v", got, production)
	}
	if err := UseContext(&cfg, "production"); err != nil {
		t.Fatalf("UseContext() error = %v", err)
	}
	got, err := Current(cfg)
	if err != nil {
		t.Fatalf("Current() error = %v", err)
	}
	if !reflect.DeepEqual(got, production) {
		t.Fatalf("Current() = %#v, want %#v", got, production)
	}
}

func TestSetContextSupportsNilMap(t *testing.T) {
	cfg := Config{
		APIVersion:     "rmq.apache.org/v1alpha1",
		CurrentContext: "first",
	}
	first := Context{Server: "https://rmq.example.com", Output: "table"}
	if err := SetContext(&cfg, "first", first); err != nil {
		t.Fatalf("SetContext() error = %v", err)
	}
	if got := cfg.Contexts["first"]; !reflect.DeepEqual(got, first) {
		t.Fatalf("stored context = %#v, want %#v", got, first)
	}
}

func TestMutationsDoNotPartiallyApplyOnFailure(t *testing.T) {
	t.Run("SetContext", func(t *testing.T) {
		cfg := Default()
		before := cloneConfig(cfg)
		err := SetContext(&cfg, "bad", Context{
			Server: "http://example.com",
			Output: "table",
		})
		if err == nil {
			t.Fatal("SetContext() error = nil, want rejection")
		}
		if !reflect.DeepEqual(cfg, before) {
			t.Fatalf("config mutated: got %#v, want %#v", cfg, before)
		}
	})

	t.Run("UseContext", func(t *testing.T) {
		cfg := Default()
		before := cloneConfig(cfg)
		if err := UseContext(&cfg, "missing"); err == nil {
			t.Fatal("UseContext() error = nil, want rejection")
		}
		if !reflect.DeepEqual(cfg, before) {
			t.Fatalf("config mutated: got %#v, want %#v", cfg, before)
		}
	})

	t.Run("Current validates", func(t *testing.T) {
		cfg := Default()
		cfg.Contexts["default"] = Context{
			Server: "http://example.com",
			Output: "table",
		}
		if _, err := Current(cfg); err == nil {
			t.Fatal("Current() error = nil, want validation error")
		}
	})
}

func TestSaveRejectsDirectoryAndNonRegularTargets(t *testing.T) {
	cfg := Default()

	t.Run("directory", func(t *testing.T) {
		path := filepath.Join(t.TempDir(), "config.yaml")
		if err := os.Mkdir(path, 0700); err != nil {
			t.Fatal(err)
		}
		if err := Save(path, cfg, true); err == nil {
			t.Fatal("Save() error = nil, want directory rejection")
		}
		info, err := os.Stat(path)
		if err != nil {
			t.Fatal(err)
		}
		if !info.IsDir() {
			t.Fatal("target directory was replaced")
		}
	})

	t.Run("symlink", func(t *testing.T) {
		dir := t.TempDir()
		realPath := filepath.Join(dir, "real.yaml")
		linkPath := filepath.Join(dir, "link.yaml")
		original := []byte("original")
		if err := os.WriteFile(realPath, original, 0600); err != nil {
			t.Fatal(err)
		}
		if err := os.Symlink(realPath, linkPath); err != nil {
			t.Skipf("symlink unavailable: %v", err)
		}
		if err := Save(linkPath, cfg, true); err == nil {
			t.Fatal("Save() error = nil, want symlink rejection")
		}
		got, err := os.ReadFile(realPath)
		if err != nil {
			t.Fatal(err)
		}
		if string(got) != string(original) {
			t.Fatalf("symlink target changed to %q", got)
		}
		info, err := os.Lstat(linkPath)
		if err != nil {
			t.Fatal(err)
		}
		if info.Mode()&os.ModeSymlink == 0 {
			t.Fatal("symlink was replaced")
		}
	})
}

func configWithContext(ctx Context) Config {
	return Config{
		APIVersion:     "rmq.apache.org/v1alpha1",
		CurrentContext: "default",
		Contexts: map[string]Context{
			"default": ctx,
		},
	}
}

func cloneConfig(cfg Config) Config {
	clone := cfg
	clone.Contexts = make(map[string]Context, len(cfg.Contexts))
	for name, ctx := range cfg.Contexts {
		clone.Contexts[name] = ctx
	}
	return clone
}

func waitForTemporaryConfig(t *testing.T, dir, target string, saveResult <-chan error) string {
	t.Helper()
	tempPrefix := "." + filepath.Base(target) + ".tmp-"
	deadline := time.Now().Add(10 * time.Second)
	for {
		entries, err := os.ReadDir(dir)
		if err != nil {
			t.Fatal(err)
		}
		for _, entry := range entries {
			if strings.HasPrefix(entry.Name(), tempPrefix) {
				return filepath.Join(dir, entry.Name())
			}
		}
		select {
		case err := <-saveResult:
			t.Fatalf("Save() completed before temporary config could be swapped: %v", err)
		default:
		}
		if time.Now().After(deadline) {
			t.Fatal("timed out waiting for temporary config")
		}
		runtime.Gosched()
	}
}
