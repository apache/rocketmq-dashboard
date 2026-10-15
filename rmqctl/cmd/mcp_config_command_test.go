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
	"path/filepath"
	"testing"
)

func TestMCPConfigResolvesCommandPathBeforeClientLaunch(t *testing.T) {
	t.Chdir(t.TempDir())
	for _, supplied := range []string{
		"./rmqctl",
		filepath.Join("dist", "rmqctl"),
		filepath.Join(t.TempDir(), "rmqctl"),
	} {
		t.Run(supplied, func(t *testing.T) {
			stdout, stderr := &bytes.Buffer{}, &bytes.Buffer{}
			app := NewApp(stdout, stderr)
			app.Store.Getenv = emptyEnv
			if code := app.Execute([]string{"mcp", "config", "--command", supplied, "--instance-id", "instance-a"}); code != 0 {
				t.Fatalf("exit = %d: %s", code, stderr)
			}
			var snippet struct {
				Servers map[string]struct {
					Command string `json:"command"`
				} `json:"mcpServers"`
			}
			if err := json.Unmarshal(stdout.Bytes(), &snippet); err != nil {
				t.Fatal(err)
			}
			expected, err := filepath.Abs(supplied)
			if err != nil {
				t.Fatal(err)
			}
			command := snippet.Servers["rocketmq-studio"].Command
			if command != expected {
				t.Fatalf("command = %q; want the selected absolute path %q", command, expected)
			}
			// A client can launch from another working directory; a relative command would then
			// point at a binary that is not there and the server would never start.
			t.Chdir(t.TempDir())
			resolved, err := filepath.Abs(command)
			if err != nil || resolved != expected {
				t.Fatalf("client resolves %q to %q, want %q: %v", command, resolved, expected, err)
			}
		})
	}
}

func TestMCPConfigKeepsABareCommandName(t *testing.T) {
	// A bare name is resolved through PATH by the client, which is what the documented default
	// relies on; making it absolute would pin the snippet to the directory it was generated in.
	stdout, stderr := &bytes.Buffer{}, &bytes.Buffer{}
	app := NewApp(stdout, stderr)
	app.Store.Getenv = emptyEnv
	if code := app.Execute([]string{"mcp", "config", "--instance-id", "instance-a"}); code != 0 {
		t.Fatalf("exit = %d: %s", code, stderr)
	}
	var snippet struct {
		Servers map[string]struct {
			Command string `json:"command"`
		} `json:"mcpServers"`
	}
	if err := json.Unmarshal(stdout.Bytes(), &snippet); err != nil {
		t.Fatal(err)
	}
	if command := snippet.Servers["rocketmq-studio"].Command; command != "rmqctl" {
		t.Fatalf("command = %q; want the bare default rmqctl", command)
	}
}
