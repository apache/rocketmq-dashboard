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
	"sync"
	"testing"
	"time"

	"github.com/mark3labs/mcp-go/mcp"
)

// The SDK decodes JSON-RPC request ids and notification params.requestId
// values through float64, which silently rounds integers beyond 2^53. The
// bridge must preserve the original textual identity on the wire, in the
// returned response payload, and when forwarding cancellations, and it must
// reject a response whose raw id token differs from the request's.
// See issue #6904.

type mcpIDPeer struct {
	mu     sync.Mutex
	bodies map[string][]byte
}

func newMCPIDPeer() *mcpIDPeer {
	return &mcpIDPeer{bodies: map[string][]byte{}}
}

func (peer *mcpIDPeer) record(payload observedMCPPayload) {
	peer.mu.Lock()
	defer peer.mu.Unlock()
	peer.bodies[payload.Method] = append([]byte(nil), payload.Raw...)
}

func (peer *mcpIDPeer) body(method string) []byte {
	peer.mu.Lock()
	defer peer.mu.Unlock()
	return peer.bodies[method]
}

func mcpIDPeerTransport(t *testing.T, peer *mcpIDPeer, respond func(observedMCPPayload) *http.Response) roundTripFunc {
	return func(request *http.Request) (*http.Response, error) {
		switch request.Method {
		case http.MethodDelete:
			return mcpHTTPResponse(http.StatusNoContent, "", ""), nil
		case http.MethodPost:
			payload := readMCPPayload(t, request)
			peer.record(payload)
			switch payload.Method {
			case string(mcp.MethodInitialize):
				response := mcpJSONResultResponse(payload.ID, map[string]any{
					"protocolVersion": mcp.LATEST_PROTOCOL_VERSION,
					"capabilities":    map[string]any{},
					"serverInfo":      map[string]any{"name": "studio", "version": "1"},
				})
				return response, nil
			case string(mcp.MethodNotificationInitialized):
				return mcpHTTPResponse(http.StatusAccepted, "", ""), nil
			default:
				return respond(payload), nil
			}
		default:
			return mcpHTTPResponse(http.StatusMethodNotAllowed, "text/plain", "method not allowed"), nil
		}
	}
}

func newMCPIDTestSession(t *testing.T, transport roundTripFunc) (*MCPClientSession, context.Context) {
	t.Helper()
	client := NewClient(&http.Client{Transport: transport})
	session := newMCPTestSession(t, client, Target{
		Server: "http://localhost", InstanceID: "instance-dev",
		Credential: Credential{AccessKey: "test-ak", SecretKey: "test-sk"}, Timeout: 2 * time.Second,
	})
	ctx, cancel := context.WithCancel(context.Background())
	t.Cleanup(cancel)
	initializeClientSession(t, ctx, session)
	return session, ctx
}

func toolsListResult(id json.RawMessage) *http.Response {
	return mcpJSONResultResponse(id, map[string]any{"tools": []any{}})
}

// sendToolsList runs one tools/list exchange with the given raw id token and
// returns the response payload sent to the stdio client.
func sendToolsList(t *testing.T, session *MCPClientSession, ctx context.Context, idToken string) json.RawMessage {
	t.Helper()
	response, ok, err := session.SendMessage(ctx, json.RawMessage(
		`{"jsonrpc":"2.0","id":`+idToken+`,"method":"tools/list","params":{}}`))
	if err != nil || !ok {
		t.Fatalf("tools/list id=%s = ok:%t err:%v", idToken, ok, err)
	}
	return response
}

func assertWireAndReturnedID(t *testing.T, peer *mcpIDPeer, response json.RawMessage, want string) {
	t.Helper()
	sent := peer.body(string(mcp.MethodToolsList))
	if !bytes.Contains(sent, []byte(`"id":`+want)) {
		t.Errorf("wire request id was changed, body = %s", sent)
	}
	if !bytes.Contains(response, []byte(`"id":`+want)) {
		t.Errorf("returned response id was changed, payload = %s", response)
	}
}

