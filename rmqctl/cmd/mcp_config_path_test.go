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
	"reflect"
	"testing"
)

func TestMCPConfigResolvesSelectedPathBeforeClientLaunch(t *testing.T) {
	t.Chdir(t.TempDir())
	for _, supplied := range []string{"config.yaml", filepath.Join("配置 files", "config.yaml"), filepath.Join(t.TempDir(), "absolute.yaml")} {
		t.Run(supplied, func(t *testing.T) {
			stdout, stderr := &bytes.Buffer{}, &bytes.Buffer{}
			app := NewApp(stdout, stderr)
			app.Store.Getenv = emptyEnv
			if code := app.Execute([]string{"mcp", "config", "--config", supplied}); code != 0 {
				t.Fatalf("exit = %d: %s", code, stderr)
			}
			var snippet struct {
				Servers map[string]struct {
					Args []string `json:"args"`
				} `json:"mcpServers"`
			}
			if err := json.Unmarshal(stdout.Bytes(), &snippet); err != nil {
				t.Fatal(err)
			}
			args := snippet.Servers["rocketmq-studio"].Args
			expected, err := filepath.Abs(supplied)
			if err != nil {
				t.Fatal(err)
			}
			if !reflect.DeepEqual(args, []string{"mcp", "stdio", "--config", expected}) {
				t.Fatalf("args = %q; want selected absolute config %q", args, expected)
			}
			// A client can launch in another working directory without changing the selected file.
			t.Chdir(t.TempDir())
			resolved, err := filepath.Abs(args[3])
			if err != nil || resolved != expected {
				t.Fatalf("client resolves %q, want %q: %v", resolved, expected, err)
			}
		})
	}
}

func TestMCPConfigLeavesUnspecifiedPathUnbound(t *testing.T) {
	stdout, stderr := &bytes.Buffer{}, &bytes.Buffer{}
	app := NewApp(stdout, stderr)
	app.Store.Getenv = emptyEnv
	if code := app.Execute([]string{"mcp", "config"}); code != 0 {
		t.Fatalf("exit = %d: %s", code, stderr)
	}
	if bytes.Contains(stdout.Bytes(), []byte("--config")) {
		t.Fatalf("unexpected config binding: %s", stdout)
	}
}
