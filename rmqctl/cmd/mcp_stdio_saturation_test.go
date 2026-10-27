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
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/studio"
)

// Reaching EOF requires the scanner to have handed off all preceding lines.
// With two queued requests and a one-event buffer, the old loop has consumed
// the first queued event when EOF is reached; the corrected loop pauses input.
type saturationInput struct {
	*strings.Reader
	eof  chan struct{}
	once sync.Once
}

func (r *saturationInput) Read(p []byte) (int, error) {
	n, err := r.Reader.Read(p)
	if err == io.EOF {
		r.once.Do(func() { close(r.eof) })
	}
	return n, err
}

type saturationOutput struct {
	progress  chan struct{}
	once      sync.Once
	responses atomic.Int32
}

func (w *saturationOutput) Write(p []byte) (int, error) {
	if bytes.Contains(p, []byte(`"notifications/progress"`)) {
		w.once.Do(func() { close(w.progress) })
	}
	if bytes.Contains(p, []byte(`"result"`)) {
		w.responses.Add(1)
	}
	return len(p), nil
}

func TestStdioSaturationForwardsHTTPProgress(t *testing.T) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	release, notify := make(chan struct{}), make(chan struct{})
	var releaseOnce, notifyOnce sync.Once
	releaseCalls := func() { releaseOnce.Do(func() { close(release) }) }
	sendNotification := func() { notifyOnce.Do(func() { close(notify) }) }
	defer releaseCalls()
	defer sendNotification()
	var calls, running, peak atomic.Int32
	saturated := make(chan struct{})
	output := &saturationOutput{progress: make(chan struct{})}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodDelete {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		var req struct {
			ID     json.RawMessage `json:"id"`
			Method string          `json:"method"`
		}
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			t.Error(err)
			return
		}
		if req.Method == "notifications/initialized" {
			w.WriteHeader(http.StatusAccepted)
			return
		}
		if req.Method == "initialize" {
			w.Header().Set("Content-Type", "application/json")
			w.Header().Set("Mcp-Session-Id", "synthetic-session")
			fmt.Fprintf(w, `{"jsonrpc":"2.0","id":%s,"result":{"protocolVersion":"2025-11-25","capabilities":{},"serverInfo":{"name":"probe","version":"1"}}}`, req.ID)
			return
		}
		if req.Method != "tools/list" {
			t.Errorf("unexpected method %s", req.Method)
			return
		}
		current := running.Add(1)
		for {
			previous := peak.Load()
			if current <= previous || peak.CompareAndSwap(previous, current) {
				break
			}
		}
		completed := false
		defer func() {
			if !completed {
				running.Add(-1)
			}
		}()
		index := calls.Add(1)
		if index == maxConcurrentCalls {
			close(saturated)
		}
		w.Header().Set("Content-Type", "text/event-stream")
		w.WriteHeader(http.StatusOK)
		w.(http.Flusher).Flush()
		if index == 1 {
			select {
			case <-notify:
			case <-r.Context().Done():
				return
			}
			fmt.Fprint(w, "event: message\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\",\"params\":{\"progress\":1}}\n\n")
			w.(http.Flusher).Flush()
		}
		select {
		case <-release:
		case <-r.Context().Done():
			return
		}
		// Count a call as complete before publishing its terminal response, which
		// permits the client to admit the next queued call immediately.
		running.Add(-1)
		completed = true
		fmt.Fprintf(w, "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":%s,\"result\":{\"tools\":[]}}\n\n", req.ID)
	}))
	defer func() {
		cancel()
		sendNotification()
		releaseCalls()
		server.Close()
	}()
	session, err := studio.NewClient(server.Client()).NewMCPClientSession(studio.Target{Server: server.URL, InstanceID: "test-instance", Credential: studio.Credential{AccessKey: "synthetic-ak", SecretKey: "synthetic-sk"}, Timeout: 4 * time.Second})
	if err != nil {
		t.Fatal(err)
	}
	var input strings.Builder
	input.WriteString(`{"jsonrpc":"2.0","id":1000,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"probe","version":"1"}}}` + "\n")
	input.WriteString(`{"jsonrpc":"2.0","method":"notifications/initialized"}` + "\n")
	const requestCount = maxConcurrentCalls + 2
	for i := 0; i < requestCount; i++ {
		fmt.Fprintf(&input, `{"jsonrpc":"2.0","id":%d,"method":"tools/list"}`+"\n", i+1)
	}
	reader := &saturationInput{Reader: strings.NewReader(input.String()), eof: make(chan struct{})}
	finished := make(chan error, 1)
	stopped := make(chan struct{})
	go func() { defer close(stopped); finished <- runStdioProxy(ctx, reader, output, io.Discard, session) }()
	defer func() { cancel(); sendNotification(); releaseCalls(); <-stopped }()
	select {
	case <-saturated:
	case <-ctx.Done():
		t.Fatal("HTTP calls did not saturate")
	}
	// The EOF checkpoint establishes blocked dispatch on the baseline. A loop
	// that correctly defers queued input remains paused, so use a bounded wait
	// before testing notification delivery in that state, rather than a sleep.
	select {
	case <-reader.eof:
	case <-time.After(100 * time.Millisecond):
	}
	sendNotification()
	select {
	case <-output.progress:
	case <-time.After(time.Second):
		t.Error("SSE progress blocked behind queued stdin at capacity")
	}
	if calls.Load() != maxConcurrentCalls {
		t.Errorf("admitted %d requests before capacity was released", calls.Load())
	}
	releaseCalls()
	select {
	case err := <-finished:
		if err != nil {
			t.Errorf("proxy: %v", err)
		}
	case <-ctx.Done():
		t.Fatal("proxy did not drain queued calls")
	}
	if calls.Load() != requestCount {
		t.Errorf("HTTP requests=%d, want %d", calls.Load(), requestCount)
	}
	if peak.Load() > maxConcurrentCalls {
		t.Errorf("HTTP peak=%d, exceeds %d", peak.Load(), maxConcurrentCalls)
	}
	if output.responses.Load() != requestCount+1 {
		t.Errorf("response frames=%d, want %d including initialize", output.responses.Load(), requestCount+1)
	}
	t.Logf("HTTP requests=%d, peak outstanding=%d, response frames=%d", calls.Load(), peak.Load(), output.responses.Load())
}

