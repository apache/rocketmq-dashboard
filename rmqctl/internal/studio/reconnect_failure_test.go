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
	"fmt"
	"net/http"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	mcptransport "github.com/mark3labs/mcp-go/client/transport"
	"github.com/mark3labs/mcp-go/mcp"
)

func TestSessionRetriesIncompleteReconnectBeforeNextRequest(t *testing.T) {
	initializes, initialized, unreadyCalls := 0, 0, 0
	ready := false
	client := NewClient(&http.Client{Transport: roundTripFunc(func(request *http.Request) (*http.Response, error) {
		if request.Method == http.MethodDelete {
			return mcpHTTPResponse(http.StatusNoContent, "", ""), nil
		}
		payload := readMCPPayload(t, request)
		switch payload.Method {
		case string(mcp.MethodInitialize):
			initializes++
			ready = false
			response := mcpJSONResultResponse(payload.ID, map[string]any{
				"protocolVersion": mcp.LATEST_PROTOCOL_VERSION,
				"capabilities":    map[string]any{},
				"serverInfo":      map[string]any{"name": "studio", "version": "1"},
			})
			response.Header.Set(mcptransport.HeaderKeySessionID, fmt.Sprintf("session-%d", initializes))
			return response, nil
		case string(mcp.MethodNotificationInitialized):
			initialized++
			if initialized == 2 {
				return mcpHTTPResponse(http.StatusBadGateway, "application/json", `{"code":"UNAVAILABLE","message":"Transient upstream failure"}`), nil
			}
			ready = true
			return mcpHTTPResponse(http.StatusAccepted, "", ""), nil
		case string(mcp.MethodToolsList):
			if request.Header.Get(mcptransport.HeaderKeySessionID) == "session-1" {
				return mcpHTTPResponse(http.StatusNotFound, "text/plain", "Session expired"), nil
			}
			if !ready {
				unreadyCalls++
				return mcpHTTPResponse(http.StatusOK, "application/json", fmt.Sprintf(`{"jsonrpc":"2.0","id":%s,"error":{"code":-32600,"message":"Session not initialized"}}`, payload.ID)), nil
			}
			return mcpJSONResultResponse(payload.ID, map[string]any{"tools": []any{}}), nil
		default:
			return nil, fmt.Errorf("unexpected method: %s", payload.Method)
		}
	})})
	session := newMCPTestSession(t, client, Target{
		Server: "http://localhost", InstanceID: "instance-dev",
		Credential: Credential{AccessKey: "test-ak", SecretKey: "test-sk"}, Timeout: time.Second,
	})
	defer session.Close()
	ctx := context.Background()
	initializeClientSession(t, ctx, session)
	request := json.RawMessage(`{"jsonrpc":"2.0","id":"list-1","method":"tools/list","params":{}}`)
	if _, _, err := session.SendMessage(ctx, request); err == nil {
		t.Fatal("expected the first call to report the failed reconnect notification")
	}
	response, ok, err := session.SendMessage(ctx, request)
	t.Logf("retry response=%s ok=%t err=%v initialize=%d initialized=%d unready-tool-calls=%d", response, ok, err, initializes, initialized, unreadyCalls)
	if err != nil || !ok || !bytes.Contains(response, []byte(`"tools":[]`)) || unreadyCalls != 0 {
		t.Fatalf("retry did not repair the incomplete handshake before sending tools/list")
	}
}

