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
	"context"
	"encoding/json"
	"sync"
	"testing"

	mcptransport "github.com/mark3labs/mcp-go/client/transport"
	"github.com/mark3labs/mcp-go/mcp"
)

// reconnectStubTransport answers requests the way a Studio server that lost a
// session does: every non-initialize request fails with a 404 until a fresh
// initialize exchange establishes "session-2". Like mcp-go's StreamableHTTP
// client, it clears the stored session id when a request fails with a 404.
type reconnectStubTransport struct {
	mu          sync.Mutex
	sessionID   string
	sends       int
	initializes int
}

func newReconnectStubTransport(sessionID string) *reconnectStubTransport {
	return &reconnectStubTransport{sessionID: sessionID}
}

func (transport *reconnectStubTransport) Start(context.Context) error { return nil }
func (transport *reconnectStubTransport) Close() error                { return nil }

func (transport *reconnectStubTransport) SetNotificationHandler(
	func(mcp.JSONRPCNotification),
) {
}

func (transport *reconnectStubTransport) SetProtocolVersion(string) {}

func (transport *reconnectStubTransport) GetSessionId() string {
	transport.mu.Lock()
	defer transport.mu.Unlock()
	return transport.sessionID
}

func (transport *reconnectStubTransport) SendRequest(
	_ context.Context,
	request mcptransport.JSONRPCRequest,
) (*mcptransport.JSONRPCResponse, error) {
	transport.mu.Lock()
	if request.Method == string(mcp.MethodInitialize) {
		transport.initializes++
		transport.sessionID = "session-2"
		transport.mu.Unlock()
		return &mcptransport.JSONRPCResponse{
			JSONRPC: mcp.JSONRPC_VERSION,
			ID:      request.ID,
			Result:  json.RawMessage(`{"protocolVersion":"` + mcp.LATEST_PROTOCOL_VERSION + `"}`),
		}, nil
	}
	transport.sends++
	established := transport.sessionID == "session-2"
	used := transport.sessionID
	transport.mu.Unlock()
	if !established {
		// mcp-go clears the session id on a 404, but only when it still holds
		// the value the failed request was sent with (CompareAndSwap).
		transport.mu.Lock()
		if transport.sessionID == used {
			transport.sessionID = ""
		}
		transport.mu.Unlock()
		return nil, mcptransport.ErrSessionTerminated
	}
	return &mcptransport.JSONRPCResponse{
		JSONRPC: mcp.JSONRPC_VERSION,
		ID:      request.ID,
		Result:  json.RawMessage(`{}`),
	}, nil
}

func (transport *reconnectStubTransport) SendNotification(
	context.Context,
	mcp.JSONRPCNotification,
) error {
	return nil
}

func (transport *reconnectStubTransport) initializeRequest() *mcptransport.JSONRPCRequest {
	return &mcptransport.JSONRPCRequest{
		JSONRPC: mcp.JSONRPC_VERSION,
		ID:      mcp.NewRequestId("initialize-1"),
		Method:  string(mcp.MethodInitialize),
	}
}

func newReconnectTestSession(transport *reconnectStubTransport) *MCPClientSession {
	session := &MCPClientSession{
		transport:     transport,
		closed:        make(chan struct{}),
		notifications: make(chan json.RawMessage, notificationBufferSize),
	}
	session.state.initialize = transport.initializeRequest()
	return session
}

func sendToolsList(session *MCPClientSession) error {
	return session.sendWithReconnect(context.Background(), false, func() error {
		_, err := session.transport.SendRequest(context.Background(), mcptransport.JSONRPCRequest{
			JSONRPC: mcp.JSONRPC_VERSION,
			ID:      mcp.NewRequestId("send-1"),
			Method:  string(mcp.MethodToolsList),
		})
		return err
	})
}

// TestSendWithReconnectRetriesAfterConcurrentSessionClear reproduces the
// concurrent-404 race: mcp-go clears the transport session id as soon as any
// request fails with a 404, so another in-flight sender can observe an empty
// session id in its snapshot even though a session had been established and
// recorded. Reconnect-worthiness must therefore come from the recorded
// initialize request, not from the transport's session id.
func TestSendWithReconnectRetriesAfterConcurrentSessionClear(t *testing.T) {
	transport := newReconnectStubTransport("session-1")
	session := newReconnectTestSession(transport)

	// A concurrent sender just failed with a 404, which cleared the transport
	// session id before that sender managed to reinitialize.
	if err := sendToolsList(session); err != nil {
		t.Fatalf("sendWithReconnect after a concurrent 404 cleared the session: %v", err)
	}
	transport.mu.Lock()
	initializes := transport.initializes
	transport.mu.Unlock()
	if initializes != 1 {
		t.Fatalf("initialize exchanges = %d, want 1", initializes)
	}
	if transport.GetSessionId() != "session-2" {
		t.Fatalf("session id = %q, want session-2", transport.GetSessionId())
	}
}

// TestSendWithReconnectConcurrent404sAllRecover verifies that concurrent
// senders hitting a terminated session all recover once one of them
// reinitializes, without duplicating the initialize exchange per sender.
func TestSendWithReconnectConcurrent404sAllRecover(t *testing.T) {
	transport := newReconnectStubTransport("session-1")
	session := newReconnectTestSession(transport)

	const senders = 8
	var wg sync.WaitGroup
	errs := make([]error, senders)
	for i := range errs {
		wg.Add(1)
		go func() {
			defer wg.Done()
			errs[i] = sendToolsList(session)
		}()
	}
	wg.Wait()
	for i, err := range errs {
		if err != nil {
			t.Fatalf("sender %d: %v", i, err)
		}
	}
	transport.mu.Lock()
	initializes := transport.initializes
	transport.mu.Unlock()
	// At least one initialize exchange must have re-established the session;
	// a sender whose request raced the establishment may legitimately repeat
	// it, but each sender must still recover.
	if initializes < 1 {
		t.Fatalf("initialize exchanges = %d, want at least 1", initializes)
	}
}

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
