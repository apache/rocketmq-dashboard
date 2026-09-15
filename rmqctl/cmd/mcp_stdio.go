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
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"strings"
	"sync"
	"unicode/utf8"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/studio"
)

const stdioMaxLineBytes = 16 * 1024 * 1024

const (
	jsonrpcInternalErrorCode = -32603
	jsonrpcServerErrorCode   = -32000
	// stdioDiagnosticLimit caps diagnostic message length to avoid flooding stderr.
	stdioDiagnosticLimit = 512
	// maxConcurrentCalls bounds requests forwarded to Studio at one time.
	maxConcurrentCalls = 64
	// scannerInitialBuffer is the initial buffer size for the stdin scanner.
	scannerInitialBuffer = 64 * 1024
)

// mcpSession captures the Studio MCP session operations used by stdioProxy.
// *studio.MCPClientSession satisfies this interface; tests may substitute a
// fake to exercise the proxy state machine without an HTTP server.
type mcpSession interface {
	Close() error
	Notifications() <-chan json.RawMessage
	SendMessage(ctx context.Context, payload json.RawMessage) (json.RawMessage, bool, error)
}

type stdinEvent struct {
	line string
	err  error
	done bool
}

type callResult struct {
	line     string
	response json.RawMessage
	ok       bool
	err      error
}

type jsonRPCErrorPayload struct {
	JSONRPC string       `json:"jsonrpc"`
	ID      any          `json:"id,omitempty"`
	Error   jsonRPCError `json:"error"`
}

type jsonRPCError struct {
	Code    int               `json:"code"`
	Message string            `json:"message"`
	Data    *jsonRPCErrorData `json:"data,omitempty"`
}

type jsonRPCErrorData struct {
	Code    string `json:"code"`
	Message string `json:"message"`
	Hint    string `json:"hint"`
}

// runStdioProxy starts a stdioProxy for the given session and blocks until
// stdin is exhausted and all in-flight calls drain. The session starts lazily
// when its first message is sent; the proxy owns closing it.
func runStdioProxy(ctx context.Context, in io.Reader, out io.Writer, errOut io.Writer, session mcpSession) error {
	defer func() { _ = session.Close() }()

	proxy := newStdioProxy(in, out, errOut, session)
	err := proxy.run(ctx)
	close(proxy.done)
	proxy.calls.Wait()
	return err
}

// stdioProxy bridges newline-delimited MCP JSON-RPC traffic from stdin to a
// Studio MCP session, forwarding server notifications and call results to out.
type stdioProxy struct {
	in      io.Reader
	out     io.Writer
	errOut  io.Writer
	session mcpSession

	events    chan stdinEvent
	results   chan callResult
	done      chan struct{}
	callSlots chan struct{}
	calls     sync.WaitGroup

	inputDone   bool
	activeCalls int
	inputErr    error

	initializationSucceeded bool
	ready                   bool
}

func newStdioProxy(in io.Reader, out io.Writer, errOut io.Writer, session mcpSession) *stdioProxy {
	return &stdioProxy{
		in:        in,
		out:       out,
		errOut:    errOut,
		session:   session,
		events:    make(chan stdinEvent, 1),
		results:   make(chan callResult, maxConcurrentCalls),
		done:      make(chan struct{}),
		callSlots: make(chan struct{}, maxConcurrentCalls),
	}
}

// run drives the select loop until stdin is exhausted and all in-flight calls
// have drained. It returns the scanner error from readInput (nil on clean EOF).
//
// When ctx is cancelled, run returns nil immediately rather than propagating
// inputErr. This is intentional: a cancelled context signals an external
// shutdown (e.g. SIGINT), and surfacing a stale scanner error would be
// misleading. The caller (runStdioProxy) already closes the session and
// drains in-flight calls regardless of the return value.
func (p *stdioProxy) run(ctx context.Context) error {
	go readInput(p.in, p.events, p.done)
	for !p.inputDone || p.activeCalls > 0 {
		events := p.events
		// Preserve stdin order through the first initialize/initialized exchange.
		// Keep handling responses, server notifications and cancellation while a
		// handshake message is in flight; ordinary calls may run concurrently once
		// the initialized notification has been sent successfully.
		if !p.ready && p.activeCalls > 0 {
			events = nil
		}
		select {
		case <-ctx.Done():
			return nil
		case message := <-p.session.Notifications():
			p.handleNotification(message)
		case event := <-events:
			p.handleEvent(ctx, event)
		case result := <-p.results:
			p.handleResult(result)
		}
	}
	return p.inputErr
}

func (p *stdioProxy) handleNotification(message json.RawMessage) {
	fmt.Fprintln(p.out, string(message))
}

func (p *stdioProxy) handleEvent(ctx context.Context, event stdinEvent) {
	if event.done {
		p.inputDone = true
		p.inputErr = event.err
		// Setting events to nil disables this case in select so the loop
		// drains remaining in-flight calls without re-arming the reader.
		p.events = nil
		return
	}
	line := strings.TrimSpace(event.line)
	if line == "" {
		return
	}
	select {
	case p.callSlots <- struct{}{}:
		p.activeCalls++
		p.calls.Go(func() {
			sendMessage(ctx, p.session, line, p.results, p.done, p.callSlots)
		})
	case <-ctx.Done():
	}
}

