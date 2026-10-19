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
	"errors"
	"fmt"
	"net/http"
	"sync/atomic"
	"testing"
	"time"
)
import mcptransport "github.com/mark3labs/mcp-go/client/transport"

func TestSessionDeadlineWhileReconnectOwnsSendLock(t *testing.T) {
	var initCount atomic.Int32
	entered := make(chan struct{})
	release := make(chan struct{})
	client := NewClient(&http.Client{Transport: roundTripFunc(func(req *http.Request) (*http.Response, error) {
		if req.Method == http.MethodDelete {
			return mcpHTTPResponse(204, "", ""), nil
		}
		payload := readMCPPayload(t, req)
		switch payload.Method {
		case "initialize":
			n := initCount.Add(1)
			if n == 2 {
				close(entered)
				select {
				case <-release:
				case <-req.Context().Done():
					return nil, req.Context().Err()
				}
			}
			resp := mcpJSONResultResponse(payload.ID, map[string]any{"protocolVersion": "2025-11-25", "capabilities": map[string]any{}, "serverInfo": map[string]any{"name": "test", "version": "1"}})
			resp.Header.Set(mcptransport.HeaderKeySessionID, fmt.Sprintf("session-%d", n))
			return resp, nil
		case "notifications/initialized":
			return mcpHTTPResponse(202, "", ""), nil
		case "tools/list":
			if req.Header.Get(mcptransport.HeaderKeySessionID) == "session-1" {
				return mcpHTTPResponse(404, "text/plain", "expired"), nil
			}
			return mcpJSONResultResponse(payload.ID, map[string]any{"tools": []any{}}), nil
		}
		return nil, fmt.Errorf("unexpected method %s", payload.Method)
	})})
	session := newMCPTestSession(t, client, Target{Server: "http://localhost", InstanceID: "dev", Credential: Credential{AccessKey: "test", SecretKey: "test"}, Timeout: 5 * time.Second})
	defer session.Close()
	initializeClientSession(t, context.Background(), session)
	firstDone := make(chan error, 1)
	go func() {
		_, _, err := session.SendMessage(context.Background(), json.RawMessage(`{"jsonrpc":"2.0","id":"first","method":"tools/list"}`))
		firstDone <- err
	}()
	select {
	case <-entered:
	case <-time.After(time.Second):
		t.Fatal("reconnect did not start")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Millisecond)
	defer cancel()
	done := make(chan error, 1)
	go func() {
		_, _, err := session.SendMessage(ctx, json.RawMessage(`{"jsonrpc":"2.0","id":"second","method":"tools/list"}`))
		done <- err
	}()
	gotDone := false
	select {
	case err := <-done:
		gotDone = true
		if !errors.Is(err, context.DeadlineExceeded) {
			t.Errorf("unexpected cancellation error %v", err)
		}
	case <-time.After(time.Second):
		t.Error("cancelled request remained blocked by another request's reconnect")
	}
	close(release)
	select {
	case err := <-firstDone:
		if err != nil {
			t.Errorf("healthy reconnect failed: %v", err)
		}
	case <-time.After(time.Second):
		t.Error("healthy request stalled")
	}
	if !gotDone {
		select {
		case <-done:
		case <-time.After(time.Second):
			t.Error("cancelled call never returned")
		}
	}
}

