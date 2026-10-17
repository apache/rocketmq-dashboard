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
	"strconv"
	"strings"
	"sync"
	"testing"

	mcptransport "github.com/mark3labs/mcp-go/client/transport"
	"github.com/mark3labs/mcp-go/mcp"
)

// 2^53+1 is the smallest positive integer that does not survive a float64
// round trip; 2^63-1 is the largest int64. Both are valid JSON-RPC ids that
// the SDK's float64 decoder corrupts (#6904).
const (
	largeRequestID = "9007199254740993"
	maxInt64ID     = "9223372036854775807"
	adjacentID     = "9007199254740992"
)

func decodeWire(t *testing.T, payload []byte) (string, mcp.RequestId) {
	t.Helper()
	decoded, err := decodeMCPMessage(payload)
	if err != nil {
		t.Fatalf("decode failed: %v", err)
	}
	if decoded.request == nil {
		t.Fatalf("expected a request frame")
	}
	wire, err := json.Marshal(decoded.request)
	if err != nil {
		t.Fatal(err)
	}
	return string(wire), decoded.request.ID
}

func decodeNotificationWire(t *testing.T, payload []byte) string {
	t.Helper()
	decoded, err := decodeMCPMessage(payload)
	if err != nil {
		t.Fatalf("decode failed: %v", err)
	}
	if decoded.notification == nil {
		t.Fatalf("expected a notification frame")
	}
	wire, err := json.Marshal(*decoded.notification)
	if err != nil {
		t.Fatal(err)
	}
	return string(wire)
}

// TestDecodeRequestPreservesLargeIntegerIDs covers the two corrupted-request
// defect scenarios: a 2^53+1 id and an int64-max id must reach the transport
// with the exact literal the MCP client sent.
func TestDecodeRequestPreservesLargeIntegerIDs(t *testing.T) {
	for _, id := range []string{largeRequestID, maxInt64ID} {
		wire, requestID := decodeWire(t, []byte(`{"jsonrpc":"2.0","id":`+id+`,"method":"tools/list"}`))
		value, ok := requestID.Value().(int64)
		if !ok || strconv.FormatInt(value, 10) != id {
			t.Fatalf("id %s decoded as %#v, want exact int64 %s", id, requestID.Value(), id)
		}
		if !strings.Contains(wire, `"id":`+id) {
			t.Fatalf("re-encoded request rounded the id, wire: %s", wire)
		}
	}
}

// TestDecodeRequestKeepsSmallAndStringIDs covers the passing controls: small
// numeric ids and string ids must decode exactly as the SDK did before.
func TestDecodeRequestKeepsSmallAndStringIDs(t *testing.T) {
	wire, requestID := decodeWire(t, []byte(`{"jsonrpc":"2.0","id":1,"method":"tools/list"}`))
	if value, ok := requestID.Value().(int64); !ok || value != 1 {
		t.Fatalf("small id decoded as %#v, want int64 1", requestID.Value())
	}
	if !strings.Contains(wire, `"id":1`) {
		t.Fatalf("small id changed shape, wire: %s", wire)
	}

	wire, requestID = decodeWire(t, []byte(`{"jsonrpc":"2.0","id":"abc-123","method":"tools/list"}`))
	if value, ok := requestID.Value().(string); !ok || value != "abc-123" {
		t.Fatalf("string id decoded as %#v, want string abc-123", requestID.Value())
	}
	if !strings.Contains(wire, `"id":"abc-123"`) {
		t.Fatalf("string id changed shape, wire: %s", wire)
	}
}

// TestSendRequestRejectsAdjacentResponseID covers the mismatched-response
// defect: a response carrying 9007199254740992 must never be correlated with
// a request sent as 9007199254740993, and the forwarded id must stay intact.
func TestSendRequestRejectsAdjacentResponseID(t *testing.T) {
	transport := &echoTransport{response: &mcptransport.JSONRPCResponse{
		JSONRPC: mcp.JSONRPC_VERSION,
		ID:      mcp.NewRequestId(int64(9007199254740992)),
		Result:  json.RawMessage(`{}`),
	}}
	session := &MCPClientSession{transport: transport}
	payload, ok, err := session.SendMessage(context.Background(), json.RawMessage(
		`{"jsonrpc":"2.0","id":9007199254740993,"method":"tools/list"}`))
	if ok || err == nil {
		t.Fatalf("adjacent-id response was accepted: payload=%s", payload)
	}
	if !strings.Contains(err.Error(), "does not match") {
		t.Fatalf("expected an id-mismatch error, got: %v", err)
	}
	if sent := transport.sentID(); sent != "int64:"+largeRequestID {
		t.Fatalf("forwarded id = %s, want int64:%s", sent, largeRequestID)
	}
}

// TestSendRequestAcceptsMatchingLargeResponseID verifies that a response whose
// id decodes to the same identity as the forwarded request is still correlated
// normally: the identity fix must not break response matching itself.
func TestSendRequestAcceptsMatchingLargeResponseID(t *testing.T) {
	transport := &echoTransport{response: &mcptransport.JSONRPCResponse{
		JSONRPC: mcp.JSONRPC_VERSION,
		ID:      mcp.NewRequestId(int64(9007199254740993)),
		Result:  json.RawMessage(`{}`),
	}}
	session := &MCPClientSession{transport: transport}
	_, ok, err := session.SendMessage(context.Background(), json.RawMessage(
		`{"jsonrpc":"2.0","id":9007199254740993,"method":"tools/list"}`))
	if err != nil || !ok {
		t.Fatalf("matching large response id was rejected: ok=%t err=%v", ok, err)
	}
}