func TestStdioSaturationCancellationDrainsWorkers(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	session := newFakeMcpSession()
	saturated := make(chan struct{})
	var calls, running atomic.Int32
	session.sendHandler = func(callCtx context.Context, payload json.RawMessage) (json.RawMessage, bool, error) {
		var request struct {
			ID     json.RawMessage `json:"id"`
			Method string          `json:"method"`
		}
		if err := json.Unmarshal(payload, &request); err != nil {
			return nil, false, err
		}
		if request.Method == "initialize" {
			return json.RawMessage(`{"jsonrpc":"2.0","id":1000,"result":{}}`), true, nil
		}
		if request.Method == "notifications/initialized" {
			return nil, false, nil
		}
		running.Add(1)
		defer running.Add(-1)
		if calls.Add(1) == maxConcurrentCalls {
			close(saturated)
		}
		<-callCtx.Done()
		return nil, false, callCtx.Err()
	}
	var input strings.Builder
	input.WriteString(`{"jsonrpc":"2.0","id":1000,"method":"initialize"}` + "\n" + `{"jsonrpc":"2.0","method":"notifications/initialized"}` + "\n")
	for i := 0; i < maxConcurrentCalls+2; i++ {
		fmt.Fprintf(&input, `{"jsonrpc":"2.0","id":%d,"method":"tools/list"}`+"\n", i+1)
	}
	finished := make(chan error, 1)
	stopped := make(chan struct{})
	go func() {
		defer close(stopped)
		finished <- runStdioProxy(ctx, strings.NewReader(input.String()), io.Discard, io.Discard, session)
	}()
	defer func() { cancel(); <-stopped }()
	select {
	case <-saturated:
	case <-time.After(2 * time.Second):
		cancel()
		<-finished
		t.Fatal("calls did not saturate")
	}
	cancel()
	select {
	case err := <-finished:
		if err != nil {
			t.Errorf("cancelled proxy: %v", err)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("workers did not drain after cancellation")
	}
	if running.Load() != 0 || !session.closed {
		t.Errorf("workers=%d closed=%v", running.Load(), session.closed)
	}
}
