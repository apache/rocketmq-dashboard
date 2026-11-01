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
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/config"
	"gopkg.in/yaml.v3"
	"strings"
	"testing"
)

func TestConfigGetContextsControlCellsTest(t *testing.T) {
	name := "dev\nforged\trow\x1b"
	cfg := config.Config{CurrentContext: name, Contexts: map[string]config.Context{name: {Server: "https://studio.example.com", Credential: config.CredentialRef{AccessKeyRef: "env:AK", SecretKeyRef: "env:SK"}}}}
	path := newTestConfigPath(t)
	if err := config.NewStore().Save(path, cfg); err != nil {
		t.Fatal(err)
	}
	for _, format := range []string{"table", "json", "yaml"} {
		t.Run(format, func(t *testing.T) {
			var out, diagnostic bytes.Buffer
			app := NewApp(&out, &diagnostic)
			app.Store.Getenv = emptyEnv
			if code := app.Execute([]string{"--config", path, "--output", format, "config", "get-contexts"}); code != 0 {
				t.Fatalf("exit=%d stderr=%q", code, diagnostic.String())
			}
			if format == "table" {
				if strings.Count(out.String(), "\n") != 2 || !strings.Contains(out.String(), `dev\nforged\trow\x1b`) {
					t.Fatalf("unsafe config table: %q", out.String())
				}
			} else {
				var got config.Config
				var err error
				if format == "json" {
					err = json.Unmarshal(out.Bytes(), &got)
				} else {
					err = yaml.Unmarshal(out.Bytes(), &got)
				}
				if err != nil || got.CurrentContext != name || got.Contexts[name] != cfg.Contexts[name] {
					t.Fatalf("structured config changed: %#v, err=%v", got, err)
				}
			}
		})
	}
}

func TestCatalogControlCellsWireOutputTest(t *testing.T) {
	key := "detail\n\x1b"
	value := "订单\vnote\fend\x1b"
	payload := map[string]any{"items": []any{map[string]any{key: value}}}
	for _, format := range []string{"table", "json", "yaml"} {
		t.Run(format, func(t *testing.T) {
			stdout, stderr, code := executeCatalogResult(t, "rmq.topic.list", payload, "topic", "list", "--output", format)
			if code != 0 || stderr != "" {
				t.Fatalf("exit=%d stderr=%q", code, stderr)
			}
			if format == "table" {
				if want := "DETAIL\\n\\x1b\n订单\\x0bnote\\x0cend\\x1b\n"; stdout != want {
					t.Fatalf("wire table=%q want=%q", stdout, want)
				}
			} else {
				var got map[string]any
				var err error
				if format == "json" {
					err = json.Unmarshal([]byte(stdout), &got)
				} else {
					err = yaml.Unmarshal([]byte(stdout), &got)
				}
				if err != nil {
					t.Fatal(err)
				}
				items := got["items"].([]any)
				if items[0].(map[string]any)[key] != value {
					t.Fatalf("wire value changed: %#v", got)
				}
			}
		})
	}
}
