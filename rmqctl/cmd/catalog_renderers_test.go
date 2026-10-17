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
	"io"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"

	toolcatalog "github.com/apache/rocketmq-dashboard/rmqctl/internal/catalog"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/output"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/types"
	"gopkg.in/yaml.v3"
)

func TestMessageQueryCompletenessWarningTest(t *testing.T) {
	commands := []struct {
		tool string
		args []string
	}{
		{"rmq.message.query", []string{"message", "query", "--topic-name", "orders", "--key", "order-key", "--limit", "1"}},
		{"rmq.message.query_by_topic", []string{"message", "query-by-topic", "--topic-name", "orders", "--limit", "1"}},
	}
	cases := []struct {
		name      string
		truncated bool
		skipped   int
		empty     bool
	}{
		{"limit", true, 5, false},
		{"provider_bound", true, 0, false},
		{"complete", false, 0, false},
		{"empty", true, 0, true},
	}
	for _, command := range commands {
		for _, tc := range cases {
			for _, format := range []string{"table", "json", "yaml"} {
				t.Run(command.tool+"/"+tc.name+"/"+format, func(t *testing.T) {
					items := []any{}
					if !tc.empty {
						items = append(items, map[string]any{"msgId": "m1", "topic": "orders", "storeTime": 1, "size": 3})
					}
					payload := map[string]any{
						"items": items, "resultMayBeTruncated": tc.truncated, "skippedCount": tc.skipped,
					}
					args := append([]string{}, command.args...)
					if format != "table" {
						args = append(args, "--output", format)
					}
					stdout, stderr, exitCode := executeCatalogResult(t, command.tool, payload, args...)
					if exitCode != 0 {
						t.Fatalf("exit = %d, stderr = %q", exitCode, stderr)
					}
					if format == "table" && !tc.empty {
						const want = "MSGID  SIZE  STORETIME  TOPIC\nm1     3     1          orders\n"
						if stdout != want {
							t.Errorf("stdout changed: got %q, want %q", stdout, want)
						}
					} else {
						var decoded any
						var err error
						if format == "yaml" {
							err = yaml.Unmarshal([]byte(stdout), &decoded)
						} else {
							err = json.Unmarshal([]byte(stdout), &decoded)
						}
						if err != nil {
							t.Fatalf("decode output: %v; stdout = %q", err, stdout)
						}
						got, err := json.Marshal(decoded)
						if err != nil {
							t.Fatal(err)
						}
						want, err := json.Marshal(payload)
						if err != nil {
							t.Fatal(err)
						}
						if !bytes.Equal(got, want) {
							t.Errorf("payload changed: got %s, want %s", got, want)
						}
					}
					wantWarning := ""
					if format == "table" && !tc.empty && tc.truncated {
						wantWarning = fmt.Sprintf("WARNING: Results may be incomplete (resultMayBeTruncated=true); skippedCount=%d rows omitted by limit from the provider-bounded result. More messages may exist.\n", tc.skipped)
					}
					if stderr != wantWarning {
						t.Errorf("stderr = %q, want %q", stderr, wantWarning)
					}
				})
			}
		}
	}
}

func TestCatalogOtherTableOutputTest(t *testing.T) {
	rows := []map[string]any{{
		"name": "orders", "writeQueues": 4, "readQueues": 4,
		"messageCount": 7, "tps": 0, "consumerGroupCount": 1,
	}}
	payload := map[string]any{"items": rows}
	stdout, stderr, exitCode := executeCatalogResult(t, "rmq.topic.list", payload, "topic", "list", "--output", "table")
	var want bytes.Buffer
	if err := output.Rows(&want, rows, []output.Column{
		{Header: "CONSUMERGROUPCOUNT", Key: "consumerGroupCount"},
		{Header: "MESSAGECOUNT", Key: "messageCount"},
		{Header: "NAME", Key: "name"},
		{Header: "READQUEUES", Key: "readQueues"},
		{Header: "TPS", Key: "tps"},
		{Header: "WRITEQUEUES", Key: "writeQueues"},
	}); err != nil {
		t.Fatal(err)
	}
	if exitCode != 0 || stderr != "" || stdout != want.String() {
		t.Fatalf("table changed: exit = %d, stdout = %q, stderr = %q", exitCode, stdout, stderr)
	}
}