func TestSessionQueuedCallerDoesNotUseIncompleteReconnect(t *testing.T) {
	var lock sync.Mutex
	initializes, initialized, unreadyCalls := 0, 0, 0
	ready := false
	var expiredRequests atomic.Int32
	bothExpired := make(chan struct{})
	client := NewClient(&http.Client{Transport: roundTripFunc(func(request *http.Request) (*http.Response, error) {
		if request.Method == http.MethodDelete {
			return mcpHTTPResponse(http.StatusNoContent, "", ""), nil
		}
		payload := readMCPPayload(t, request)
		if payload.Method == string(mcp.MethodToolsList) && request.Header.Get(mcptransport.HeaderKeySessionID) == "session-1" {
			if expiredRequests.Add(1) == 2 {
				close(bothExpired)
			}
			select {
			case <-bothExpired:
			case <-request.Context().Done():
				return nil, request.Context().Err()
			}
			return mcpHTTPResponse(http.StatusNotFound, "text/plain", "Session expired"), nil
		}
		lock.Lock()
		defer lock.Unlock()
		switch payload.Method {
		case string(mcp.MethodInitialize):
			initializes++
			ready = false
			response := mcpJSONResultResponse(payload.ID, map[string]any{
				"protocolVersion": mcp.LATEST_PROTOCOL_VERSION,
				"capabilities":    map[string]any{},
				"serverInfo":      map[string]any{"name": "studio", "version": "1"},
			})
			response.Header.Set(mcptransport.HeaderKeySessionID, fmt.Sprintf("session-%d", initializes))
			return response, nil
		case string(mcp.MethodNotificationInitialized):
			initialized++
			if initialized == 2 {
				return mcpHTTPResponse(http.StatusBadGateway, "application/json", `{"code":"UNAVAILABLE"}`), nil
			}
			ready = true
			return mcpHTTPResponse(http.StatusAccepted, "", ""), nil
		case string(mcp.MethodToolsList):
			if !ready {
				unreadyCalls++
				return mcpHTTPResponse(http.StatusOK, "application/json", fmt.Sprintf(`{"jsonrpc":"2.0","id":%s,"error":{"code":-32600,"message":"Session not initialized"}}`, payload.ID)), nil
			}
			return mcpJSONResultResponse(payload.ID, map[string]any{"tools": []any{}}), nil
		default:
			return nil, fmt.Errorf("unexpected method: %s", payload.Method)
		}
	})})
	session := newMCPTestSession(t, client, Target{
		Server: "http://localhost", InstanceID: "instance-dev",
		Credential: Credential{AccessKey: "test-ak", SecretKey: "test-sk"}, Timeout: 3 * time.Second,
	})
	defer session.Close()
	initializeClientSession(t, context.Background(), session)
	type result struct {
		payload json.RawMessage
		err     error
	}
	results := make(chan result, 2)
	for id := 0; id < 2; id++ {
		go func() {
			payload, _, err := session.SendMessage(context.Background(), json.RawMessage(fmt.Sprintf(`{"jsonrpc":"2.0","id":%d,"method":"tools/list","params":{}}`, id+2)))
			results <- result{payload, err}
		}()
	}
	successes, failures := 0, 0
	for i := 0; i < 2; i++ {
		result := <-results
		if result.err != nil {
			failures++
		} else if bytes.Contains(result.payload, []byte(`"tools":[]`)) {
			successes++
		}
		t.Logf("caller result=%s err=%v", result.payload, result.err)
	}
	lock.Lock()
	defer lock.Unlock()
	t.Logf("initialize=%d initialized=%d unready-tool-calls=%d", initializes, initialized, unreadyCalls)
	if successes != 1 || failures != 1 || unreadyCalls != 0 {
		t.Fatalf("queued caller used the incomplete reconnect: successes=%d failures=%d unready=%d", successes, failures, unreadyCalls)
	}
}

// reconnectFailureTransport isolates failure phases without retrying an ordinary
// request that may already have been accepted. The HTTP tests above exercise the
// same lifecycle through mcp-go's real Streamable HTTP transport.
type reconnectFailureTransport struct {
	mu                sync.Mutex
	sessionID         string
	ready             bool
	initializeFailure string
	failInitializes   int
	failNotifications int
	initializeCalls   int
	notificationCalls int
	ordinaryCalls     int
	unreadyCalls      int
	ordinaryError     error
	ordinaryRPCError  bool
	alwaysExpired     bool
}

