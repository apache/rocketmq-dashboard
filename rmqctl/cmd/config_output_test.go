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
	"errors"
	"strings"
	"testing"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/config"
)

type configResultWriter struct {
	buffer  bytes.Buffer
	failure error
}

func (w *configResultWriter) Write(p []byte) (int, error) {
	if w.failure != nil {
		return 0, w.failure
	}
	return w.buffer.Write(p)
}

func TestConfigCommandOutput(t *testing.T) {
	commands := []struct {
		name string
		args []string
		text string
	}{
		{"set-context", []string{"set-context", "prod", "--server", "https://updated.example.com"}, "updated context prod\n"},
		{"use-context", []string{"use-context", "dev"}, "current context: dev\n"},
		{"delete-context", []string{"delete-context", "prod"}, "deleted context prod\n"},
		{"current-context", []string{"current-context"}, "prod\n"},
	}
	for _, command := range commands {
		for _, format := range []string{"table", "json", "yaml"} {
			for _, fail := range []bool{false, true} {
				mode := "success"
				if fail {
					mode = "writer failure"
				}
				t.Run(command.name+"/"+format+"/"+mode, func(t *testing.T) {
					out := &configResultWriter{}
					if fail {
						out.failure = errors.New("synthetic output unavailable")
					}
					stderr := &bytes.Buffer{}
					app := NewApp(out, stderr)
					app.Store.Getenv = emptyEnv
					path := newTestConfigPath(t)
					context := config.Context{Server: "https://studio.example.com", Credential: config.CredentialRef{
						AccessKeyRef: "env:SYNTHETIC_AK", SecretKeyRef: "env:SYNTHETIC_SK",
					}}
					if err := app.Store.Save(path, config.Config{CurrentContext: "prod", Contexts: map[string]config.Context{
						"prod": context, "dev": context,
					}}); err != nil {
						t.Fatal(err)
					}
					args := append([]string{"--config", path, "--output", format, "config"}, command.args...)
					code := app.Execute(args)
					// An output failure must not roll back or replay an already saved mutation.
					saved, err := app.Store.Load(path)
					if err != nil {
						t.Fatal(err)
					}
					switch command.name {
					case "set-context":
						if saved.Contexts["prod"].Server != "https://updated.example.com" {
							t.Fatalf("saved context: %#v", saved)
						}
					case "use-context":
						if saved.CurrentContext != "dev" {
							t.Fatalf("saved context: %#v", saved)
						}
					case "delete-context":
						if _, exists := saved.Contexts["prod"]; exists || saved.CurrentContext != "" {
							t.Fatalf("saved context: %#v", saved)
						}
					case "current-context":
						if saved.CurrentContext != "prod" {
							t.Fatalf("saved context: %#v", saved)
						}
					}
					if fail {
						if code == 0 {
							t.Fatal("command reported success despite failed output")
						}
						if !strings.Contains(stderr.String(), "synthetic output unavailable") {
							t.Fatalf("lost writer error: %s", stderr)
						}
					} else {
						if code != 0 {
							t.Fatalf("exit %d: %s", code, stderr)
						}
						if format == "table" && out.buffer.String() != command.text {
							t.Fatalf("unexpected output: %q", out.buffer.String())
						}
						if out.buffer.Len() == 0 {
							t.Fatal("missing output")
						}
					}
				})
			}
		}
	}
}