func TestCatalogMutationOutputTest(t *testing.T) {
	payload := map[string]any{
		"status": "PLANNED", "instanceId": "instance-dev", "confirm_token": "confirmation",
		"plan": map[string]any{"summary": "Update topic orders"},
	}
	stdout, stderr, exitCode := executeCatalogResult(t, "rmq.topic.update", payload,
		"topic", "update", "--topic-name", "orders", "--write-queues", "8", "--dry-run")
	var want bytes.Buffer
	if err := output.ToolCallSummary(&want, payload); err != nil {
		t.Fatal(err)
	}
	if exitCode != 0 || stderr != "" || stdout != want.String() {
		t.Fatalf("mutation changed: exit = %d, stdout = %q, stderr = %q", exitCode, stdout, stderr)
	}
}

func TestRenderTableWriterErrorsTest(t *testing.T) {
	tool, ok := toolcatalog.LookupTool("rmq.message.query")
	if !ok {
		t.Fatal("message query tool is missing")
	}
	payload := map[string]any{
		"items":                []any{map[string]any{"msgId": "m1"}},
		"resultMayBeTruncated": true, "skippedCount": 0,
	}
	wantErr := errors.New("writer failed")
	t.Run("stdout", func(t *testing.T) {
		var stderr bytes.Buffer
		err := renderTable(catalogErrorWriter{wantErr}, &stderr, tool, payload)
		if !errors.Is(err, wantErr) || stderr.Len() != 0 {
			t.Fatalf("err = %v, stderr = %q; want stdout error without warning", err, stderr.String())
		}
	})
	t.Run("stderr", func(t *testing.T) {
		var stdout bytes.Buffer
		err := renderTable(&stdout, catalogErrorWriter{wantErr}, tool, payload)
		if !errors.Is(err, wantErr) || stdout.String() != "MSGID\nm1\n" {
			t.Fatalf("err = %v, stdout = %q; want stderr error after unchanged table", err, stdout.String())
		}
	})
}

func TestRenderTableWarningScopeTest(t *testing.T) {
	for _, tc := range []struct {
		name      string
		tool      string
		truncated any
	}{
		{"other_tool", "rmq.topic.list", true},
		{"string_true", "rmq.message.query", "true"},
		{"missing_flag", "rmq.message.query", nil},
	} {
		t.Run(tc.name, func(t *testing.T) {
			tool, ok := toolcatalog.LookupTool(tc.tool)
			if !ok {
				t.Fatalf("tool %q is missing", tc.tool)
			}
			payload := map[string]any{
				"items":        []any{map[string]any{"name": "row"}},
				"skippedCount": 5,
			}
			if tc.truncated != nil {
				payload["resultMayBeTruncated"] = tc.truncated
			}
			var stderr bytes.Buffer
			if err := renderTable(io.Discard, &stderr, tool, payload); err != nil || stderr.Len() != 0 {
				t.Fatalf("unexpected warning or error: stderr = %q, err = %v", stderr.String(), err)
			}
		})
	}
}

type catalogErrorWriter struct{ err error }

func (w catalogErrorWriter) Write([]byte) (int, error) { return 0, w.err }

func executeCatalogResult(t *testing.T, tool string, payload any, args ...string) (string, string, int) {
	t.Helper()
	var requests atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requests.Add(1)
		var request types.ToolCallRequest
		if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
			t.Errorf("decode request: %v", err)
			http.Error(w, "invalid request", http.StatusBadRequest)
			return
		}
		if r.Method != http.MethodPost || r.URL.Path != "/api/mcp/tools/call" || request.Name != tool {
			t.Errorf("unexpected request: %s %s, tool = %q", r.Method, r.URL.Path, request.Name)
		}
		writeStudioSuccess(t, w, payload)
	}))
	defer server.Close()
	stdout, stderr, exitCode := executeTestApp(t, server.Client(), server.URL, args...)
	if requests.Load() != 1 {
		t.Errorf("requests = %d, want exactly one", requests.Load())
	}
	return stdout, stderr, exitCode
}
