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
package output

import (
	"bytes"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/config"
	"strings"
	"testing"
)

func TestTableCellControlsTest(t *testing.T) {
	for _, tc := range []struct{ name, value, want string }{
		{"vertical_tab", "a\vb", `a\x0bb`},
		{"form_feed", "a\fb", `a\x0cb`},
		{"escape", "a\x1bb", `a\x1bb`},
		{"nul", "a\x00b", `a\x00b`},
		{"bell_backspace_delete", "a\a\b\x7fb", `a\x07\x08\x7fb`},
		{"unicode_and_literal_escape", `订单\path😀`, `订单\path😀`},
	} {
		t.Run(tc.name, func(t *testing.T) {
			var out bytes.Buffer
			if err := Rows(&out, []map[string]any{{"value": tc.value}}, []Column{{Header: "VALUE", Key: "value"}}); err != nil {
				t.Fatal(err)
			}
			if want := "VALUE\n" + tc.want + "\n"; out.String() != want {
				t.Fatalf("output = %q, want %q", out.String(), want)
			}
		})
	}
}

func TestTableHeadersControlsTest(t *testing.T) {
	var out bytes.Buffer
	if err := Rows(&out, []map[string]any{{"value": "normal"}}, []Column{{Header: "FIELD\n\t\x1b", Key: "value"}}); err != nil {
		t.Fatal(err)
	}
	if want := "FIELD\\n\\t\\x1b\nnormal\n"; out.String() != want {
		t.Fatalf("output=%q, want %q", out.String(), want)
	}
}

func TestConfigTableControlsTest(t *testing.T) {
	name := "dev\nforged\trow"
	cfg := config.Config{CurrentContext: name, Contexts: map[string]config.Context{name: {Server: "https://studio.example.com/\x1b", Credential: config.CredentialRef{AccessKeyRef: "env:AK", SecretKeyRef: "env:SK"}}}}
	var out bytes.Buffer
	if err := ConfigTable(&out, cfg); err != nil {
		t.Fatal(err)
	}
	if strings.Count(out.String(), "\n") != 2 || !strings.Contains(out.String(), `dev\nforged\trow`) || !strings.Contains(out.String(), `https://studio.example.com/\x1b`) {
		t.Fatalf("unsafe table: %q", out.String())
	}
	if cfg.CurrentContext != name || cfg.Contexts[name].Server != "https://studio.example.com/\x1b" {
		t.Fatal("rendering modified config")
	}
}

func TestMutationTableMetadataControlsTest(t *testing.T) {
	value := map[string]any{"status": "PLANNED", "instanceId": "dev\nforged", "confirm_token": "token\f\x1b", "plan": map[string]any{"summary": "original\nmessage"}}
	var out bytes.Buffer
	if err := ToolCallSummary(&out, value); err != nil {
		t.Fatal(err)
	}
	lines := strings.SplitN(out.String(), "\n\n", 2)
	if len(lines) != 2 || strings.Count(lines[0], "\n") != 1 || !strings.Contains(lines[0], `dev\nforged`) || !strings.Contains(lines[0], `token\x0c\x1b`) {
		t.Fatalf("unsafe metadata: %q", out.String())
	}
	var expected bytes.Buffer
	if err := JSON(&expected, value["plan"]); err != nil {
		t.Fatal(err)
	}
	if lines[1] != expected.String() {
		t.Fatalf("plan changed: %q, want %q", lines[1], expected.String())
	}
}