func (transport *reconnectFailureTransport) Start(context.Context) error                          { return nil }
func (transport *reconnectFailureTransport) Close() error                                         { return nil }
func (transport *reconnectFailureTransport) SetNotificationHandler(func(mcp.JSONRPCNotification)) {}
func (transport *reconnectFailureTransport) SetProtocolVersion(string)                            {}
func (transport *reconnectFailureTransport) GetSessionId() string {
	transport.mu.Lock()
	defer transport.mu.Unlock()
	return transport.sessionID
}
func (transport *reconnectFailureTransport) SendRequest(_ context.Context, request mcptransport.JSONRPCRequest) (*mcptransport.JSONRPCResponse, error) {
	transport.mu.Lock()
	defer transport.mu.Unlock()
	response := &mcptransport.JSONRPCResponse{JSONRPC: mcp.JSONRPC_VERSION, ID: request.ID, Result: json.RawMessage(`{"tools":[]}`)}
	if request.Method == string(mcp.MethodInitialize) {
		transport.initializeCalls++
		transport.ready = false
		if transport.failInitializes > 0 {
			transport.failInitializes--
			switch transport.initializeFailure {
			case "transport":
				return nil, &MCPHTTPStatusError{StatusCode: http.StatusBadGateway}
			case "empty":
				return nil, nil
			case "rpc":
				response.Result = nil
				response.Error = &mcp.JSONRPCErrorDetails{Code: -32603, Message: "initialize failed"}
				return response, nil
			case "malformed":
				response.Result = json.RawMessage(`{"protocolVersion":123}`)
				return response, nil
			}
		}
		transport.sessionID = "replacement"
		response.Result = json.RawMessage(`{"protocolVersion":"` + mcp.LATEST_PROTOCOL_VERSION + `"}`)
		return response, nil
	}
	transport.ordinaryCalls++
	if transport.sessionID == "expired" || transport.alwaysExpired {
		transport.sessionID = ""
		return nil, mcptransport.ErrSessionTerminated
	}
	if !transport.ready {
		transport.unreadyCalls++
	}
	if transport.ordinaryError != nil {
		return nil, transport.ordinaryError
	}
	if transport.ordinaryRPCError {
		response.Result = nil
		response.Error = &mcp.JSONRPCErrorDetails{Code: -32603, Message: "tool failed"}
	}
	return response, nil
}
func (transport *reconnectFailureTransport) SendNotification(_ context.Context, _ mcp.JSONRPCNotification) error {
	transport.mu.Lock()
	defer transport.mu.Unlock()
	transport.notificationCalls++
	if transport.failNotifications > 0 {
		transport.failNotifications--
		return &MCPHTTPStatusError{StatusCode: http.StatusBadGateway}
	}
	transport.ready = true
	return nil
}
func failureTestSession(transport *reconnectFailureTransport) *MCPClientSession {
	session := &MCPClientSession{transport: transport}
	session.state.initialize = &mcptransport.JSONRPCRequest{JSONRPC: mcp.JSONRPC_VERSION, ID: mcp.NewRequestId(1), Method: string(mcp.MethodInitialize)}
	session.state.generation = 1
	session.state.ready = true
	return session
}
func sendFailureTestRequest(session *MCPClientSession) error {
	_, _, err := session.SendMessage(context.Background(), json.RawMessage(`{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}`))
	return err
}

func TestSessionKeepsFailedInitializeRecoverable(t *testing.T) {
	for _, failure := range []string{"transport", "empty", "rpc", "malformed"} {
		t.Run(failure, func(t *testing.T) {
			transport := &reconnectFailureTransport{sessionID: "expired", ready: true, initializeFailure: failure, failInitializes: 1}
			session := failureTestSession(transport)
			if err := sendFailureTestRequest(session); err == nil {
				t.Fatal("expected failed initialize")
			}
			if !session.state.reconnectPending || session.state.generation != 1 {
				t.Fatalf("failed handshake published state: pending=%t generation=%d", session.state.reconnectPending, session.state.generation)
			}
			if err := sendFailureTestRequest(session); err != nil {
				t.Fatalf("later recovery failed: %v", err)
			}
			if session.state.reconnectPending || session.state.generation != 2 || transport.unreadyCalls != 0 {
				t.Fatalf("recovery state: pending=%t generation=%d unready=%d", session.state.reconnectPending, session.state.generation, transport.unreadyCalls)
			}
		})
	}
}

