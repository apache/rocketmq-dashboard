// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package cli

import (
	"bytes"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"reflect"
	"sort"
	"strings"
	"testing"

	"github.com/apache/rocketmq-dashboard/tools/rmq/internal/catalog"
	"github.com/apache/rocketmq-dashboard/tools/rmq/internal/config"
	"github.com/spf13/cobra"
	"go.yaml.in/yaml/v3"
)

func TestNewRootInjectsWritersAndRegistersOnlyLocalCommands(t *testing.T) {
	var stdout, stderr bytes.Buffer
	root := NewRoot(Options{Out: &stdout, ErrOut: &stderr, ConfigPath: t.TempDir() + "/config.yaml"})
	want := map[string]bool{
		"completion": false,
		"config":     false,
		"explain":    false,
		"version":    false,
	}
	for _, cmd := range root.Commands() {
		if cmd.Name() == "help" {
			continue
		}
		if _, ok := want[cmd.Name()]; ok {
			want[cmd.Name()] = true
			continue
		}
		t.Fatalf("unexpected root command %q", cmd.Name())
	}
	for name, found := range want {
		if !found {
			t.Errorf("missing root command %q", name)
		}
	}

	root.SetArgs([]string{"version"})
	if err := root.Execute(); err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if stdout.Len() == 0 || stderr.Len() != 0 {
		t.Fatalf("version stdout = %q, stderr = %q", stdout.String(), stderr.String())
	}
}

func TestVersionFormats(t *testing.T) {
	t.Run("default table", func(t *testing.T) {
		stdout, stderr, err := executeRoot(t, Options{}, "version")
		if err != nil {
			t.Fatalf("Execute() error = %v", err)
		}
		if stderr != "" {
			t.Fatalf("stderr = %q", stderr)
		}
		want := "VERSION  COMMIT   DATE\ndev      unknown  unknown\n"
		if stdout != want {
			t.Fatalf("stdout = %q, want %q", stdout, want)
		}
	})

	t.Run("json", func(t *testing.T) {
		stdout, stderr, err := executeRoot(t, Options{}, "version", "--output", "json")
		if err != nil {
			t.Fatalf("Execute() error = %v", err)
		}
		if stderr != "" {
			t.Fatalf("stderr = %q", stderr)
		}
		want := "{\n  \"version\": \"dev\",\n  \"commit\": \"unknown\",\n  \"date\": \"unknown\"\n}\n"
		if stdout != want {
			t.Fatalf("stdout = %q, want %q", stdout, want)
		}
	})

	t.Run("yaml", func(t *testing.T) {
		stdout, _, err := executeRoot(t, Options{}, "version", "--output", "yaml")
		if err != nil {
			t.Fatalf("Execute() error = %v", err)
		}
		want := "version: dev\ncommit: unknown\ndate: unknown\n"
		if stdout != want {
			t.Fatalf("stdout = %q, want %q", stdout, want)
		}
	})
}

func TestCompletionWritesFourShellsToStdoutOnly(t *testing.T) {
	for _, shell := range []string{"bash", "zsh", "fish", "powershell"} {
		t.Run(shell, func(t *testing.T) {
			stdout, stderr, err := executeRoot(t, Options{}, "completion", shell)
			if err != nil {
				t.Fatalf("Execute() error = %v", err)
			}
			if stdout == "" {
				t.Fatal("completion stdout is empty")
			}
			if stderr != "" {
				t.Fatalf("completion stderr = %q", stderr)
			}
		})
	}
}

func TestCompletionRejectsInvalidShellWithoutWritingScriptOrUsage(t *testing.T) {
	stdout, stderr, err := executeRoot(t, Options{}, "completion", "tcsh")
	if err == nil || !strings.Contains(err.Error(), `unsupported shell "tcsh"`) {
		t.Fatalf("Execute() error = %v", err)
	}
	if stdout != "" || stderr != "" {
		t.Fatalf("invalid completion stdout = %q, stderr = %q", stdout, stderr)
	}
}

