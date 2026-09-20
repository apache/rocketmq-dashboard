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
package studio

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/types"
)

func TestCallTool(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost || r.URL.Path != toolCallPath ||
			!strings.HasPrefix(r.Header.Get("Authorization"),
				"rmq-hmac-sha256 Credential=test-ak, Signature=") ||
			r.Header.Get(HeaderInstance) != "instance-dev" {
			http.Error(w, "unexpected request", http.StatusUnauthorized)
			return
		}
		var request struct {
			Name      string         `json:"name"`
			Arguments map[string]any `json:"arguments"`
		}
		if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}
		_ = json.NewEncoder(w).Encode(map[string]any{
			"code": 200, "message": "success",
			"data": map[string]any{"items": []any{}},
		})
	}))
	defer server.Close()

	client := NewClient(server.Client())
	result, err := client.CallTool(context.Background(), Target{
		Server: server.URL, InstanceID: "instance-dev",
		Credential: Credential{AccessKey: "test-ak", SecretKey: "test-sk"}, Timeout: time.Second,
	}, "rmq.topic.list", map[string]any{"instanceId": "instance-dev"})
	resultMap, ok := result.(map[string]any)
	if err != nil || !ok || resultMap["items"] == nil {
		t.Fatalf("result=%#v err=%v", result, err)
	}
}

func TestCallToolKeepsCatalogControlsInArguments(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var request types.ToolCallRequest
		if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
			t.Fatal(err)
		}
		if request.Arguments["dry_run"] != true ||
			request.Arguments["topicName"] != "orders" {
			t.Errorf("unexpected transport request: %#v", request)
		}
		_ = json.NewEncoder(w).Encode(map[string]any{
			"code": 200, "message": "success", "data": map[string]any{
				"status":        "PLANNED",
				"instanceId":    "instance-dev",
				"confirm_token": "confirmation", "plan": map[string]any{"summary": "create"},
			},
		})
	}))
	defer server.Close()

	client := NewClient(server.Client())
	result, err := client.CallTool(context.Background(), Target{
		Server: server.URL, InstanceID: "instance-dev",
		Credential: Credential{AccessKey: "test-ak", SecretKey: "test-sk"}, Timeout: time.Second,
	}, "rmq.topic.update", map[string]any{
		"instanceId": "instance-dev", "topicName": "orders", "dry_run": true,
	})
	if err != nil {
		t.Fatal(err)
	}
	mutation, err := types.DecodeMutationOutput(result)
	if err != nil {
		t.Fatal(err)
	}
	if mutation.Status != types.MutationPlanned ||
		mutation.ConfirmToken != "confirmation" || mutation.Plan == nil {
		t.Fatalf("unexpected result: %#v", mutation)
	}
}

func TestRequestRejectsOversizedResponseBody(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write(bytes.Repeat([]byte("x"), 4096))
	}))
	defer server.Close()

	client := NewClient(server.Client())
	original := maxResponseBytes
	maxResponseBytes = 1024
	defer func() { maxResponseBytes = original }()

	var out map[string]any
	err := client.request(context.Background(), Target{
		Server:     server.URL,
		InstanceID: "instance-dev",
		Credential: Credential{AccessKey: "test-ak", SecretKey: "test-sk"},
		Timeout:    time.Second,
	}, http.MethodGet, "/api/oversized", nil, &out)
	if err == nil || !strings.Contains(err.Error(), "exceeds") {
		t.Fatalf("expected oversized-response error, got %v", err)
	}
}