func TestSessionRepeatedHandshakeFailureHasBoundedRetries(t *testing.T) {
	transport := &reconnectFailureTransport{sessionID: "expired", ready: true, failNotifications: 2}
	session := failureTestSession(transport)
	for attempt := 1; attempt <= 2; attempt++ {
		if err := sendFailureTestRequest(session); err == nil {
			t.Fatal("expected failed initialized notification")
		}
		if transport.initializeCalls != attempt || transport.ordinaryCalls != 1 || transport.unreadyCalls != 0 {
			t.Fatalf("failure retry exceeded budget: initialize=%d ordinary=%d unready=%d", transport.initializeCalls, transport.ordinaryCalls, transport.unreadyCalls)
		}
	}
	if err := sendFailureTestRequest(session); err != nil {
		t.Fatalf("later recovery: %v", err)
	}
	if transport.initializeCalls != 3 || transport.ordinaryCalls != 2 {
		t.Fatalf("unexpected recovery counts: initialize=%d ordinary=%d", transport.initializeCalls, transport.ordinaryCalls)
	}
}

func TestSessionConcurrentCallersSharePendingRecovery(t *testing.T) {
	transport := &reconnectFailureTransport{sessionID: "expired", ready: true, failNotifications: 1}
	session := failureTestSession(transport)
	if err := sendFailureTestRequest(session); err == nil {
		t.Fatal("expected initial reconnect failure")
	}
	const callers = 12
	var wait sync.WaitGroup
	errors := make(chan error, callers)
	for i := 0; i < callers; i++ {
		wait.Go(func() { errors <- sendFailureTestRequest(session) })
	}
	wait.Wait()
	close(errors)
	for err := range errors {
		if err != nil {
			t.Errorf("concurrent caller: %v", err)
		}
	}
	if transport.initializeCalls != 2 || transport.notificationCalls != 2 || transport.unreadyCalls != 0 {
		t.Fatalf("pending recovery was not shared: initialize=%d initialized=%d unready=%d", transport.initializeCalls, transport.notificationCalls, transport.unreadyCalls)
	}
}

func TestSessionDoesNotReplayAmbiguousOrdinaryFailures(t *testing.T) {
	for _, failure := range []string{"timeout", "http", "rpc"} {
		t.Run(failure, func(t *testing.T) {
			transport := &reconnectFailureTransport{sessionID: "ready", ready: true}
			switch failure {
			case "timeout":
				transport.ordinaryError = context.DeadlineExceeded
			case "http":
				transport.ordinaryError = &MCPHTTPStatusError{StatusCode: http.StatusBadGateway}
			case "rpc":
				transport.ordinaryRPCError = true
			}
			session := failureTestSession(transport)
			_, _, _ = session.SendMessage(context.Background(), json.RawMessage(`{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"example","arguments":{}}}`))
			if transport.ordinaryCalls != 1 || transport.initializeCalls != 0 {
				t.Fatalf("ambiguous tool failure was replayed: ordinary=%d initialize=%d", transport.ordinaryCalls, transport.initializeCalls)
			}
		})
	}
}

func TestSessionDoesNotLoopOnRepeatedSessionTermination(t *testing.T) {
	transport := &reconnectFailureTransport{sessionID: "expired", ready: true, alwaysExpired: true}
	session := failureTestSession(transport)
	if err := sendFailureTestRequest(session); err == nil {
		t.Fatal("expected repeated session termination")
	}
	if transport.ordinaryCalls != 2 || transport.initializeCalls != 1 {
		t.Fatalf("recovery exceeded one attempt: ordinary=%d initialize=%d", transport.ordinaryCalls, transport.initializeCalls)
	}
}

func TestSessionClientInitializeDoesNotClearPendingRecovery(t *testing.T) {
	transport := &reconnectFailureTransport{sessionID: "replacement"}
	session := failureTestSession(transport)
	session.state.reconnectPending = true
	request := *session.state.initialize
	response := &mcptransport.JSONRPCResponse{Result: json.RawMessage(`{"protocolVersion":"` + mcp.LATEST_PROTOCOL_VERSION + `"}`)}
	if err := session.recordInitialization(request, response); err != nil {
		t.Fatal(err)
	}
	if !session.state.reconnectPending {
		t.Fatal("client initialize cleared pending recovery before initialized succeeded")
	}
}
