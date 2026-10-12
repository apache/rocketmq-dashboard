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
	"net"
	"strings"
	"testing"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/studio"
)

func decodeStdioErrorFrame(t *testing.T, out *bytes.Buffer) jsonRPCErrorPayload {
	t.Helper()
	frameLine := strings.TrimSpace(out.String())
	if frameLine == "" {
		t.Fatal("no error frame was written to stdout")
	}
	if strings.Contains(frameLine, "\n") {
		t.Fatalf("stdout carries more than one line: %q", frameLine)
	}
	var payload jsonRPCErrorPayload
	if err := json.Unmarshal([]byte(frameLine), &payload); err != nil {
		t.Fatalf("stdout frame is not JSON: %v (%q)", err, frameLine)
	}
	return payload
}

// TestStdioErrorFrameKeepsTheFailureCause verifies that a call failure without
// an HTTP status still tells the MCP client what went wrong: the classified
// code, the sanitized cause and a hint travel in the frame's data, instead of
// collapsing into a bare "stdio proxy call failed".
func TestStdioErrorFrameKeepsTheFailureCause(t *testing.T) {
	connectionRefused := &net.OpError{Op: "dial", Net: "tcp", Err: errors.New("connect: connection refused")}
	cases := []struct {
		name        string
		callErr     error
		wantCode    string
		wantMessage string
	}{
		{
			name:        "transport failure",
			callErr:     fmt.Errorf("failed to send request: %w", connectionRefused),
			wantCode:    "UNAVAILABLE",
			wantMessage: "connection refused",
		},
		{
			name:        "deadline exceeded",
			callErr:     fmt.Errorf("mcp call: %w", context.DeadlineExceeded),
			wantCode:    "TIMEOUT",
			wantMessage: "deadline exceeded",
		},
		{
			name:        "protocol decode failure",
			callErr:     errors.New("decode MCP JSON-RPC response: unexpected end of JSON input"),
			wantCode:    "COMMAND_FAILED",
			wantMessage: "unexpected end of JSON input",
		},
	}
	for _, testCase := range cases {
		t.Run(testCase.name, func(t *testing.T) {
			out := &bytes.Buffer{}
			errOut := &bytes.Buffer{}
			writeStdioError(out, errOut, `{"jsonrpc":"2.0","id":7,"method":"tools/call"}`, testCase.callErr)

			frame := decodeStdioErrorFrame(t, out)
			if frame.Error.Code != jsonrpcServerErrorCode {
				t.Fatalf("frame code = %d, want %d", frame.Error.Code, jsonrpcServerErrorCode)
			}
			if frame.Error.Data == nil {
				t.Fatal("error frame carries no data payload")
			}
			if frame.Error.Data.Code != testCase.wantCode {
				t.Fatalf("data.code = %q, want %q", frame.Error.Data.Code, testCase.wantCode)
			}
			if !strings.Contains(frame.Error.Message, testCase.wantMessage) {
				t.Fatalf("message = %q, want it to contain %q", frame.Error.Message, testCase.wantMessage)
			}
			if !strings.Contains(frame.Error.Data.Message, testCase.wantMessage) {
				t.Fatalf("data.message = %q, want it to contain %q", frame.Error.Data.Message, testCase.wantMessage)
			}
			if frame.Error.Data.Hint == "" {
				t.Fatal("data.hint is empty, so the caller has nothing to act on")
			}
			if !strings.Contains(errOut.String(), testCase.wantMessage) {
				t.Fatalf("stderr = %q, want the cause to stay there too", errOut.String())
			}
		})
	}
}

// TestStdioErrorFrameKeepsTheHTTPStatusShape pins the branch that already
// worked: a structured MCP HTTP error keeps its own code, message and hint.
func TestStdioErrorFrameKeepsTheHTTPStatusShape(t *testing.T) {
	out := &bytes.Buffer{}
	errOut := &bytes.Buffer{}
	writeStdioError(out, errOut, `{"jsonrpc":"2.0","id":"srv-1","method":"tools/call"}`,
		&studio.MCPHTTPStatusError{StatusCode: 403, Code: "FORBIDDEN", Message: "instance mismatch", Hint: "check the instance id"})

	frame := decodeStdioErrorFrame(t, out)
	if frame.Error.Code != jsonrpcServerErrorCode {
		t.Fatalf("frame code = %d, want %d", frame.Error.Code, jsonrpcServerErrorCode)
	}
	if frame.Error.Data == nil || frame.Error.Data.Code != "FORBIDDEN" {
		t.Fatalf("data = %#v, want the server's FORBIDDEN code", frame.Error.Data)
	}
	if frame.Error.Message != "instance mismatch" || frame.Error.Data.Hint != "check the instance id" {
		t.Fatalf("frame = %#v, want the server's message and hint", frame.Error)
	}
}

// TestStdioErrorFrameSanitizesAndCapsTheCause verifies the frame stays a
// single line and within the diagnostic cap even for a multi-line, oversized
// failure text.
func TestStdioErrorFrameSanitizesAndCapsTheCause(t *testing.T) {
	out := &bytes.Buffer{}
	errOut := &bytes.Buffer{}
	writeStdioError(out, errOut, `{"jsonrpc":"2.0","id":1,"method":"tools/call"}`,
		fmt.Errorf("first line\r\nsecond\tline %s", strings.Repeat("x", 4*stdioDiagnosticLimit)))

	frame := decodeStdioErrorFrame(t, out)
	if len(frame.Error.Message) > stdioDiagnosticLimit {
		t.Fatalf("message length = %d, want at most %d", len(frame.Error.Message), stdioDiagnosticLimit)
	}
	if strings.ContainsAny(frame.Error.Message, "\r\n\t") {
		t.Fatalf("message = %q, want control characters flattened", frame.Error.Message)
	}
	if !strings.Contains(frame.Error.Message, "first line") || !strings.Contains(frame.Error.Message, "second line") {
		t.Fatalf("message = %q, want the sanitized cause", frame.Error.Message)
	}
}

// TestStdioErrorFrameSkipsUnparsableRequests pins the deliberate part of the
// current behaviour: without a request id there is nothing a JSON-RPC error
// frame could address, so only stderr carries the diagnostic.
func TestStdioErrorFrameSkipsUnparsableRequests(t *testing.T) {
	out := &bytes.Buffer{}
	errOut := &bytes.Buffer{}
	writeStdioError(out, errOut, "not json", errors.New("connect: connection refused"))

	if out.Len() != 0 {
		t.Fatalf("stdout = %q, want no frame for an unparsable request", out.String())
	}
	if !strings.Contains(errOut.String(), "connection refused") {
		t.Fatalf("stderr = %q, want the diagnostic", errOut.String())
	}
}
