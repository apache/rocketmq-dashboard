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
	"fmt"
	"net/http"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	mcptransport "github.com/mark3labs/mcp-go/client/transport"
	"github.com/mark3labs/mcp-go/mcp"
)

func TestReconnectInitializedFailure(t *testing.T) {
	for _, tc := range []struct {
		name                string
		retryStatus         int
		callers             int
		wantInitializations int32
		wantNotices         int32
		staleGeneration     bool
	}{
		{"retry succeeds", 202, 1, 2, 3, false},
		{"retry still fails", 503, 1, 2, 3, false},
		{"replacement expires", 404, 1, 3, 4, false},
		{"concurrent retries", 202, 8, 2, 3, false},
		{"waiting reconnect", 202, 1, 2, 3, true},
	} {
		t.Run(tc.name, func(t *testing.T) {
			var initializations, notices, premature, calls atomic.Int32
			var ready atomic.Bool
			client := NewClient(&http.Client{Transport: roundTripFunc(func(request *http.Request) (*http.Response, error) {
				if request.Method == http.MethodDelete {
					return mcpHTTPResponse(204, "", ""), nil
				}
				payload := readMCPPayload(t, request)
				switch payload.Method {
				case string(mcp.MethodInitialize):
					n := initializations.Add(1)
					ready.Store(false)
					response := mcpJSONResultResponse(payload.ID, map[string]any{
						"protocolVersion": mcp.LATEST_PROTOCOL_VERSION,
						"capabilities":    map[string]any{},
						"serverInfo":      map[string]any{"name": "studio", "version": "1"},
					})
					response.Header.Set(mcptransport.HeaderKeySessionID, fmt.Sprintf("session-%d", n))
					return response, nil
				case string(mcp.MethodNotificationInitialized):
					attempt := notices.Add(1)
					if attempt == 2 {
						return mcpHTTPResponse(503, "text/plain", "unavailable"), nil
					}
					if attempt == 3 && tc.retryStatus != 202 {
						return mcpHTTPResponse(tc.retryStatus, "text/plain", "retry failed"), nil
					}
					ready.Store(true)
					return mcpHTTPResponse(202, "", ""), nil
				case string(mcp.MethodToolsList):
					if initializations.Load() == 1 {
						return mcpHTTPResponse(404, "text/plain", "expired"), nil
					}
					if !ready.Load() {
						premature.Add(1)
					}
					calls.Add(1)
					return mcpJSONResultResponse(payload.ID, map[string]any{"tools": []any{}}), nil
				default:
					return nil, fmt.Errorf("unexpected method %s", payload.Method)
				}
			})})
			session := newMCPTestSession(t, client, Target{
				Server: "http://localhost", InstanceID: "instance-dev",
				Credential: Credential{AccessKey: "test-ak", SecretKey: "test-sk"}, Timeout: time.Second,
			})
			defer session.Close()
			ctx := context.Background()
			initializeClientSession(t, ctx, session)
			call := func(id int) error {
				_, _, err := session.SendMessage(ctx, json.RawMessage(fmt.Sprintf(`{"jsonrpc":"2.0","id":%d,"method":"tools/list","params":{}}`, id)))
				return err
			}
			if err := call(2); err == nil {
				t.Fatal("expected initialized notification failure")
			}
			if tc.staleGeneration {
				// Another caller saw the original generation before its 404.
				if err := session.reinitialize(ctx, 1); err != nil {
					t.Fatalf("waiting reconnect: %v", err)
				}
			}
			var wg sync.WaitGroup
			for i := 0; i < tc.callers; i++ {
				wg.Add(1)
				go func(id int) {
					defer wg.Done()
					err := call(id)
					if tc.retryStatus == 503 && err == nil {
						t.Error("expected retry error")
					}
					if tc.retryStatus != 503 && err != nil {
						t.Errorf("retry: %v", err)
					}
				}(i + 3)
			}
			wg.Wait()
			if got := premature.Load(); got != 0 {
				t.Errorf("requests before initialized accepted = %d, want 0", got)
			}
			wantCalls := int32(tc.callers)
			if tc.retryStatus == 503 {
				wantCalls = 0
			}
			if got := calls.Load(); got != wantCalls {
				t.Errorf("tool calls = %d, want %d", got, wantCalls)
			}
			if got := initializations.Load(); got != tc.wantInitializations {
				t.Errorf("initializations = %d, want %d", got, tc.wantInitializations)
			}
			if got := notices.Load(); got != tc.wantNotices {
				t.Errorf("initialized notifications = %d, want %d", got, tc.wantNotices)
			}
		})
	}
}