func TestExplainExactNameAndResourceAreEquivalent(t *testing.T) {
	exact, exactErr, err := executeRoot(t, Options{}, "explain", "rmq.cluster.list", "--output", "json")
	if err != nil {
		t.Fatalf("exact Execute() error = %v", err)
	}
	resource, resourceErr, err := executeRoot(t, Options{}, "explain", "cluster", "--output", "json")
	if err != nil {
		t.Fatalf("resource Execute() error = %v", err)
	}
	if exactErr != "" || resourceErr != "" {
		t.Fatalf("stderr exact = %q, resource = %q", exactErr, resourceErr)
	}
	if exact != resource {
		t.Fatalf("exact output differs from resource output:\nexact=%s\nresource=%s", exact, resource)
	}
	var got catalog.Tool
	if err := json.Unmarshal([]byte(exact), &got); err != nil {
		t.Fatalf("decode explain JSON: %v", err)
	}
	if got.Name != "rmq.cluster.list" || got.CLI.Resource != "cluster" {
		t.Fatalf("explain JSON = %#v", got)
	}
}

func TestExplainFormats(t *testing.T) {
	tests := []struct {
		format string
		check  func(*testing.T, string)
	}{
		{
			format: "table",
			check: func(t *testing.T, got string) {
				t.Helper()
				for _, want := range []string{"NAME", "RESOURCE", "rmq.cluster.list", "cluster"} {
					if !strings.Contains(got, want) {
						t.Fatalf("table output %q does not contain %q", got, want)
					}
				}
			},
		},
		{
			format: "json",
			check: func(t *testing.T, got string) {
				t.Helper()
				var tool catalog.Tool
				if err := json.Unmarshal([]byte(got), &tool); err != nil {
					t.Fatalf("decode JSON: %v", err)
				}
				if tool.Name != "rmq.cluster.list" {
					t.Fatalf("JSON tool = %#v", tool)
				}
			},
		},
		{
			format: "yaml",
			check: func(t *testing.T, got string) {
				t.Helper()
				var tool catalog.Tool
				if err := yaml.Unmarshal([]byte(got), &tool); err != nil {
					t.Fatalf("decode YAML: %v", err)
				}
				if tool.Name != "rmq.cluster.list" {
					t.Fatalf("YAML tool = %#v", tool)
				}
			},
		},
	}
	for _, tt := range tests {
		t.Run(tt.format, func(t *testing.T) {
			stdout, stderr, err := executeRoot(t, Options{}, "explain", "cluster", "--output", tt.format)
			if err != nil {
				t.Fatalf("Execute() error = %v", err)
			}
			if stderr != "" {
				t.Fatalf("stderr = %q", stderr)
			}
			tt.check(t, stdout)
		})
	}
}

func TestExplainMissingIsActionable(t *testing.T) {
	stdout, stderr, err := executeRoot(t, Options{}, "explain", "missing")
	if err == nil {
		t.Fatal("Execute() error = nil")
	}
	for _, want := range []string{"missing", "rmq.cluster.list"} {
		if !strings.Contains(err.Error(), want) {
			t.Fatalf("Execute() error = %q, want %q", err, want)
		}
	}
	if stdout != "" || stderr != "" {
		t.Fatalf("stdout = %q, stderr = %q", stdout, stderr)
	}
}

func TestConfigInitRefusesOverwriteUnlessForced(t *testing.T) {
	path := filepath.Join(t.TempDir(), "config.yaml")
	if _, _, err := executeRoot(t, Options{ConfigPath: path}, "config", "init"); err != nil {
		t.Fatalf("first init error = %v", err)
	}
	if _, _, err := executeRoot(t, Options{ConfigPath: path}, "config", "init"); err == nil ||
		!strings.Contains(err.Error(), "already exists") {
		t.Fatalf("second init error = %v", err)
	}
	if _, _, err := executeRoot(t, Options{ConfigPath: path}, "config", "init", "--force"); err != nil {
		t.Fatalf("forced init error = %v", err)
	}
}

func TestConfigGetPrintsTokenEnvironmentNameButNotValue(t *testing.T) {
	path := initializedConfig(t)
	t.Setenv("RMQCTL_TOKEN", "SENTINEL_TOKEN_VALUE_MUST_NOT_LEAK")
	stdout, stderr, err := executeRoot(t, Options{ConfigPath: path}, "config", "get")
	if err != nil {
		t.Fatalf("Execute() error = %v", err)
	}
	if stderr != "" {
		t.Fatalf("stderr = %q", stderr)
	}
	if !strings.Contains(stdout, "RMQCTL_TOKEN") {
		t.Fatalf("config get stdout = %q, want token environment name", stdout)
	}
	if strings.Contains(stdout, "SENTINEL_TOKEN_VALUE_MUST_NOT_LEAK") {
		t.Fatalf("config get leaked token value: %q", stdout)
	}
}

