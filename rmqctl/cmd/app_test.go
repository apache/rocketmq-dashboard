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
	"net/http"
	"net/http/httptest"
	"reflect"
	"strings"
	"testing"

	toolcatalog "github.com/apache/rocketmq-dashboard/rmqctl/internal/catalog"
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
	instanceKey := catalogInstanceArgumentKey(t, "rmq.topic.update")
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
			"instanceId":    request.Arguments[instanceKey],
			"plan":          map[string]any{"summary": "Create topic orders"},
			"confirm_token": confirmToken,
		}
		if dryRun, ok := request.Arguments["dry_run"]; !ok || dryRun != true {
			mutation["status"] = "EXECUTED"
			mutation["result"] = map[string]any{"topic": request.Arguments["topicName"]}
		}
		writeStudioSuccess(t, w, mutation)
	}))
	defer server.Close()

	stdout, stderr, exitCode := executeTestApp(t, server.Client(), server.URL,
		"--output", "json",
		"topic", "update", "--dry-run", "--topic-name", "orders", "--write-queues", "8",
	)
	if exitCode != 0 {
		t.Fatalf("preview failed: %s", stderr)
	}
	call := <-observed
	wantArguments := map[string]any{
		instanceKey: "instance-dev", "topicName": "orders", "writeQueues": float64(8), "dry_run": true,
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
		"topic", "update", "--topic-name", "orders", "--write-queues", "8",
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

// TestCatalogYesAutoFetchesConfirmToken verifies that --yes on an L2 mutation
// without an explicit --confirm-token transparently runs a dry-run preview to
// obtain the token and then executes, so callers need not script the two-phase
// handshake manually.
func TestCatalogYesAutoFetchesConfirmToken(t *testing.T) {
	const confirmToken = "auto-confirmation"
	instanceKey := catalogInstanceArgumentKey(t, "rmq.topic.update")
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
			"instanceId":    request.Arguments[instanceKey],
			"plan":          map[string]any{"summary": "Create topic orders"},
			"confirm_token": confirmToken,
		}
		if dryRun, ok := request.Arguments["dry_run"]; !ok || dryRun != true {
			mutation["status"] = "EXECUTED"
			mutation["result"] = map[string]any{"topic": request.Arguments["topicName"]}
		}
		writeStudioSuccess(t, w, mutation)
	}))
	defer server.Close()

	stdout, stderr, exitCode := executeTestApp(t, server.Client(), server.URL,
		"--output", "json",
		"topic", "update", "--topic-name", "orders", "--write-queues", "8",
		"--yes",
	)
	if exitCode != 0 {
		t.Fatalf("apply failed: %s", stderr)
	}

	preview := <-observed
	if preview.request.Arguments["dry_run"] != true ||
		preview.request.Arguments["confirm_token"] != nil {
		t.Fatalf("expected an auto dry-run preview first: %#v", preview.request)
	}
	applyCall := <-observed
	if applyCall.request.Arguments["dry_run"] != nil ||
		applyCall.request.Arguments["confirm_token"] != confirmToken {
		t.Fatalf("expected the apply call to carry the auto-fetched token: %#v", applyCall.request)
	}
	var apply map[string]any
	if err := json.Unmarshal([]byte(stdout), &apply); err != nil || apply["status"] != "EXECUTED" {
		t.Fatalf("unexpected apply output: %#v, err=%v", apply, err)
	}
}

// catalogInstanceArgumentKey returns the arguments key carrying the Studio
// instance identifier for the named catalog tool ("instanceId" after the
// catalog regeneration, transitional "cluster" before it).
func catalogInstanceArgumentKey(t *testing.T, toolName string) string {
	t.Helper()
	tool, ok := toolcatalog.LookupTool(toolName)
	if !ok {
		t.Fatalf("tool %q is missing from the catalog", toolName)
	}
	name, ok := toolcatalog.InstanceFieldName(tool.InputSchema)
	if !ok {
		t.Fatalf("tool %q does not declare an instance identifier field", toolName)
	}
	return name
}