// Defect scenario 1: a numeric id beyond 2^53 echoed by the peer must be sent
// and returned exactly.
func TestLargeNumericRequestIDIsPreserved(t *testing.T) {
	peer := newMCPIDPeer()
	session, ctx := newMCPIDTestSession(t, mcpIDPeerTransport(t, peer, func(payload observedMCPPayload) *http.Response {
		return toolsListResult(payload.ID)
	}))
	response := sendToolsList(t, session, ctx, `9007199254740993`)
	assertWireAndReturnedID(t, peer, response, `9007199254740993`)
}

// Defect scenario 2: the same for the int64 maximum.
func TestInt64MaxRequestIDIsPreserved(t *testing.T) {
	peer := newMCPIDPeer()
	session, ctx := newMCPIDTestSession(t, mcpIDPeerTransport(t, peer, func(payload observedMCPPayload) *http.Response {
		return toolsListResult(payload.ID)
	}))
	response := sendToolsList(t, session, ctx, `9223372036854775807`)
	assertWireAndReturnedID(t, peer, response, `9223372036854775807`)
}

// Defect scenario 3: a response whose id is the rounded neighbour of the
// request id must be rejected instead of silently accepted.
func TestAdjacentResponseIDIsRejected(t *testing.T) {
	peer := newMCPIDPeer()
	session, ctx := newMCPIDTestSession(t, mcpIDPeerTransport(t, peer, func(payload observedMCPPayload) *http.Response {
		return toolsListResult(json.RawMessage(`9007199254740992`))
	}))
	_, ok, err := session.SendMessage(ctx, json.RawMessage(
		`{"jsonrpc":"2.0","id":9007199254740993,"method":"tools/list","params":{}}`))
	if err == nil || ok {
		t.Fatalf("adjacent response id was accepted: ok:%t err:%v", ok, err)
	}
}

// Defect scenario 4: a large numeric params.requestId in a cancellation must
// be forwarded exactly.
func TestLargeCancellationRequestIDIsPreserved(t *testing.T) {
	peer := newMCPIDPeer()
	session, ctx := newMCPIDTestSession(t, mcpIDPeerTransport(t, peer, func(payload observedMCPPayload) *http.Response {
		return mcpHTTPResponse(http.StatusAccepted, "", "")
	}))
	if _, ok, err := session.SendMessage(ctx, json.RawMessage(
		`{"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":9007199254740993}}`)); err != nil || ok {
		t.Fatalf("notifications/cancelled = ok:%t err:%v", ok, err)
	}
	sent := peer.body(string(mcp.MethodNotificationCancelled))
	if !bytes.Contains(sent, []byte(`"requestId":9007199254740993`)) {
		t.Errorf("cancellation requestId was changed, body = %s", sent)
	}
}

// Defect scenario 3 over the POST SSE response channel: the mismatched frame
// must be withheld even when it arrives as a stream rather than a JSON body.
func TestAdjacentResponseIDIsRejectedOverSSE(t *testing.T) {
	peer := newMCPIDPeer()
	session, ctx := newMCPIDTestSession(t, mcpIDPeerTransport(t, peer, func(payload observedMCPPayload) *http.Response {
		return mcpHTTPResponse(http.StatusOK, "text/event-stream",
			"event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":9007199254740992,\"result\":{\"tools\":[]}}\n\n")
	}))
	_, ok, err := session.SendMessage(ctx, json.RawMessage(
		`{"jsonrpc":"2.0","id":9007199254740993,"method":"tools/list","params":{}}`))
	if err == nil || ok {
		t.Fatalf("adjacent SSE response id was accepted: ok:%t err:%v", ok, err)
	}
}

// Control: small numeric ids keep their exact (unchanged) representation.
func TestSmallNumericRequestIDIsPreserved(t *testing.T) {
	peer := newMCPIDPeer()
	session, ctx := newMCPIDTestSession(t, mcpIDPeerTransport(t, peer, func(payload observedMCPPayload) *http.Response {
		return toolsListResult(payload.ID)
	}))
	response := sendToolsList(t, session, ctx, `7`)
	assertWireAndReturnedID(t, peer, response, `7`)
}