func TestSessionDeadlineWhileAnotherCallOwnsReadLock(t *testing.T) {
	entered := make(chan struct{})
	release := make(chan struct{})
	client := NewClient(&http.Client{Transport: roundTripFunc(func(req *http.Request) (*http.Response, error) {
		if req.Method == http.MethodDelete {
			return mcpHTTPResponse(204, "", ""), nil
		}
		payload := readMCPPayload(t, req)
		switch payload.Method {
		case "initialize":
			resp := mcpJSONResultResponse(payload.ID, map[string]any{"protocolVersion": "2025-11-25", "capabilities": map[string]any{}, "serverInfo": map[string]any{"name": "test", "version": "1"}})
			resp.Header.Set(mcptransport.HeaderKeySessionID, "session-1")
			return resp, nil
		case "notifications/initialized":
			return mcpHTTPResponse(202, "", ""), nil
		case "tools/list":
			if string(payload.ID) == `"long"` {
				close(entered)
				select {
				case <-release:
				case <-req.Context().Done():
					return nil, req.Context().Err()
				}
				return mcpJSONResultResponse(payload.ID, map[string]any{"tools": []any{}}), nil
			}
			return mcpHTTPResponse(404, "text/plain", "expired"), nil
		}
		return nil, fmt.Errorf("unexpected method %s", payload.Method)
	})})
	session := newMCPTestSession(t, client, Target{Server: "http://localhost", InstanceID: "dev", Credential: Credential{AccessKey: "test", SecretKey: "test"}, Timeout: 5 * time.Second})
	defer session.Close()
	initializeClientSession(t, context.Background(), session)
	longDone := make(chan error, 1)
	go func() {
		_, _, err := session.SendMessage(context.Background(), json.RawMessage(`{"jsonrpc":"2.0","id":"long","method":"tools/list"}`))
		longDone <- err
	}()
	select {
	case <-entered:
	case <-time.After(time.Second):
		t.Fatal("long request did not start")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Millisecond)
	defer cancel()
	done := make(chan error, 1)
	go func() {
		_, _, err := session.SendMessage(ctx, json.RawMessage(`{"jsonrpc":"2.0","id":"short","method":"tools/list"}`))
		done <- err
	}()
	gotDone := false
	select {
	case err := <-done:
		gotDone = true
		if !errors.Is(err, context.DeadlineExceeded) {
			t.Errorf("unexpected cancellation error %v", err)
		}
	case <-time.After(time.Second):
		t.Error("reconnect lock acquisition ignored the caller deadline")
	}
	close(release)
	select {
	case err := <-longDone:
		if err != nil {
			t.Errorf("healthy long request failed: %v", err)
		}
	case <-time.After(time.Second):
		t.Error("long request stalled")
	}
	if !gotDone {
		select {
		case <-done:
		case <-time.After(time.Second):
			t.Error("cancelled call never returned")
		}
	}
}

func TestSessionConfiguredTimeoutAndCloseWhileQueued(t *testing.T) {
	for _, method := range []string{"request", "notification"} {
		for _, termination := range []string{"timeout", "close"} {
			t.Run(method+"/"+termination, func(t *testing.T) {
				transport := &reconnectFailureTransport{}
				session := &MCPClientSession{transport: transport, closed: make(chan struct{})}
				if termination == "timeout" {
					session.timeout = 30 * time.Millisecond
				}
				if err := session.sendGate.acquire(context.Background(), session.closed, true); err != nil {
					t.Fatal(err)
				}
				defer session.sendGate.release(true)
				defer session.Close()
				payload := json.RawMessage(`{"jsonrpc":"2.0","id":"queued","method":"tools/list"}`)
				if method == "notification" {
					payload = json.RawMessage(`{"jsonrpc":"2.0","method":"notifications/test"}`)
				}
				done := make(chan error, 1)
				go func() { _, _, err := session.SendMessage(context.Background(), payload); done <- err }()
				waitSendGateQueue(t, &session.sendGate, 1)
				want := error(context.DeadlineExceeded)
				if termination == "close" {
					want = errMCPClientSessionClosed
					if err := session.Close(); err != nil {
						t.Fatal(err)
					}
				}
				receiveSendGateResult(t, done, want)
				transport.mu.Lock()
				ordinary, notifications := transport.ordinaryCalls, transport.notificationCalls
				transport.mu.Unlock()
				if ordinary != 0 || notifications != 0 {
					t.Fatalf("queued message was sent: calls=%d notifications=%d", ordinary, notifications)
				}
			})
		}
	}
}
