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
	"bufio"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"os/exec"
	"runtime"
	"sync"
	"syscall"
	"testing"
	"time"

	mcptransport "github.com/mark3labs/mcp-go/client/transport"
	mcp "github.com/mark3labs/mcp-go/mcp"
)

// sigtermChildEnv marks the re-executed test binary as the child process that
// runs App.Execute("mcp stdio") under the real signal handling path.
const sigtermChildEnv = "RMQCTL_SIGTERM_TEST_CHILD"

// TestExecuteSigtermTearsDownMcpSession guards the teardown contract of the
// stdio proxy through the real entry point: MCP hosts stop stdio servers with
// SIGTERM, and the proxy only runs its deferred session Close (DELETE
// /api/mcp) when the command context that Execute builds is cancelled, so
// SIGTERM must be caught rather than left to the default kill disposition.
//
// The test re-executes the test binary as a child process: signalling the
// test process itself would kill the whole `go test ./cmd` binary if the
// registration ever broke, losing every other result in the package. The
// child runs App.Execute with the `mcp stdio` arguments against a fake Studio
// MCP endpoint; the parent sends SIGTERM once the initialize response has
// been forwarded (the session is established) and asserts that the teardown
// DELETE reaches the server with the established session id. If Execute
// stopped listening for SIGTERM, the default kill disposition would terminate
// the child before any DELETE and the test fails — a test that only drives
// the extracted signal helper cannot catch that regression.
func TestExecuteSigtermTearsDownMcpSession(t *testing.T) {
	if runtime.GOOS == "windows" {
		t.Skip("SIGTERM delivery is POSIX-only")
	}
	if os.Getenv(sigtermChildEnv) == "1" {
		runSigtermChild(t)
		return
	}

	var mu sync.Mutex
	deleteCount := 0
	deleteSeen := make(chan struct{})
	var deleteOnce sync.Once
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, request *http.Request) {
		if request.URL.Path != "/api/mcp" {
			http.NotFound(w, request)
			return
		}
		switch request.Method {
		case http.MethodDelete:
			mu.Lock()
			deleteCount++
			mu.Unlock()
			if got := request.Header.Get(mcptransport.HeaderKeySessionID); got != "session-1" {
				t.Errorf("DELETE session ID = %q, want %q", got, "session-1")
			}
			deleteOnce.Do(func() { close(deleteSeen) })
			w.WriteHeader(http.StatusNoContent)
		case http.MethodPost:
			body, err := io.ReadAll(request.Body)
			if err != nil {
				t.Errorf("read request: %v", err)
				w.WriteHeader(http.StatusBadRequest)
				return
			}
			var payload struct {
				ID     json.RawMessage `json:"id"`
				Method string          `json:"method"`
			}
			if err := json.Unmarshal(body, &payload); err != nil {
				t.Errorf("decode request %q: %v", body, err)
				w.WriteHeader(http.StatusBadRequest)
				return
			}
			switch payload.Method {
			case string(mcp.MethodInitialize):
				w.Header().Set("Content-Type", "application/json")
				w.Header().Set(mcptransport.HeaderKeySessionID, "session-1")
				fmt.Fprintf(w, `{"jsonrpc":"2.0","id":%s,"result":{"protocolVersion":%q,"capabilities":{},"serverInfo":{"name":"studio","version":"1"}}}`,
					payload.ID, mcp.LATEST_PROTOCOL_VERSION)
			case string(mcp.MethodNotificationInitialized):
				w.WriteHeader(http.StatusAccepted)
			default:
				w.Header().Set("Content-Type", "application/json")
				fmt.Fprintf(w, `{"jsonrpc":"2.0","id":%s,"result":{}}`, payload.ID)
			}
		default:
			w.WriteHeader(http.StatusMethodNotAllowed)
		}
	}))
	defer server.Close()

	configPath := newTestConfigPath(t)
	app := NewApp(io.Discard, io.Discard)
	if err := app.Store.Save(configPath, newTestConfig(server.URL)); err != nil {
		t.Fatal(err)
	}

	stdinReader, stdinWriter, err := os.Pipe()
	if err != nil {
		t.Fatal(err)
	}
	defer stdinWriter.Close()
	initializeRequest := fmt.Sprintf(
		`{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":%q,"capabilities":{},"clientInfo":{"name":"sigterm-test","version":"1"}}}`,
		mcp.LATEST_PROTOCOL_VERSION)
	if _, err := fmt.Fprintf(stdinWriter, "%s\n", initializeRequest); err != nil {
		t.Fatal(err)
	}

	child := exec.Command(os.Args[0], "-test.run=^TestExecuteSigtermTearsDownMcpSession$", "-test.timeout=60s")
	child.Env = append(os.Environ(),
		sigtermChildEnv+"=1",
		"RMQCTL_CONFIG="+configPath,
		"RMQ_TEST_AK="+testAccessKey,
		"RMQ_TEST_SK="+testSecretKey,
	)
	child.Stdin = stdinReader
	stdout, err := child.StdoutPipe()
	if err != nil {
		t.Fatal(err)
	}
	childStderr := &bytes.Buffer{}
	child.Stderr = childStderr
	if err := child.Start(); err != nil {
		t.Fatal(err)
	}

	// The proxy writes the forwarded initialize response to its stdout only
	// after SendMessage returned, which is when the transport has recorded
	// the session id — from that point on, SIGTERM must trigger the teardown.
	responseSeen := make(chan struct{})
	go func() {
		scanner := bufio.NewScanner(stdout)
		for scanner.Scan() {
			if bytes.Contains(scanner.Bytes(), []byte("serverInfo")) {
				close(responseSeen)
				return
			}
		}
	}()
	select {
	case <-responseSeen:
	case <-time.After(10 * time.Second):
		t.Fatalf("initialize response was not forwarded before the deadline; child stderr:\n%s", childStderr.String())
	}

	if err := child.Process.Signal(syscall.SIGTERM); err != nil {
		t.Fatal(err)
	}
	select {
	case <-deleteSeen:
	case <-time.After(10 * time.Second):
		t.Fatalf("session teardown DELETE /api/mcp was not issued after SIGTERM; child stderr:\n%s", childStderr.String())
	}
	mu.Lock()
	count := deleteCount
	mu.Unlock()
	if count != 1 {
		t.Fatalf("DELETE count = %d, want 1", count)
	}
	if err := child.Wait(); err != nil {
		t.Fatalf("child did not exit cleanly after teardown: %v; stderr:\n%s", err, childStderr.String())
	}
}

// runSigtermChild is the re-executed half of
// TestExecuteSigtermTearsDownMcpSession: it drives the unmodified App.Execute
// with the `mcp stdio` arguments and blocks until the parent's SIGTERM
// cancels the command context, the proxy drains, and the deferred session
// Close issues the teardown DELETE.
func runSigtermChild(t *testing.T) {
	t.Helper()
	app := NewApp(os.Stdout, os.Stderr)
	if code := app.Execute([]string{"mcp", "stdio", "--instance-id", "instance-dev", "--timeout", "30s"}); code != 0 {
		t.Fatalf("mcp stdio exited with code %d", code)
	}
}
