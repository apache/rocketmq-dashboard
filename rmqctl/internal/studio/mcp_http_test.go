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
	"io"
	"net/http"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	mcptransport "github.com/mark3labs/mcp-go/client/transport"
	"github.com/mark3labs/mcp-go/mcp"
)

type roundTripFunc func(*http.Request) (*http.Response, error)

func (function roundTripFunc) RoundTrip(request *http.Request) (*http.Response, error) {
	return function(request)
}

func TestSessionStreamsPostSSEWithoutContinuousGet(t *testing.T) {
	var deleteCount atomic.Int32
	var getCount atomic.Int32
	client := NewClient(&http.Client{Transport: roundTripFunc(func(request *http.Request) (*http.Response, error) {
		if request.URL.String() != "http://localhost/api/mcp" {
			t.Errorf("URL = %s", request.URL)
		}
		if !strings.HasPrefix(request.Header.Get("Authorization"),
			"RMQ-HMAC-SHA256 Credential=test-ak, Signature=") {
			t.Errorf("Authorization = %q", request.Header.Get("Authorization"))
		}
		if request.Method == http.MethodGet {
			getCount.Add(1)
			return mcpHTTPResponse(http.StatusMethodNotAllowed, "text/plain", "GET not supported"), nil
		}
		if request.Method == http.MethodDelete {
			deleteCount.Add(1)
			assertMCPHeaders(t, request, "session-1", mcp.LATEST_PROTOCOL_VERSION)
			return mcpHTTPResponse(http.StatusNoContent, "", ""), nil
		}
		payload := readMCPPayload(t, request)
		switch payload.Method {
		case string(mcp.MethodInitialize):
			if request.Header.Get(mcptransport.HeaderKeySessionID) != "" ||
				request.Header.Get(mcptransport.HeaderKeyProtocolVersion) != "" {
				t.Errorf("initialize headers = %#v", request.Header)
			}
			response := mcpJSONResultResponse(payload.ID, map[string]any{
				"protocolVersion": mcp.LATEST_PROTOCOL_VERSION,
				"capabilities":    map[string]any{},
				"serverInfo":      map[string]any{"name": "studio", "version": "1"},
			})
			response.Header.Set(mcptransport.HeaderKeySessionID, "session-1")
			return response, nil
		case string(mcp.MethodNotificationInitialized):
			assertMCPHeaders(t, request, "session-1", mcp.LATEST_PROTOCOL_VERSION)
			return mcpHTTPResponse(http.StatusAccepted, "", ""), nil
		case string(mcp.MethodToolsList):
			assertMCPHeaders(t, request, "session-1", mcp.LATEST_PROTOCOL_VERSION)
			reader, writer := io.Pipe()
			go func() {
				_, _ = fmt.Fprint(writer, "event: message\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/tools/list_changed\"}\n\n")
				_, _ = fmt.Fprintf(writer, "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":%s,\"result\":{\"tools\":[]}}\n\n", payload.ID)
				<-request.Context().Done()
				_ = writer.Close()
			}()
			response := mcpHTTPResponse(http.StatusOK, "text/event-stream", "")
			response.Body = reader
			return response, nil
		default:
			return nil, fmt.Errorf("unexpected payload: %s", payload.Raw)
		}
	})})
	session := newMCPTestSession(t, client, Target{
		Server: "http://localhost", Cluster: "instance-dev",
		Credential: Credential{AccessKey: "test-ak", SecretKey: "test-sk"}, Timeout: time.Second,
	})
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	initializeClientSession(t, ctx, session)

	response, ok, err := session.SendMessage(ctx, json.RawMessage(`{"jsonrpc":"2.0","id":"list-1","method":"tools/list","params":{}}`))
	if err != nil || !ok {
		t.Fatalf("tools/list = %s, %t, %v", response, ok, err)
	}
	if !bytes.Contains(response, []byte(`"id":"list-1"`)) || !bytes.Contains(response, []byte(`"tools":[]`)) {
		t.Fatalf("response = %s", response)
	}
	select {
	case notification := <-session.Notifications():
		if !bytes.Contains(notification, []byte(`notifications/tools/list_changed`)) {
			t.Fatalf("notification = %s", notification)
		}
	case <-time.After(time.Second):
		t.Fatal("server notification was not forwarded")
	}
	if err := session.Close(); err != nil {
		t.Fatalf("Close() err = %v", err)
	}
	if deleteCount.Load() != 1 {
		t.Fatalf("DELETE count = %d, want 1", deleteCount.Load())
	}
	if getCount.Load() != 0 {
		t.Fatalf("GET count = %d, want 0", getCount.Load())
	}
}

func newMCPTestSession(t *testing.T, client Client, target Target) *MCPClientSession {
	t.Helper()
	session, err := client.NewMCPClientSession(target)
	if err != nil {
		t.Fatalf("NewMCPClientSession() err = %v", err)
	}
	return session
}

func initializeClientSession(t *testing.T, ctx context.Context, session *MCPClientSession) {
	t.Helper()
	initialize := fmt.Sprintf(
		`{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":%q,"capabilities":{},"clientInfo":{"name":"test","version":"1"}}}`,
		mcp.LATEST_PROTOCOL_VERSION,
	)
	if _, ok, err := session.SendMessage(ctx, json.RawMessage(initialize)); err != nil || !ok {
		t.Fatalf("initialize = ok:%t err:%v", ok, err)
	}
	if _, ok, err := session.SendMessage(ctx, json.RawMessage(`{"jsonrpc":"2.0","method":"notifications/initialized"}`)); err != nil || ok {
		t.Fatalf("initialized = ok:%t err:%v", ok, err)
	}
}

type observedMCPPayload struct {
	Raw    []byte
	ID     json.RawMessage `json:"id"`
	Method string          `json:"method"`
}

func readMCPPayload(t *testing.T, request *http.Request) observedMCPPayload {
	t.Helper()
	body, err := io.ReadAll(request.Body)
	if err != nil {
		t.Errorf("read request: %v", err)
	}
	payload := observedMCPPayload{Raw: body}
	if err := json.Unmarshal(body, &payload); err != nil {
		t.Errorf("decode request %q: %v", body, err)
	}
	return payload
}

func assertMCPHeaders(t *testing.T, request *http.Request, sessionID string, protocolVersion string) {
	t.Helper()
	if actual := request.Header.Get(mcptransport.HeaderKeySessionID); actual != sessionID {
		t.Errorf("session ID = %q, want %q", actual, sessionID)
	}
	if actual := request.Header.Get(mcptransport.HeaderKeyProtocolVersion); actual != protocolVersion {
		t.Errorf("protocol version = %q, want %q", actual, protocolVersion)
	}
}

func mcpJSONResultResponse(id json.RawMessage, result any) *http.Response {
	payload, _ := json.Marshal(struct {
		JSONRPC string          `json:"jsonrpc"`
		ID      json.RawMessage `json:"id"`
		Result  any             `json:"result"`
	}{JSONRPC: "2.0", ID: id, Result: result})
	return mcpHTTPResponse(http.StatusOK, "application/json", string(payload))
}

func mcpHTTPResponse(status int, contentType string, body string) *http.Response {
	response := &http.Response{
		StatusCode: status,
		Header:     make(http.Header),
		Body:       io.NopCloser(strings.NewReader(body)),
	}
	if contentType != "" {
		response.Header.Set("Content-Type", contentType)
	}
	return response
}
