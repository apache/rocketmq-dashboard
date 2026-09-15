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
package cmd

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"strings"
	"sync"
	"testing"
)

// fakeMcpSession is a test double for mcpSession. It records Close and
// SendMessage calls and lets tests control responses and server notifications.
type fakeMcpSession struct {
	mu            sync.Mutex
	closed        bool
	notifications chan json.RawMessage
	sendHandler   func(ctx context.Context, payload json.RawMessage) (json.RawMessage, bool, error)
}

func newFakeMcpSession() *fakeMcpSession {
	return &fakeMcpSession{notifications: make(chan json.RawMessage, 8)}
}

func (f *fakeMcpSession) Close() error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.closed = true
	close(f.notifications)
	return nil
}

func (f *fakeMcpSession) Notifications() <-chan json.RawMessage {
	return f.notifications
}

func (f *fakeMcpSession) SendMessage(ctx context.Context, payload json.RawMessage) (json.RawMessage, bool, error) {
	f.mu.Lock()
	handler := f.sendHandler
	f.mu.Unlock()
	if handler == nil {
		return nil, false, errors.New("fakeMcpSession: no send handler configured")
	}
	return handler(ctx, payload)
}

func (f *fakeMcpSession) pushNotification(message string) {
	f.notifications <- json.RawMessage(message)
}

// TestStdioProxyForwardsSuccessfulResponse verifies that a single request line
// produces a single JSON-RPC response line on stdout.
func TestStdioProxyForwardsSuccessfulResponse(t *testing.T) {
	session := newFakeMcpSession()
	session.sendHandler = func(ctx context.Context, payload json.RawMessage) (json.RawMessage, bool, error) {
		return json.RawMessage(`{"jsonrpc":"2.0","id":1,"result":{"ok":true}}`), true, nil
	}
	stdin := strings.NewReader(`{"jsonrpc":"2.0","id":1,"method":"tools/list"}` + "\n")
	stdout := &bytes.Buffer{}
	stderr := &bytes.Buffer{}

	if err := runStdioProxy(context.Background(), stdin, stdout, stderr, session); err != nil {
		t.Fatalf("runStdioProxy: %v", err)
	}
	lines := strings.Split(strings.TrimSpace(stdout.String()), "\n")
	if len(lines) != 1 {
		t.Fatalf("stdout lines = %d, want 1:\n%s", len(lines), stdout.String())
	}
	var response map[string]any
	if err := json.Unmarshal([]byte(lines[0]), &response); err != nil {
		t.Fatalf("stdout not JSON: %v\n%s", err, stdout.String())
	}
	if response["jsonrpc"] != "2.0" {
		t.Fatalf("jsonrpc = %#v, want 2.0", response["jsonrpc"])
	}
	if stderr.Len() != 0 {
		t.Fatalf("stderr not empty: %s", stderr.String())
	}
	if !session.closed {
		t.Fatal("session was not closed")
	}
}

// TestStdioProxyForwardsServerNotification verifies that server-initiated
// notifications are written to stdout interleaved with call responses.
func TestStdioProxyForwardsServerNotification(t *testing.T) {
	session := newFakeMcpSession()
	notification := `{"jsonrpc":"2.0","method":"notifications/progress","params":{"progress":50}}`
	session.sendHandler = func(ctx context.Context, payload json.RawMessage) (json.RawMessage, bool, error) {
		// Emit a server notification before returning the response so the
		// proxy observes it on the Notifications channel during the loop.
		session.pushNotification(notification)
		return json.RawMessage(`{"jsonrpc":"2.0","id":1,"result":{}}`), true, nil
	}
	stdin := strings.NewReader(`{"jsonrpc":"2.0","id":1,"method":"tools/call"}` + "\n")
	stdout := &bytes.Buffer{}
	stderr := &bytes.Buffer{}

	if err := runStdioProxy(context.Background(), stdin, stdout, stderr, session); err != nil {
		t.Fatalf("runStdioProxy: %v", err)
	}
	output := stdout.String()
	if !strings.Contains(output, "notifications/progress") {
		t.Fatalf("stdout missing notification:\n%s", output)
	}
	if !strings.Contains(output, `"id":1`) {
		t.Fatalf("stdout missing call response:\n%s", output)
	}
}
