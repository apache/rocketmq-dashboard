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
	"encoding/json"
	"strings"
	"testing"
)

func toolCallPayload(t *testing.T, tool string, arguments map[string]any) []byte {
	t.Helper()
	params := map[string]any{"name": tool}
	if arguments != nil {
		params["arguments"] = arguments
	}
	payload, err := json.Marshal(map[string]any{
		"jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": params,
	})
	if err != nil {
		t.Fatal(err)
	}
	return payload
}

func boundArguments(t *testing.T, payload []byte) map[string]any {
	t.Helper()
	var message struct {
		Params struct {
			Arguments map[string]any `json:"arguments"`
		} `json:"params"`
	}
	if err := json.Unmarshal(payload, &message); err != nil {
		t.Fatal(err)
	}
	return message.Params.Arguments
}

// TestBindToolInstanceInjectsDeclaredInstanceField verifies that the explicit
// --instance-id value is passed through to tools whose catalog schema declares
// the instance identifier field (instanceId, or the transitional cluster name
// in the pre-regeneration catalog).
func TestBindToolInstanceInjectsDeclaredInstanceField(t *testing.T) {
	payload := toolCallPayload(t, "rmq.topic.list", nil)
	bound, err := bindToolInstance(payload, "instance-dev")
	if err != nil {
		t.Fatal(err)
	}
	arguments := boundArguments(t, bound)
	if len(arguments) != 1 {
		t.Fatalf("arguments = %#v, want exactly one injected instance field", arguments)
	}
	for _, name := range []string{"instanceId", "cluster"} {
		if value, ok := arguments[name]; ok {
			if value != "instance-dev" {
				t.Fatalf("%s = %#v, want instance-dev", name, value)
			}
			return
		}
	}
	t.Fatalf("no instance identifier injected: %#v", arguments)
}

// TestBindToolInstanceKeepsExplicitValue verifies pass-through semantics: a
// value explicitly provided by the MCP client is never overwritten.
func TestBindToolInstanceKeepsExplicitValue(t *testing.T) {
	payload := toolCallPayload(t, "rmq.topic.list", map[string]any{"instanceId": "explicit-instance"})
	bound, err := bindToolInstance(payload, "instance-dev")
	if err != nil {
		t.Fatal(err)
	}
	arguments := boundArguments(t, bound)
	if len(arguments) != 1 {
		t.Fatalf("arguments = %#v, want only the explicit client value", arguments)
	}
	if value := arguments["instanceId"]; value != "explicit-instance" {
		t.Fatalf("instanceId = %#v, want the explicit client value", value)
	}
}

// TestBindToolInstanceSkipsToolsWithoutInstanceField verifies that tools whose
// catalog schema declares no instance identifier are never modified. Today this
// covers tools unknown to the catalog; after the catalog regeneration it also
// covers the 11 platform-level tools (decisions 25/26) whose schemas drop
// instanceId.
func TestBindToolInstanceSkipsToolsWithoutInstanceField(t *testing.T) {
	payload := toolCallPayload(t, "rmq.platform.probe.unknown", map[string]any{"topicName": "orders"})
	original := string(payload)
	bound, err := bindToolInstance(payload, "instance-dev")
	if err != nil {
		t.Fatal(err)
	}
	if string(bound) != original {
		t.Fatalf("payload was modified: %s", bound)
	}
	if arguments := boundArguments(t, bound); arguments["instanceId"] != nil || arguments["cluster"] != nil {
		t.Fatalf("instance identifier leaked into arguments: %#v", arguments)
	}
}

// TestBindToolInstanceIgnoresNonToolCallMessages verifies that initialize and
// other JSON-RPC methods are forwarded untouched.
func TestBindToolInstanceIgnoresNonToolCallMessages(t *testing.T) {
	payload := []byte(`{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}`)
	bound, err := bindToolInstance(payload, "instance-dev")
	if err != nil {
		t.Fatal(err)
	}
	if string(bound) != string(payload) {
		t.Fatalf("initialize payload was modified: %s", bound)
	}
}

// A JSON-RPC response (id + result/error, no method) used to fall through to the
// generic "missing method" error, which tells the caller nothing about what was
// actually wrong. See the review on #4637.
func TestDecodeMCPMessageRejectsResponseFrameWithExplicitError(t *testing.T) {
	_, err := decodeMCPMessage([]byte(`{"jsonrpc":"2.0","id":7,"result":{"ok":true}}`))
	if err == nil {
		t.Fatal("expected an error for a response frame")
	}
	if !strings.Contains(err.Error(), "response") {
		t.Fatalf("expected an explicit response-frame error, got: %v", err)
	}
	if strings.Contains(err.Error(), "missing method") {
		t.Fatalf("the misleading missing-method error is still used: %v", err)
	}

	_, err = decodeMCPMessage([]byte(`{"jsonrpc":"2.0","id":8,"error":{"code":-32000,"message":"boom"}}`))
	if err == nil || !strings.Contains(err.Error(), "response") {
		t.Fatalf("expected an explicit error for an error frame too, got: %v", err)
	}

	// Requests and notifications must keep decoding.
	if _, err := decodeMCPMessage([]byte(`{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{}}`)); err != nil {
		t.Fatalf("request frame should still decode: %v", err)
	}
	if _, err := decodeMCPMessage([]byte(`{"jsonrpc":"2.0","method":"notifications/initialized"}`)); err != nil {
		t.Fatalf("notification frame should still decode: %v", err)
	}
}