func (p *stdioProxy) handleResult(result callResult) {
	p.activeCalls--
	p.recordInitialization(result)
	if result.err != nil {
		writeStdioError(p.out, p.errOut, result.line, result.err)
		return
	}
	if result.ok {
		fmt.Fprintln(p.out, string(result.response))
	}
}

func (p *stdioProxy) recordInitialization(result callResult) {
	if p.ready {
		return
	}
	var message struct {
		Method string          `json:"method"`
		ID     json.RawMessage `json:"id"`
	}
	if err := json.Unmarshal([]byte(result.line), &message); err != nil {
		return
	}
	switch {
	case message.Method == "initialize" && len(message.ID) > 0:
		var response struct {
			Result json.RawMessage `json:"result"`
			Error  json.RawMessage `json:"error"`
		}
		p.initializationSucceeded = result.err == nil && result.ok && json.Unmarshal(result.response, &response) == nil &&
			len(response.Result) > 0 && (len(response.Error) == 0 || bytes.Equal(response.Error, []byte("null")))
	case message.Method == "notifications/initialized" && len(message.ID) == 0:
		p.ready = result.err == nil && p.initializationSucceeded
	}
}

func readInput(in io.Reader, events chan<- stdinEvent, done <-chan struct{}) {
	scanner := bufio.NewScanner(in)
	scanner.Buffer(make([]byte, 0, scannerInitialBuffer), stdioMaxLineBytes)
	for scanner.Scan() {
		select {
		case events <- stdinEvent{line: scanner.Text()}:
		case <-done:
			return
		}
	}
	select {
	case events <- stdinEvent{done: true, err: scanner.Err()}:
	case <-done:
	}
}

func sendMessage(
	ctx context.Context,
	session mcpSession,
	line string,
	results chan<- callResult,
	done <-chan struct{},
	callSlots <-chan struct{},
) {
	response, ok, err := session.SendMessage(ctx, []byte(line))
	<-callSlots
	select {
	case results <- callResult{line: line, response: response, ok: ok, err: err}:
	case <-done:
	}
}

func writeStdioError(out io.Writer, errOut io.Writer, requestLine string, callErr error) {
	requestID := extractRequestID([]byte(requestLine))
	fmt.Fprintln(errOut, "mcp stdio proxy call failed:", safeStdioDiagnostic(callErr.Error()))
	if requestID == nil {
		return
	}
	rpcError := jsonRPCError{
		Code:    jsonrpcInternalErrorCode,
		Message: "stdio proxy call failed",
	}
	if statusError, ok := errors.AsType[*studio.MCPHTTPStatusError](callErr); ok {
		code := safeStdioDiagnostic(statusError.Code)
		message := safeStdioDiagnostic(statusError.Message)
		hint := safeStdioDiagnostic(statusError.Hint)
		if code == "" {
			code = fmt.Sprintf("HTTP_%d", statusError.StatusCode)
		}
		if message == "" {
			message = fmt.Sprintf("Studio MCP endpoint returned HTTP %d", statusError.StatusCode)
		}
		if hint == "" {
			hint = "Check the MCP configuration and retry the request."
		}
		rpcError = jsonRPCError{
			Code:    jsonrpcServerErrorCode,
			Message: message,
			Data: &jsonRPCErrorData{
				Code:    code,
				Message: message,
				Hint:    hint,
			},
		}
	}
	errorPayload := jsonRPCErrorPayload{
		JSONRPC: "2.0",
		ID:      requestID,
		Error:   rpcError,
	}
	payload, err := json.Marshal(errorPayload)
	if err != nil {
		return
	}
	fmt.Fprintln(out, string(payload))
}

func safeStdioDiagnostic(message string) string {
	diagnostic := safeDiagnosticText(message)
	if len(diagnostic) <= stdioDiagnosticLimit {
		return diagnostic
	}
	// Truncate by bytes but back up to a valid UTF-8 boundary so multi-byte
	// runes are not split.
	end := stdioDiagnosticLimit
	for end > 0 && !utf8.RuneStart(diagnostic[end]) {
		end--
	}
	return diagnostic[:end]
}

func safeDiagnosticText(text string) string {
	return strings.Map(func(character rune) rune {
		switch character {
		case '\r', '\n', '\t':
			return ' '
		default:
			return character
		}
	}, text)
}

func extractRequestID(payload []byte) any {
	var request struct {
		ID json.RawMessage `json:"id"`
	}
	if err := json.Unmarshal(payload, &request); err != nil {
		return nil
	}
	if len(request.ID) == 0 || bytes.Equal(bytes.TrimSpace(request.ID), []byte("null")) {
		return nil
	}
	return request.ID
}

var _ mcpSession = (*studio.MCPClientSession)(nil)
