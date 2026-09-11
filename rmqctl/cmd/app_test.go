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
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"reflect"
	"strings"
	"testing"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/types"
)

type observedToolCall struct {
	method        string
	path          string
	authorization string
	request       types.ToolCallRequest
}

func TestCatalogMutationRoundTrip(t *testing.T) {
	const confirmToken = "confirmation"
	observed := make(chan observedToolCall, 2)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var request types.ToolCallRequest
		if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}
		observed <- observedToolCall{r.Method, r.URL.Path, r.Header.Get("Authorization"), request}
		mutation := map[string]any{
			"status":        "PLANNED",
			"cluster":       request.Arguments["cluster"],
			"plan":          map[string]any{"summary": "Create topic orders"},
			"confirm_token": confirmToken,
		}
		if dryRun, ok := request.Arguments["dry_run"]; !ok || dryRun != true {
			mutation["status"] = "EXECUTED"
			mutation["result"] = map[string]any{"topic": request.Arguments["topic"]}
		}
		writeStudioSuccess(t, w, mutation)
	}))
	defer server.Close()

	stdout, stderr, exitCode := executeTestApp(t, server.Client(), server.URL,
		"--output", "json",
		"topic", "create", "--dry-run", "--topic", "orders", "--write-queues", "8",
	)
	if exitCode != 0 {
		t.Fatalf("preview failed: %s", stderr)
	}
	call := <-observed
	wantArguments := map[string]any{
		"cluster": "instance-dev", "topic": "orders", "writeQueues": float64(8), "dry_run": true,
	}
	if call.method != http.MethodPost || call.path != "/api/mcp/tools/call" ||
		!strings.HasPrefix(call.authorization, "RMQ-HMAC-SHA256 Credential=test-ak, Signature=") ||
		!reflect.DeepEqual(call.request.Arguments, wantArguments) {
		t.Fatalf("unexpected preview request: %#v", call)
	}
	var preview map[string]any
	if err := json.Unmarshal([]byte(stdout), &preview); err != nil {
		t.Fatalf("decode preview output: %v", err)
	}
	if preview["confirm_token"] != confirmToken || preview["plan"] == nil {
		t.Fatalf("unexpected preview output: %#v", preview)
	}

	stdout, stderr, exitCode = executeTestApp(t, server.Client(), server.URL,
		"--output", "json",
		"topic", "create", "--topic", "orders", "--write-queues", "8",
		"--confirm-token", confirmToken,
		"--yes",
	)
	if exitCode != 0 {
		t.Fatalf("apply failed: %s", stderr)
	}
	if call := <-observed; call.request.Arguments["dry_run"] != nil ||
		call.request.Arguments["confirm_token"] != confirmToken {
		t.Fatalf("unexpected apply request: %#v", call.request)
	}
	var apply map[string]any
	if err := json.Unmarshal([]byte(stdout), &apply); err != nil || apply["status"] != "EXECUTED" {
		t.Fatalf("unexpected apply output: %#v, err=%v", apply, err)
	}
}

func TestCatalogDefaultsClusterToContextInstance(t *testing.T) {
	observed := make(chan types.ToolCallRequest, 1)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var request types.ToolCallRequest
		if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
			t.Error(err)
		}
		if r.Header.Get("X-RMQ-Cluster") != "instance-profile" {
			t.Error("wrong authenticated Instance")
		}
		observed <- request
		writeStudioSuccess(t, w, map[string]any{"items": []any{}})
	}))
	defer server.Close()

	_, stderr, exitCode := executeTestAppWithInstance(t, server.Client(), server.URL, "profile",
		"--output", "json", "topic", "list")
	if exitCode != 0 {
		t.Fatalf("context default failed: exit=%d stderr=%s", exitCode, stderr)
	}
	if request := <-observed; request.Arguments["cluster"] != "instance-profile" {
		t.Fatalf("cluster = %v, want context Instance", request.Arguments["cluster"])
	}
}

func TestCatalogPreservesExplicitCluster(t *testing.T) {
	observed := make(chan types.ToolCallRequest, 1)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var request types.ToolCallRequest
		if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}
		observed <- request
		writeStudioSuccess(t, w, map[string]any{"items": []any{}})
	}))
	defer server.Close()

	_, stderr, exitCode := executeTestApp(t, server.Client(), server.URL,
		"topic", "list", "--cluster", "instance-dev",
	)
	if exitCode != 0 {
		t.Fatalf("explicit cluster failed: exit=%d stderr=%s", exitCode, stderr)
	}
	if cluster := (<-observed).Arguments["cluster"]; cluster != "instance-dev" {
		t.Fatalf("cluster = %#v, want explicit value", cluster)
	}
}

// TestCatalogAllowsL1WithoutYes verifies that L1 (read-only) operations are
// never gated by the confirmation prompt.
func TestCatalogAllowsL1WithoutYes(t *testing.T) {
	observed := make(chan types.ToolCallRequest, 1)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var request types.ToolCallRequest
		if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}
		observed <- request
		writeStudioSuccess(t, w, map[string]any{"items": []any{}})
	}))
	defer server.Close()

	_, stderr, exitCode := executeTestApp(t, server.Client(), server.URL,
		"topic", "list", "--cluster", "instance-dev",
	)
	if exitCode != 0 {
		t.Fatalf("L1 without --yes should succeed: exit=%d stderr=%s", exitCode, stderr)
	}
	<-observed
}

// TestCatalogInteractiveConfirmAcceptsYes verifies that when the user types
// "yes" at the prompt, the L2 operation proceeds to the server.
func TestCatalogInteractiveConfirmAcceptsYes(t *testing.T) {
	observed := make(chan types.ToolCallRequest, 1)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var request types.ToolCallRequest
		if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}
		observed <- request
		writeStudioSuccess(t, w, map[string]any{"status": "EXECUTED", "result": map[string]any{"topic": "orders"}})
	}))
	defer server.Close()

	stdout, stderr, exitCode := executeTestAppWithStdin(t, server.Client(), server.URL, "dev",
		"yes\n",
		"--output", "json",
		"topic", "create", "--cluster", "instance-dev", "--topic", "orders", "--write-queues", "8",
		"--confirm-token", "confirmation",
	)
	if exitCode != 0 {
		t.Fatalf("interactive yes should proceed: exit=%d stderr=%s", exitCode, stderr)
	}
	if call := <-observed; call.Arguments["topic"] != "orders" {
		t.Fatalf("unexpected request: %#v", call)
	}
	if !strings.Contains(stderr, "WARNING") || !strings.Contains(stderr, server.URL) ||
		!strings.Contains(stderr, "Type \"yes\" to continue:") {
		t.Fatalf("expected confirmation prompt in stderr: %s", stderr)
	}
	var result map[string]any
	if err := json.Unmarshal([]byte(stdout), &result); err != nil || result["status"] != "EXECUTED" {
		t.Fatalf("expected only JSON result in stdout: %s, err=%v", stdout, err)
	}
}