// Control: string ids are forwarded and returned exactly.
func TestStringRequestIDIsPreserved(t *testing.T) {
	peer := newMCPIDPeer()
	session, ctx := newMCPIDTestSession(t, mcpIDPeerTransport(t, peer, func(payload observedMCPPayload) *http.Response {
		return toolsListResult(payload.ID)
	}))
	response, ok, err := session.SendMessage(ctx, json.RawMessage(
		`{"jsonrpc":"2.0","id":"list-7","method":"tools/list","params":{}}`))
	if err != nil || !ok {
		t.Fatalf("tools/list = ok:%t err:%v", ok, err)
	}
	sent := peer.body(string(mcp.MethodToolsList))
	if !bytes.Contains(sent, []byte(`"id":"list-7"`)) {
		t.Errorf("wire request id was changed, body = %s", sent)
	}
	if !bytes.Contains(response, []byte(`"id":"list-7"`)) {
		t.Errorf("returned response id was changed, payload = %s", response)
	}
}

// Control: a string response id for a numeric request id stays rejected.
func TestStringResponseIDForNumericRequestIsRejected(t *testing.T) {
	peer := newMCPIDPeer()
	session, ctx := newMCPIDTestSession(t, mcpIDPeerTransport(t, peer, func(payload observedMCPPayload) *http.Response {
		return toolsListResult(json.RawMessage(`"7"`))
	}))
	_, ok, err := session.SendMessage(ctx, json.RawMessage(
		`{"jsonrpc":"2.0","id":7,"method":"tools/list","params":{}}`))
	if err == nil || ok {
		t.Fatalf("type-mismatched response id was accepted: ok:%t err:%v", ok, err)
	}
}

// Control: a numeric response id for a string request id stays rejected.
func TestNumericResponseIDForStringRequestIsRejected(t *testing.T) {
	peer := newMCPIDPeer()
	session, ctx := newMCPIDTestSession(t, mcpIDPeerTransport(t, peer, func(payload observedMCPPayload) *http.Response {
		return toolsListResult(json.RawMessage(`7`))
	}))
	_, ok, err := session.SendMessage(ctx, json.RawMessage(
		`{"jsonrpc":"2.0","id":"7","method":"tools/list","params":{}}`))
	if err == nil || ok {
		t.Fatalf("type-mismatched response id was accepted: ok:%t err:%v", ok, err)
	}
}

// Control: small numeric cancellation request ids are forwarded unchanged.
func TestSmallCancellationRequestIDIsPreserved(t *testing.T) {
	peer := newMCPIDPeer()
	session, ctx := newMCPIDTestSession(t, mcpIDPeerTransport(t, peer, func(payload observedMCPPayload) *http.Response {
		return mcpHTTPResponse(http.StatusAccepted, "", "")
	}))
	if _, ok, err := session.SendMessage(ctx, json.RawMessage(
		`{"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":7}}`)); err != nil || ok {
		t.Fatalf("notifications/cancelled = ok:%t err:%v", ok, err)
	}
	sent := peer.body(string(mcp.MethodNotificationCancelled))
	if !bytes.Contains(sent, []byte(`"requestId":7`)) {
		t.Errorf("cancellation requestId was changed, body = %s", sent)
	}
}

// Control: string cancellation request ids are forwarded unchanged.
func TestStringCancellationRequestIDIsPreserved(t *testing.T) {
	peer := newMCPIDPeer()
	session, ctx := newMCPIDTestSession(t, mcpIDPeerTransport(t, peer, func(payload observedMCPPayload) *http.Response {
		return mcpHTTPResponse(http.StatusAccepted, "", "")
	}))
	if _, ok, err := session.SendMessage(ctx, json.RawMessage(
		`{"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":"c-1"}}`)); err != nil || ok {
		t.Fatalf("notifications/cancelled = ok:%t err:%v", ok, err)
	}
	sent := peer.body(string(mcp.MethodNotificationCancelled))
	if !bytes.Contains(sent, []byte(`"requestId":"c-1"`)) {
		t.Errorf("cancellation requestId was changed, body = %s", sent)
	}
}