func TestConfigSetExistingChangesOnlyExplicitFlags(t *testing.T) {
	path := initializedConfig(t)
	cfg, err := config.Load(path)
	if err != nil {
		t.Fatal(err)
	}
	ctx := cfg.Contexts["default"]
	ctx.Cluster = "keep-cluster"
	ctx.CAFile = "/keep/ca.pem"
	if err := config.SetContext(&cfg, "default", ctx); err != nil {
		t.Fatal(err)
	}
	if err := config.Save(path, cfg, true); err != nil {
		t.Fatal(err)
	}

	if _, _, err := executeRoot(t, Options{ConfigPath: path},
		"config", "set", "default", "--output", "json"); err != nil {
		t.Fatalf("config set error = %v", err)
	}
	got, err := config.Load(path)
	if err != nil {
		t.Fatal(err)
	}
	want := ctx
	want.Output = "json"
	if !reflect.DeepEqual(got.Contexts["default"], want) {
		t.Fatalf("context = %#v, want %#v", got.Contexts["default"], want)
	}
}

func TestConfigSetNewContextUsesSafeDefaultsAndAllFlags(t *testing.T) {
	path := initializedConfig(t)
	args := []string{
		"config", "set", "production",
		"--server", "https://rmq.example.com",
		"--cluster", "prod",
		"--output", "yaml",
		"--token-env", "PROD_RMQ_TOKEN",
		"--ca-file", "/etc/rmq/ca.pem",
	}
	if _, _, err := executeRoot(t, Options{ConfigPath: path}, args...); err != nil {
		t.Fatalf("config set error = %v", err)
	}
	got, err := config.Load(path)
	if err != nil {
		t.Fatal(err)
	}
	want := config.Context{
		Server:   "https://rmq.example.com",
		Cluster:  "prod",
		Output:   "yaml",
		TokenEnv: "PROD_RMQ_TOKEN",
		CAFile:   "/etc/rmq/ca.pem",
	}
	if !reflect.DeepEqual(got.Contexts["production"], want) {
		t.Fatalf("new context = %#v, want %#v", got.Contexts["production"], want)
	}
}

func TestConfigUseAndCurrentContextRoundTrip(t *testing.T) {
	path := initializedConfig(t)
	if _, _, err := executeRoot(t, Options{ConfigPath: path}, "config", "set", "local"); err != nil {
		t.Fatalf("config set error = %v", err)
	}
	if _, _, err := executeRoot(t, Options{ConfigPath: path}, "config", "use-context", "local"); err != nil {
		t.Fatalf("config use-context error = %v", err)
	}
	stdout, stderr, err := executeRoot(t, Options{ConfigPath: path}, "config", "current-context")
	if err != nil {
		t.Fatalf("config current-context error = %v", err)
	}
	if stdout != "local\n" || stderr != "" {
		t.Fatalf("current-context stdout = %q, stderr = %q", stdout, stderr)
	}
	got, err := config.Load(path)
	if err != nil {
		t.Fatal(err)
	}
	if got.CurrentContext != "local" {
		t.Fatalf("CurrentContext = %q", got.CurrentContext)
	}
}

