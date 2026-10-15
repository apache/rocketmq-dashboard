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
	"bytes"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
)

func TestMCPConfigBindsExplicitExecutablePathToGenerationDirectory(t *testing.T) {
	for _, supplied := range []string{"./rmqctl", filepath.Join("bin space", "rmqctl"), filepath.Join("..", "rmqctl")} {
		t.Run(supplied, func(t *testing.T) {
			generationDirectory := filepath.Join(t.TempDir(), "generation")
			if err := os.Mkdir(generationDirectory, 0700); err != nil {
				t.Fatal(err)
			}
			t.Chdir(generationDirectory)
			expected, err := filepath.Abs(supplied)
			if err != nil {
				t.Fatal(err)
			}
			stdout, stderr := &bytes.Buffer{}, &bytes.Buffer{}
			app := NewApp(stdout, stderr)
			app.Store.Getenv = emptyEnv
			if code := app.Execute([]string{"mcp", "config", "--command", supplied, "--instance-id", "example"}); code != 0 {
				t.Fatalf("exit=%d: %s", code, stderr)
			}
			var snippet struct {
				Servers map[string]struct {
					Command string `json:"command"`
				} `json:"mcpServers"`
			}
			if err := json.Unmarshal(stdout.Bytes(), &snippet); err != nil {
				t.Fatal(err)
			}
			command := snippet.Servers["rocketmq-studio"].Command
			t.Chdir(t.TempDir())
			resolved, err := filepath.Abs(command)
			if err != nil || resolved != expected {
				t.Fatalf("client resolves executable %q, want selected executable %q: %v", resolved, expected, err)
			}
		})
	}
}

func TestMCPConfigPreservesBareExecutableNamesAndAbsolutePaths(t *testing.T) {
	for _, supplied := range []string{"rmqctl", "custom-rmqctl", filepath.Join(t.TempDir(), "bin space", "rmqctl"), t.TempDir() + string(filepath.Separator) + "link" + string(filepath.Separator) + ".." + string(filepath.Separator) + "rmqctl"} {
		t.Run(supplied, func(t *testing.T) {
			stdout, stderr := &bytes.Buffer{}, &bytes.Buffer{}
			app := NewApp(stdout, stderr)
			app.Store.Getenv = emptyEnv
			if code := app.Execute([]string{"mcp", "config", "--command", supplied, "--instance-id", "example"}); code != 0 {
				t.Fatalf("exit=%d: %s", code, stderr)
			}
			var snippet struct {
				Servers map[string]struct {
					Command string `json:"command"`
				} `json:"mcpServers"`
			}
			if err := json.Unmarshal(stdout.Bytes(), &snippet); err != nil {
				t.Fatal(err)
			}
			if got := snippet.Servers["rocketmq-studio"].Command; got != supplied {
				t.Fatalf("command=%q, want unchanged %q", got, supplied)
			}
		})
	}
}

func TestMCPConfigPreservesRelativeSymlinkParentResolution(t *testing.T) {
	if filepath.Separator != '/' {
		t.Skip("POSIX symlink/parent resolution")
	}
	generation := t.TempDir()
	target := t.TempDir()
	if err := os.Mkdir(filepath.Join(target, "child"), 0700); err != nil {
		t.Fatal(err)
	}
	executable := filepath.Join(target, "rmqctl")
	if err := os.WriteFile(executable, []byte("fixture"), 0600); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink(filepath.Join(target, "child"), filepath.Join(generation, "link")); err != nil {
		t.Fatal(err)
	}
	t.Chdir(generation)
	stdout, stderr := &bytes.Buffer{}, &bytes.Buffer{}
	app := NewApp(stdout, stderr)
	app.Store.Getenv = emptyEnv
	if code := app.Execute([]string{"mcp", "config", "--command", "./link/../rmqctl", "--instance-id", "example"}); code != 0 {
		t.Fatalf("exit=%d: %s", code, stderr)
	}
	var snippet struct {
		Servers map[string]struct {
			Command string `json:"command"`
		} `json:"mcpServers"`
	}
	if err := json.Unmarshal(stdout.Bytes(), &snippet); err != nil {
		t.Fatal(err)
	}
	t.Chdir(t.TempDir())
	actual, err := os.Stat(snippet.Servers["rocketmq-studio"].Command)
	if err != nil {
		t.Fatal(err)
	}
	expected, err := os.Stat(executable)
	if err != nil {
		t.Fatal(err)
	}
	if !os.SameFile(actual, expected) {
		t.Fatal("generated command selects a different file")
	}
}
