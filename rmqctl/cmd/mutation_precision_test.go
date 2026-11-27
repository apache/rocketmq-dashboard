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
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestMutationPreviewPreservesIntegerPrecision(t *testing.T) {
	const number = "9007199254740993"
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var request struct {
			Name      string                     `json:"name"`
			Arguments map[string]json.RawMessage `json:"arguments"`
		}
		if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
			t.Error(err)
			return
		}
		if request.Name != "rmq.group.reset_offset" || string(request.Arguments["dry_run"]) != "true" {
			t.Errorf("expected reset-offset dry-run, got %#v", request)
		}
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprint(w, `{"code":200,"message":"OK","data":{"status":"PLANNED","instanceId":"test-instance","confirm_token":"synthetic-token","plan":{"before":{"queues":[{"offset":9007199254740993,"lag":0}]},"after":{"queues":[{"offset":42,"lag":0}]}}}}`)
	}))
	defer server.Close()
	for _, format := range []string{"table", "json", "yaml"} {
		t.Run(format, func(t *testing.T) {
			out, errOut := &bytes.Buffer{}, &bytes.Buffer{}
			app := NewApp(out, errOut)
			app.Store.Getenv = testEnv
			app.HTTP = server.Client()
			path := newTestConfigPath(t)
			if err := app.Store.Save(path, newTestConfig(server.URL)); err != nil {
				t.Fatal(err)
			}
			code := app.Execute([]string{"--config", path, "--instance-id", "test-instance", "--output", format, "group", "reset-offset", "--group-name", "orders", "--topic-name", "orders", "--timestamp", "1700000000000", "--dry-run"})
			t.Logf("format=%s exit=%d output=%s stderr=%s", format, code, out.String(), errOut.String())
			if code != 0 {
				t.Fatalf("unexpected command failure")
			}
			if !strings.Contains(out.String(), number) {
				t.Errorf("original queue offset %s is absent from %s output", number, format)
			}
		})
	}
}