func TestConfigPathPrecedence(t *testing.T) {
	t.Run("explicit flag beats env and injected", func(t *testing.T) {
		dir := t.TempDir()
		explicitPath := filepath.Join(dir, "explicit.yaml")
		envPath := filepath.Join(dir, "env.yaml")
		injectedPath := filepath.Join(dir, "injected.yaml")
		t.Setenv("RMQCTL_CONFIG", envPath)
		if _, _, err := executeRoot(t, Options{ConfigPath: injectedPath},
			"--config", explicitPath, "config", "init"); err != nil {
			t.Fatalf("Execute() error = %v", err)
		}
		assertOnlyPathExists(t, explicitPath, envPath, injectedPath)
	})

	t.Run("env beats injected", func(t *testing.T) {
		dir := t.TempDir()
		envPath := filepath.Join(dir, "env.yaml")
		injectedPath := filepath.Join(dir, "injected.yaml")
		t.Setenv("RMQCTL_CONFIG", envPath)
		if _, _, err := executeRoot(t, Options{ConfigPath: injectedPath}, "config", "init"); err != nil {
			t.Fatalf("Execute() error = %v", err)
		}
		assertOnlyPathExists(t, envPath, injectedPath)
	})

	t.Run("injected beats default", func(t *testing.T) {
		dir := t.TempDir()
		injectedPath := filepath.Join(dir, "injected.yaml")
		t.Setenv("RMQCTL_CONFIG", "")
		t.Setenv("HOME", filepath.Join(dir, "home"))
		if _, _, err := executeRoot(t, Options{ConfigPath: injectedPath}, "config", "init"); err != nil {
			t.Fatalf("Execute() error = %v", err)
		}
		defaultPath := filepath.Join(dir, "home", ".rmqctl", "config.yaml")
		assertOnlyPathExists(t, injectedPath, defaultPath)
	})

	t.Run("default uses temporary home", func(t *testing.T) {
		home := t.TempDir()
		t.Setenv("RMQCTL_CONFIG", "")
		t.Setenv("HOME", home)
		if _, _, err := executeRoot(t, Options{}, "config", "init"); err != nil {
			t.Fatalf("Execute() error = %v", err)
		}
		if _, err := os.Stat(filepath.Join(home, ".rmqctl", "config.yaml")); err != nil {
			t.Fatalf("default config was not created in temporary home: %v", err)
		}
	})
}

func TestUsageErrorsReturnErrorWithoutWritingUsage(t *testing.T) {
	stdout, stderr, err := executeRoot(t, Options{}, "config", "set")
	if err == nil {
		t.Fatal("Execute() error = nil")
	}
	if stdout != "" || stderr != "" {
		t.Fatalf("stdout = %q, stderr = %q", stdout, stderr)
	}
}

type errorWriter struct{}

func (errorWriter) Write([]byte) (int, error) {
	return 0, errors.New("injected write failure")
}

func TestInjectedWriterErrorsAreReturned(t *testing.T) {
	var stderr bytes.Buffer
	root := NewRoot(Options{Out: errorWriter{}, ErrOut: &stderr})
	root.SetArgs([]string{"version"})
	err := root.Execute()
	if err == nil || !strings.Contains(err.Error(), "injected write failure") {
		t.Fatalf("Execute() error = %v", err)
	}
}

func TestCommandTreeHasNoRemoteCommands(t *testing.T) {
	root := NewRoot(Options{Out: &bytes.Buffer{}, ErrOut: &bytes.Buffer{}})
	var paths []string
	var walk func(*cobra.Command, string)
	walk = func(parent *cobra.Command, prefix string) {
		for _, command := range parent.Commands() {
			if command.Name() == "help" {
				continue
			}
			path := strings.TrimSpace(prefix + " " + command.Name())
			paths = append(paths, path)
			walk(command, path)
		}
	}
	walk(root, "")
	sort.Strings(paths)
	want := []string{
		"completion",
		"config",
		"config current-context",
		"config get",
		"config init",
		"config set",
		"config use-context",
		"explain",
		"version",
	}
	if !reflect.DeepEqual(paths, want) {
		t.Fatalf("root commands = %v, want %v", paths, want)
	}
}

func executeRoot(t *testing.T, options Options, args ...string) (string, string, error) {
	t.Helper()
	var stdout, stderr bytes.Buffer
	options.Out = &stdout
	options.ErrOut = &stderr
	root := NewRoot(options)
	root.SetArgs(args)
	err := root.Execute()
	return stdout.String(), stderr.String(), err
}

func initializedConfig(t *testing.T) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), "config.yaml")
	if _, _, err := executeRoot(t, Options{ConfigPath: path}, "config", "init"); err != nil {
		t.Fatalf("config init error = %v", err)
	}
	return path
}

func assertOnlyPathExists(t *testing.T, wanted string, unwanted ...string) {
	t.Helper()
	if _, err := os.Stat(wanted); err != nil {
		t.Fatalf("wanted path %q does not exist: %v", wanted, err)
	}
	for _, path := range unwanted {
		if _, err := os.Stat(path); !os.IsNotExist(err) {
			t.Fatalf("unwanted path %q exists or stat returned unexpected error: %v", path, err)
		}
	}
}
