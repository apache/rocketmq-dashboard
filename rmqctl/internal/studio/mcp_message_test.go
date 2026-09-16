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
