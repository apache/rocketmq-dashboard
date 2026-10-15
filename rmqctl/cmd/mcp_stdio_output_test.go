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
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

var errStdioBrokenPipe = errors.New("synthetic broken pipe")

type stdioBrokenWriter struct{ writes int }

func (w *stdioBrokenWriter) Write(p []byte) (int, error) {
	w.writes++
	return 0, errStdioBrokenPipe
}

func TestStdioOutputFailure(t *testing.T) {
	for _, mode := range []string{"response", "notification", "error"} {
		t.Run(mode, func(t *testing.T) {
			session := newFakeMcpSession()
			session.sendHandler = func(ctx context.Context, payload json.RawMessage) (json.RawMessage, bool, error) {
				if mode == "notification" {
					session.pushNotification(`{"jsonrpc":"2.0","method":"notifications/progress","params":{"progress":1}}`)
					return nil, false, nil
				}
				if mode == "error" {
					return nil, false, errors.New("synthetic upstream failure")
				}
				return json.RawMessage(`{"jsonrpc":"2.0","id":1,"result":{}}`), true, nil
			}
			out := &stdioBrokenWriter{}
			err := runStdioProxy(context.Background(), strings.NewReader(`{"jsonrpc":"2.0","id":1,"method":"tools/list"}`+"\n"), out, &bytes.Buffer{}, session)
			t.Logf("mode=%s failed writes=%d return=%v session closed=%v", mode, out.writes, err, session.closed)
			if out.writes == 0 {
				t.Fatal("probe did not exercise writer")
			}
			if !errors.Is(err, errStdioBrokenPipe) {
				t.Errorf("output error lost: %v", err)
			}
			if !session.closed {
				t.Error("session was not closed")
			}
		})
	}
}

func TestStdioAppBrokenOutput(t *testing.T) {
	var posts, deletes atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodDelete {
			deletes.Add(1)
			w.WriteHeader(http.StatusNoContent)
			return
		}
		if r.Method != http.MethodPost {
			w.WriteHeader(http.StatusMethodNotAllowed)
			return
		}
		posts.Add(1)
		var request struct {
			ID     json.RawMessage `json:"id"`
			Method string          `json:"method"`
		}
		if err := json.NewDecoder(r.Body).Decode(&request); err != nil {
			t.Error(err)
			return
		}
		if request.Method == "notifications/initialized" {
			w.WriteHeader(http.StatusAccepted)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		w.Header().Set("Mcp-Session-Id", "probe-session")
		if request.Method == "initialize" {
			fmt.Fprintf(w, `{"jsonrpc":"2.0","id":%s,"result":{"protocolVersion":"2025-11-25","capabilities":{},"serverInfo":{"name":"probe","version":"1"}}}`, request.ID)
		} else {
			fmt.Fprintf(w, `{"jsonrpc":"2.0","id":%s,"result":{"tools":[]}}`, request.ID)
		}
	}))
	defer server.Close()
	out := &stdioBrokenWriter{}
	app := NewApp(out, &bytes.Buffer{})
	app.Store.Getenv = testEnv
	app.HTTP = server.Client()
	path := newTestConfigPath(t)
	if err := app.Store.Save(path, newTestConfig(server.URL)); err != nil {
		t.Fatal(err)
	}
	app.In = strings.NewReader(`{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"probe","version":"1"}}}` + "\n" + `{"jsonrpc":"2.0","method":"notifications/initialized"}` + "\n" + `{"jsonrpc":"2.0","id":2,"method":"tools/list"}` + "\n")
	code := app.Execute([]string{"--config", path, "--instance-id", "test-instance", "--timeout", "1s", "mcp", "stdio"})
	t.Logf("App exit=%d, failed stdout writes=%d, real HTTP POST=%d, DELETE=%d", code, out.writes, posts.Load(), deletes.Load())
	if out.writes != 1 || posts.Load() != 1 || deletes.Load() != 1 {
		t.Fatal("expected immediate failure after initialize, one failed write, and session teardown")
	}
	if code == 0 {
		t.Error("real mcp stdio command reports success with a broken stdout")
	}
}

type stdioShortWriter struct{}

func (stdioShortWriter) Write(p []byte) (int, error) { return len(p) - 1, nil }

func TestStdioShortWrite(t *testing.T) {
	session := newFakeMcpSession()
	session.sendHandler = func(context.Context, json.RawMessage) (json.RawMessage, bool, error) {
		return json.RawMessage(`{"jsonrpc":"2.0","id":1,"result":{}}`), true, nil
	}
	err := runStdioProxy(context.Background(), strings.NewReader(`{"jsonrpc":"2.0","id":1,"method":"tools/list"}`+"\n"), stdioShortWriter{}, &bytes.Buffer{}, session)
	if !errors.Is(err, io.ErrShortWrite) {
		t.Fatalf("error = %v, want short write", err)
	}
}

type stdioClosingSession struct {
	*fakeMcpSession
	closedSignal chan struct{}
}

func (s *stdioClosingSession) Close() error {
	err := s.fakeMcpSession.Close()
	close(s.closedSignal)
	return err
}

func TestStdioOutputFailureStopsWorkersAndPipeReader(t *testing.T) {
	input, writer := io.Pipe()
	defer input.Close()
	defer writer.Close()
	session := &stdioClosingSession{fakeMcpSession: newFakeMcpSession(), closedSignal: make(chan struct{})}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	workerDone := make(chan struct{})
	session.sendHandler = func(callCtx context.Context, payload json.RawMessage) (json.RawMessage, bool, error) {
		defer close(workerDone)
		session.pushNotification(`{"jsonrpc":"2.0","method":"notifications/progress","params":{"progress":1}}`)
		// Closing the session must not be deferred until after waiting for this call.
		select {
		case <-session.closedSignal:
		case <-ctx.Done():
			return nil, false, ctx.Err()
		}
		if callCtx.Err() == nil {
			t.Error("in-flight call context was not cancelled")
		}
		return nil, false, callCtx.Err()
	}
	inputWriterDone := make(chan struct{})
	go func() {
		defer close(inputWriterDone)
		_, _ = io.WriteString(writer, `{"jsonrpc":"2.0","id":1,"method":"tools/list"}`+"\n")
	}()
	out := &stdioBrokenWriter{}
	err := runStdioProxy(ctx, input, out, &bytes.Buffer{}, session)
	if !errors.Is(err, errStdioBrokenPipe) {
		t.Fatalf("error = %v", err)
	}
	if ctx.Err() != nil {
		t.Fatal("cleanup exceeded deadline")
	}
	select {
	case <-workerDone:
	default:
		t.Fatal("call worker still running")
	}
	<-inputWriterDone
	// Returning also joins the scanner after closing interruptible stdin.
	if _, err := writer.Write([]byte("more input")); !errors.Is(err, io.ErrClosedPipe) {
		t.Fatalf("stdin pipe remains open: %v", err)
	}
	if out.writes != 1 {
		t.Fatalf("stdout writes = %d, want stop after first failure", out.writes)
	}
}
