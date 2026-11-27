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
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"unicode/utf8"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/studio"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/types"
	"gopkg.in/yaml.v3"
)

func TestCommandErrorEscapesTerminalControls(t *testing.T) {
	cases := []struct{ name, text, displayed string }{
		{"unicode and literal backslash", `中文 \\x1b`, `中文 \\x1b`},
	}
	for control := byte(0); control < 0x20; control++ {
		displayed := fmt.Sprintf(`\x%02x`, control)
		if control == '\r' || control == '\n' || control == '\t' {
			displayed = " "
		}
		cases = append(cases, struct{ name, text, displayed string }{fmt.Sprintf("C0-%02x", control), string(control), displayed})
	}
	cases = append(cases, struct{ name, text, displayed string }{"DEL", string(rune(0x7f)), `\x7f`})
	for _, testCase := range cases {
		t.Run(testCase.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				w.Header().Set("Content-Type", "application/json")
				w.WriteHeader(http.StatusForbidden)
				if err := json.NewEncoder(w).Encode(map[string]string{
					"code":    "ERR" + testCase.text + "CODE",
					"message": "resource " + testCase.text + " denied 中文",
					"hint":    "try " + testCase.text + " again",
				}); err != nil {
					t.Error(err)
				}
			}))
			defer server.Close()
			stdout, stderr, exitCode := executeTestApp(t, server.Client(), server.URL, "topic", "list")
			if exitCode != 1 || stdout != "" {
				t.Fatalf("exit=%d stdout=%q stderr=%q", exitCode, stdout, stderr)
			}
			want := fmt.Sprintf("error [ERR%sCODE]: resource %s denied 中文\nhint: try %s again\n", testCase.displayed, testCase.displayed, testCase.displayed)
			if stderr != want {
				t.Errorf("stderr=%q, want %q", stderr, want)
			}
		})
	}
}

func TestCommandErrorKeepsStructuredControlValues(t *testing.T) {
	original := &studio.APIError{
		StatusCode: 403, Code: "ERR\x1bCODE",
		Message: "resource \x1b[2J\x07\x08\x7f 中文\\literal\r\nnext\tpart",
		Hint:    "hint \x00\x1b\r\nnext\tpart",
	}
	want := types.CLIError{
		Code:    original.Code,
		Message: "resource \x1b[2J\x07\x08\x7f 中文\\literal  next part",
		Hint:    "hint \x00\x1b  next part",
	}
	for _, format := range []string{"json", "yaml"} {
		t.Run(format, func(t *testing.T) {
			stderr := &bytes.Buffer{}
			app := NewApp(&bytes.Buffer{}, stderr)
			app.writeCommandError(format, original)
			var got types.CLIError
			var err error
			if format == "json" {
				err = json.Unmarshal(stderr.Bytes(), &got)
			} else {
				err = yaml.Unmarshal(stderr.Bytes(), &got)
			}
			if err != nil {
				t.Fatal(err)
			}
			if got != want {
				t.Errorf("structured error=%#v, want %#v", got, want)
			}
		})
	}
}

func TestStdioErrorEscapesOnlyTerminalDiagnostic(t *testing.T) {
	for _, request := range []string{
		`{"jsonrpc":"2.0","id":"call-1","method":"tools/list"}`,
		`{"jsonrpc":"2.0","method":"notifications/initialized"}`,
	} {
		t.Run(request, func(t *testing.T) {
			original := "transport \x1b[2J\x07\x08\x7f 中文\\literal\r\nnext\tpart"
			out, stderr := &bytes.Buffer{}, &bytes.Buffer{}
			writeStdioError(out, stderr, request, fmt.Errorf("wrapped: %w", errors.New(original)))
			want := "mcp stdio proxy call failed: wrapped: transport \\x1b[2J\\x07\\x08\\x7f 中文\\literal  next part\n"
			if stderr.String() != want {
				t.Errorf("stderr=%q, want %q", stderr.String(), want)
			}
			if !strings.Contains(request, `"id"`) {
				if out.Len() != 0 {
					t.Fatalf("notification received error frame: %q", out.String())
				}
				return
			}
			frame := decodeStdioErrorFrame(t, out)
			message := "wrapped: transport \x1b[2J\x07\x08\x7f 中文\\literal  next part"
			if frame.Error.Message != message || frame.Error.Data == nil || frame.Error.Data.Message != message {
				t.Errorf("structured error changed: %#v", frame.Error)
			}
		})
	}
}

func TestStdioTerminalDiagnosticRetainsByteLimit(t *testing.T) {
	for _, message := range []string{
		strings.Repeat("\x1b", stdioDiagnosticLimit),
		strings.Repeat("x", stdioDiagnosticLimit-1) + "中文",
	} {
		t.Run(fmt.Sprintf("first-byte-%x", message[0]), func(t *testing.T) {
			out, stderr := &bytes.Buffer{}, &bytes.Buffer{}
			writeStdioError(out, stderr, `{"jsonrpc":"2.0","id":1,"method":"tools/list"}`, errors.New(message))
			diagnostic := strings.TrimSuffix(strings.TrimPrefix(stderr.String(), "mcp stdio proxy call failed: "), "\n")
			if len(diagnostic) > stdioDiagnosticLimit || !utf8.ValidString(diagnostic) {
				t.Fatalf("diagnostic is oversized or invalid UTF-8: len=%d value=%q", len(diagnostic), diagnostic)
			}
			if strings.ContainsRune(diagnostic, '\x1b') {
				t.Errorf("stderr contains raw ESC: %q", diagnostic)
			}
			frame := decodeStdioErrorFrame(t, out)
			if len(frame.Error.Message) > stdioDiagnosticLimit || !utf8.ValidString(frame.Error.Message) {
				t.Errorf("structured message cap changed: %#v", frame.Error)
			}
		})
	}
}