// TestCatalogRequiresExplicitInstanceID verifies decision 7: no default
// injection from the context or config. A tool call without --instance-id
// fails with INVALID_ARGUMENT before any HTTP request is sent.
func TestCatalogRequiresExplicitInstanceID(t *testing.T) {
	requests := make(chan struct{}, 1)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requests <- struct{}{}
		writeStudioSuccess(t, w, map[string]any{"items": []any{}})
	}))
	defer server.Close()

	stdout, stderr := &bytes.Buffer{}, &bytes.Buffer{}
	app := NewApp(stdout, stderr)
	app.HTTP = server.Client()
	app.Store.Getenv = testEnv
	app.confirm = stubConfirmReject
	configPath := newTestConfigPath(t)
	if err := app.Store.Save(configPath, newTestConfig(server.URL)); err != nil {
		t.Fatal(err)
	}
	exitCode := app.Execute([]string{"--config", configPath, "topic", "list"})
	if exitCode != 1 {
		t.Fatalf("exit code = %d, want 1; stdout=%s stderr=%s", exitCode, stdout, stderr)
	}
	if !strings.Contains(stderr.String(), "INVALID_ARGUMENT") ||
		!strings.Contains(stderr.String(), "--instance-id is required") {
		t.Fatalf("stderr = %q, want INVALID_ARGUMENT for the missing --instance-id", stderr)
	}
	if !strings.Contains(stderr.String(), "explicitly") {
		t.Fatalf("stderr = %q, want a hint to pass --instance-id explicitly", stderr)
	}
	select {
	case <-requests:
		t.Fatal("request was sent without --instance-id")
	default:
	}
}

// TestCatalogPassesExplicitInstanceIDToArgumentsAndHeader verifies that the
// explicit --instance-id value is passed through to the tool arguments (for
// tools whose schema declares the instance identifier) and used for the
// x-rmq-instance-id request header/HMAC signing.
func TestCatalogPassesExplicitInstanceIDToArgumentsAndHeader(t *testing.T) {
	instanceKey := catalogInstanceArgumentKey(t, "rmq.topic.list")
	observed := make(chan observedToolCall, 1)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var request types.ToolCallRequest
		if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
			t.Error(err)
		}
		observed <- observedToolCall{
			method: r.Method, path: r.URL.Path,
			authorization: r.Header.Get("Authorization"), request: request,
		}
		if r.Header.Get("x-rmq-instance-id") != "instance-profile" {
			t.Error("wrong x-rmq-instance-id header")
		}
		writeStudioSuccess(t, w, map[string]any{"items": []any{}})
	}))
	defer server.Close()

	_, stderr, exitCode := executeTestAppWithInstance(t, server.Client(), server.URL, "profile",
		"--output", "json", "topic", "list")
	if exitCode != 0 {
		t.Fatalf("explicit instance failed: exit=%d stderr=%s", exitCode, stderr)
	}
	call := <-observed
	if value := call.request.Arguments[instanceKey]; value != "instance-profile" {
		t.Fatalf("%s = %v, want the explicit --instance-id value", instanceKey, value)
	}
	if !strings.HasPrefix(call.authorization, "RMQ-HMAC-SHA256 Credential=test-ak, Signature=") {
		t.Fatalf("authorization = %q, want HMAC signature", call.authorization)
	}
}

// TestCatalogRejectsRemovedPerToolClusterFlag verifies the per-tool --cluster
// flag is gone: the instance identifier comes exclusively from the global
// --instance-id flag.
func TestCatalogRejectsRemovedPerToolClusterFlag(t *testing.T) {
	_, stderr, exitCode := executeTestApp(t, httptest.NewServer(http.HandlerFunc(
		func(w http.ResponseWriter, r *http.Request) {
			writeStudioSuccess(t, w, map[string]any{"items": []any{}})
		})).Client(), "http://127.0.0.1:1",
		"topic", "list", "--cluster", "instance-dev",
	)
	if exitCode == 0 {
		t.Fatal("per-tool --cluster flag must no longer exist")
	}
	if !strings.Contains(stderr, "unknown flag") {
		t.Fatalf("stderr = %q, want an unknown flag error", stderr)
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
		"topic", "list",
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
		"topic", "update", "--topic-name", "orders", "--write-queues", "8",
		"--confirm-token", "confirmation",
	)
	if exitCode != 0 {
		t.Fatalf("interactive yes should proceed: exit=%d stderr=%s", exitCode, stderr)
	}
	if call := <-observed; call.Arguments["topicName"] != "orders" {
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