// TestSendRequestKeepsNumericStringTypeDistinctions covers the two
// type-mismatch controls: numeric 5 and string "5" are different JSON-RPC
// identities and a response of one shape must not be correlated with the other.
func TestSendRequestKeepsNumericStringTypeDistinctions(t *testing.T) {
	scenarios := []struct {
		name       string
		request    string
		responseID mcp.RequestId
	}{
		{"string request numeric response", `"id":"5"`, mcp.NewRequestId(int64(5))},
		{"numeric request string response", `"id":5`, mcp.NewRequestId("5")},
	}
	for _, scenario := range scenarios {
		t.Run(scenario.name, func(t *testing.T) {
			transport := &echoTransport{response: &mcptransport.JSONRPCResponse{
				JSONRPC: mcp.JSONRPC_VERSION,
				ID:      scenario.responseID,
				Result:  json.RawMessage(`{}`),
			}}
			session := &MCPClientSession{transport: transport}
			payload := `{"jsonrpc":"2.0",` + scenario.request + `,"method":"tools/list"}`
			if _, ok, err := session.SendMessage(context.Background(), json.RawMessage(payload)); ok || err == nil {
				t.Fatal("a numeric/string id mismatch was accepted")
			}
		})
	}
}

// TestDecodeCancellationPreservesLargeRequestID covers the cancellation
// defect: the requestId inside notifications/cancelled must be forwarded with
// its literal value, alongside the unrelated reason field.
func TestDecodeCancellationPreservesLargeRequestID(t *testing.T) {
	wire := decodeNotificationWire(t, []byte(
		`{"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":9007199254740993,"reason":"user"}}`))
	if !strings.Contains(wire, `"requestId":9007199254740993`) {
		t.Fatalf("cancellation requestId was rounded, wire: %s", wire)
	}
	if !strings.Contains(wire, `"reason":"user"`) {
		t.Fatalf("unrelated params were dropped, wire: %s", wire)
	}
}

// TestDecodeCancellationKeepsSmallAndStringRequestIDs covers the cancellation
// controls: small numeric and string requestIds keep the SDK's decoded shape.
func TestDecodeCancellationKeepsSmallAndStringRequestIDs(t *testing.T) {
	wire := decodeNotificationWire(t, []byte(
		`{"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":7}}`))
	if !strings.Contains(wire, `"requestId":7`) {
		t.Fatalf("small cancellation requestId changed, wire: %s", wire)
	}

	wire = decodeNotificationWire(t, []byte(
		`{"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":"req-42"}}`))
	if !strings.Contains(wire, `"requestId":"req-42"`) {
		t.Fatalf("string cancellation requestId changed, wire: %s", wire)
	}
}

// TestDecodeNotificationWithoutRequestIDInjectsNothing verifies that the
// cancellation restore never injects a requestId into frames that had none.
func TestDecodeNotificationWithoutRequestIDInjectsNothing(t *testing.T) {
	wire := decodeNotificationWire(t, []byte(`{"jsonrpc":"2.0","method":"notifications/initialized"}`))
	if strings.Contains(wire, "requestId") {
		t.Fatalf("requestId was injected into a frame without one, wire: %s", wire)
	}
	if strings.Contains(wire, "params") {
		t.Fatalf("an empty params object appeared, wire: %s", wire)
	}
}

// TestDecodeRequestRejectsInvalidIDShape verifies that non string/number/null
// ids keep the SDK's rejection instead of being silently accepted.
func TestDecodeRequestRejectsInvalidIDShape(t *testing.T) {
	if _, err := decodeMCPMessage([]byte(`{"jsonrpc":"2.0","id":true,"method":"tools/list"}`)); err == nil {
		t.Fatal("a boolean id was accepted")
	}
}

// echoTransport is a minimal scripted transport: it records the forwarded
// request id and replies with a fixed response.
type echoTransport struct {
	mu       sync.Mutex
	response *mcptransport.JSONRPCResponse
	forward  mcp.RequestId
}

func (transport *echoTransport) Start(context.Context) error                          { return nil }
func (transport *echoTransport) Close() error                                         { return nil }
func (transport *echoTransport) SetNotificationHandler(func(mcp.JSONRPCNotification)) {}
func (transport *echoTransport) SetProtocolVersion(string)                            {}
func (transport *echoTransport) GetSessionId() string                                 { return "" }

func (transport *echoTransport) SendRequest(_ context.Context, request mcptransport.JSONRPCRequest) (*mcptransport.JSONRPCResponse, error) {
	transport.mu.Lock()
	defer transport.mu.Unlock()
	transport.forward = request.ID
	return transport.response, nil
}

func (transport *echoTransport) SendNotification(_ context.Context, _ mcp.JSONRPCNotification) error {
	return nil
}

func (transport *echoTransport) sentID() string {
	transport.mu.Lock()
	defer transport.mu.Unlock()
	return transport.forward.String()
}
