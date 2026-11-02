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
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"math/big"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	mcptransport "github.com/mark3labs/mcp-go/client/transport"
)

const (
	notificationBufferSize = 64
	statusErrorBodyLimit   = 4 * 1024
)

// noRedirectPolicy prevents HTTP clients from following redirects, preserving
// the original response for callers to inspect.
var noRedirectPolicy = func(*http.Request, []*http.Request) error {
	return http.ErrUseLastResponse
}

// MCPClientSession manages a Streamable HTTP MCP session with Studio Server.
// It handles initialize/initialized lifecycle, automatic reconnection on
// session termination, and forwarding of server-initiated notifications.
type MCPClientSession struct {
	instanceID string
	transport  mcptransport.HTTPConnection
	timeout    time.Duration

	closeOnce     sync.Once
	closed        chan struct{}
	notifications chan json.RawMessage

	// sendGate allows ordinary messages to be concurrent while preventing any
	// message from entering a replacement session before reinitialization and
	// its initialized notification have completed.
	sendGate sendGate
	state    struct {
		sync.RWMutex
		initialize       *mcptransport.JSONRPCRequest
		generation       uint64
		ready            bool
		reconnectPending bool
	}
}

// MCPHTTPStatusError preserves the bounded structured error returned by the
// Studio MCP authentication filter. Error deliberately omits those details so
// callers must handle and sanitize the fields explicitly before displaying them.
type MCPHTTPStatusError struct {
	StatusCode int
	Code       string
	Message    string
	Hint       string
}

func (err *MCPHTTPStatusError) Error() string {
	return fmt.Sprintf("Studio MCP endpoint returned HTTP %d", err.StatusCode)
}

func (c Client) NewMCPClientSession(target Target) (*MCPClientSession, error) {
	if c.httpClient == nil {
		return nil, fmt.Errorf("studio: nil HTTP client")
	}
	if err := target.validate(); err != nil {
		return nil, err
	}
	// The transport follows MCP 2025-11-25 Streamable HTTP:
	// https://modelcontextprotocol.io/specification/2025-11-25/basic/transports#streamable-http
	requestURL, err := url.JoinPath(target.BaseURL(), mcpPath)
	if err != nil {
		return nil, err
	}
	transport, err := mcptransport.NewStreamableHTTP(
		requestURL,
		mcptransport.WithHTTPBasicClient(mcpHTTPClient(c.httpClient, target)),
		mcptransport.WithHTTPLogger(slog.New(slog.NewTextHandler(io.Discard, nil))),
	)
	if err != nil {
		return nil, err
	}
	session := &MCPClientSession{
		instanceID:    target.InstanceID,
		transport:     transport,
		timeout:       target.Timeout,
		closed:        make(chan struct{}),
		notifications: make(chan json.RawMessage, notificationBufferSize),
	}
	transport.SetNotificationHandler(session.forwardNotification)
	return session, nil
}

func mcpHTTPClient(client *http.Client, target Target) *http.Client {
	clone := *client
	// Request timeouts are applied per message. A client-wide timeout would
	// compete with those contexts and may terminate POST SSE responses.
	clone.Timeout = 0
	clone.CheckRedirect = noRedirectPolicy
	base := clone.Transport
	if base == nil {
		base = http.DefaultTransport
	}
	clone.Transport = mcpStatusRoundTripper{base: AuthTransport{
		base: base, target: target,
	}}
	return &clone
}

type mcpStatusRoundTripper struct {
	base http.RoundTripper
}

// errMCPResponseIDMismatch is returned when a JSON-RPC response frame carries
// a raw id token that differs from the originating request's. The SDK decodes
// ids through float64, so an adjacent large integer would otherwise decode to
// the same value and be silently accepted (issue #6904).
var errMCPResponseIDMismatch = errors.New("MCP JSON-RPC response id does not match the request id")

func (transport mcpStatusRoundTripper) RoundTrip(request *http.Request) (*http.Response, error) {
	exchange, requestID := mcpCorrelatedRequest(request)
	response, err := transport.base.RoundTrip(exchange)
	if err != nil {
		return nil, err
	}
	// 400 is intentionally not intercepted: the mcp-go transport inspects it
	// to decide whether to fall back from Streamable HTTP to legacy SSE.
	// See https://modelcontextprotocol.io/specification/2025-11-25/basic/transports#legacy-sse
	if response.StatusCode == http.StatusUnauthorized ||
		response.StatusCode == http.StatusForbidden ||
		response.StatusCode == http.StatusUnprocessableEntity ||
		response.StatusCode == http.StatusTooManyRequests ||
		response.StatusCode >= http.StatusInternalServerError {
		statusError := readMCPHTTPStatusError(response.StatusCode, response.Body)
		_ = response.Body.Close()
		return nil, statusError
	}
	if requestID != nil && response.StatusCode == http.StatusOK {
		if err := correlateMCPResponseID(response, requestID); err != nil {
			return nil, err
		}
	}
	return response, nil
}

// mcpCorrelatedRequest buffers a JSON-RPC POST so the raw top-level id token
// can be compared against the response, and returns a replayable clone. The
// original request is returned unchanged for anything that is not a JSON-RPC
// request frame.
func mcpCorrelatedRequest(request *http.Request) (*http.Request, json.RawMessage) {
	if request.Method != http.MethodPost || request.Body == nil ||
		!strings.HasPrefix(request.Header.Get("Content-Type"), "application/json") {
		return request, nil
	}
	body, readErr := io.ReadAll(request.Body)
	_ = request.Body.Close()
	clone := request.Clone(request.Context())
	clone.Body = io.NopCloser(bytes.NewReader(body))
	clone.ContentLength = int64(len(body))
	if readErr != nil {
		return clone, nil
	}
	var fields map[string]json.RawMessage
	if json.Unmarshal(body, &fields) != nil {
		return clone, nil
	}
	if _, hasMethod := fields["method"]; !hasMethod {
		return clone, nil
	}
	rawID, hasID := fields["id"]
	if !hasID {
		return clone, nil
	}
	return clone, rawID
}

// correlateMCPResponseID verifies that the response frame carries the exact
// request identity before the SDK decodes it through float64. For JSON
// responses the check happens eagerly; for POST SSE streams the body reader
// enforces it as the response frame is consumed, so a mismatched frame never
// reaches the transport's parser.
func correlateMCPResponseID(response *http.Response, requestID json.RawMessage) error {
	contentType := strings.ToLower(response.Header.Get("Content-Type"))
	switch {
	case strings.HasPrefix(contentType, "application/json"):
		body, err := io.ReadAll(response.Body)
		_ = response.Body.Close()
		response.Body = io.NopCloser(bytes.NewReader(body))
		if err != nil {
			return nil
		}
		var fields map[string]json.RawMessage
		if json.Unmarshal(body, &fields) != nil {
			return nil
		}
		rawID, hasID := fields["id"]
		_, hasMethod := fields["method"]
		if !hasID || hasMethod {
			return nil
		}
		if !mcpIDTokensEqual(requestID, rawID) {
			return fmt.Errorf("%w: request %s, response %s", errMCPResponseIDMismatch, requestID, rawID)
		}
		return nil
	case strings.HasPrefix(contentType, "text/event-stream"):
		response.Body = &mcpSSEResponseIDReader{body: response.Body, want: requestID}
		return nil
	default:
		return nil
	}
}

// mcpIDTokensEqual compares two raw JSON-RPC id tokens without decoding
// numbers through float64: numeric tokens are compared by value (1 equals 1.0),
// while a numeric and a string token never match.
func mcpIDTokensEqual(a, b json.RawMessage) bool {
	a = bytes.TrimSpace(a)
	b = bytes.TrimSpace(b)
	if bytes.Equal(a, b) {
		return true
	}
	var an, bn json.Number
	aNumber := json.Unmarshal(a, &an) == nil
	bNumber := json.Unmarshal(b, &bn) == nil
	if aNumber != bNumber {
		return false
	}
	if !aNumber {
		return false
	}
	ar, aOK := new(big.Rat).SetString(an.String())
	br, bOK := new(big.Rat).SetString(bn.String())
	return aOK && bOK && ar.Cmp(br) == 0
}

// mcpSSEResponseIDReader enforces the raw request identity on a POST SSE
// stream. Complete events that are not the JSON-RPC response frame (id with
// result or error, no method) are released immediately so server
// notifications keep streaming; the response frame itself is released only
// when its raw id token matches the request's, and a mismatched frame is
// withheld and surfaced as a read error so the transport cannot accept it.
type mcpSSEResponseIDReader struct {
	body     io.ReadCloser
	want     json.RawMessage
	pending  []byte
	tail     []byte
	decided  bool
	mismatch error
}

func (reader *mcpSSEResponseIDReader) Read(p []byte) (int, error) {
	for {
		if len(reader.pending) > 0 {
			n := copy(p, reader.pending)
			reader.pending = reader.pending[n:]
			return n, nil
		}
		if reader.mismatch != nil {
			return 0, reader.mismatch
		}
		if reader.decided {
			return reader.body.Read(p)
		}
		var chunk [4096]byte
		n, err := reader.body.Read(chunk[:])
		if n > 0 {
			reader.tail = append(reader.tail, chunk[:n]...)
			reader.scan()
		}
		if err != nil {
			reader.decided = true
			reader.pending = append(reader.pending, reader.tail...)
			reader.tail = nil
		}
	}
}

func (reader *mcpSSEResponseIDReader) Close() error {
	return reader.body.Close()
}

func (reader *mcpSSEResponseIDReader) scan() {
	for {
		idx := bytes.Index(reader.tail, []byte("\n\n"))
		if idx < 0 {
			return
		}
		event := reader.tail[:idx]
		reader.tail = reader.tail[idx+2:]
		frame, isResponse := mcpSSEFrameResponse(event)
		matched := isResponse && mcpIDTokensEqual(reader.want, frame)
		if isResponse {
			reader.decided = true
			reader.tail = nil
			if !matched {
				reader.mismatch = fmt.Errorf(
					"%w: request %s, response %s", errMCPResponseIDMismatch, reader.want, frame)
				return
			}
		}
		reader.pending = append(reader.pending, event...)
		reader.pending = append(reader.pending, "\n\n"...)
	}
}

// mcpSSEFrameResponse returns the raw id token when a complete SSE event
// carries a JSON-RPC response frame.
func mcpSSEFrameResponse(event []byte) (json.RawMessage, bool) {
	var payload []byte
	for _, line := range bytes.Split(event, []byte("\n")) {
		line = bytes.TrimRight(line, "\r")
		if !bytes.HasPrefix(line, []byte("data:")) {
			continue
		}
		value := bytes.TrimLeft(bytes.TrimPrefix(line, []byte("data:")), " ")
		if len(payload) > 0 {
			payload = append(payload, '\n')
		}
		payload = append(payload, value...)
	}
	if len(payload) == 0 {
		return nil, false
	}
	var fields map[string]json.RawMessage
	if json.Unmarshal(payload, &fields) != nil {
		return nil, false
	}
	_, hasMethod := fields["method"]
	_, hasResult := fields["result"]
	_, hasError := fields["error"]
	rawID, hasID := fields["id"]
	if hasID && !hasMethod && (hasResult || hasError) {
		return rawID, true
	}
	return nil, false
}

type mcpHTTPErrorBody struct {
	Code    string `json:"code"`
	Message string `json:"message"`
	Hint    string `json:"hint"`
}

func readMCPHTTPStatusError(statusCode int, body io.Reader) *MCPHTTPStatusError {
	statusError := defaultMCPHTTPStatusError(statusCode)
	payload, err := io.ReadAll(io.LimitReader(body, statusErrorBodyLimit+1))
	if err != nil || len(payload) > statusErrorBodyLimit {
		return statusError
	}
	var structured mcpHTTPErrorBody
	if err := json.Unmarshal(payload, &structured); err != nil {
		return statusError
	}
	if value := strings.TrimSpace(structured.Code); value != "" {
		statusError.Code = value
	}
	if value := strings.TrimSpace(structured.Message); value != "" {
		statusError.Message = value
	}
	if value := strings.TrimSpace(structured.Hint); value != "" {
		statusError.Hint = value
	}
	return statusError
}

func defaultMCPHTTPStatusError(statusCode int) *MCPHTTPStatusError {
	statusError := &MCPHTTPStatusError{StatusCode: statusCode}
	switch statusCode {
	case http.StatusUnauthorized:
		statusError.Code = "UNAUTHENTICATED"
		statusError.Message = "MCP authentication failed."
		statusError.Hint = "Configure valid MCP credentials and retry the MCP request."
	case http.StatusForbidden:
		statusError.Code = "PERMISSION_DENIED"
		statusError.Message = "MCP request is not permitted."
		statusError.Hint = "Use a credential with the required permission."
	case http.StatusUnprocessableEntity:
		statusError.Code = "INVALID_ARGUMENT"
		statusError.Message = "MCP request is invalid."
		statusError.Hint = "Check the request arguments and selected target."
	case http.StatusTooManyRequests:
		statusError.Code = "UNAVAILABLE"
		statusError.Message = "MCP request was rate limited."
		statusError.Hint = "Wait before retrying the MCP request."
	case http.StatusBadGateway, http.StatusServiceUnavailable, http.StatusGatewayTimeout:
		statusError.Code = "UNAVAILABLE"
		statusError.Message = "MCP service is temporarily unavailable."
		statusError.Hint = "Wait before retrying the MCP request."
	default:
		if statusCode >= http.StatusInternalServerError {
			statusError.Code = "INTERNAL_ERROR"
			statusError.Message = "MCP service failed unexpectedly."
			statusError.Hint = "Retry once; if the failure persists, contact an administrator."
		}
	}
	return statusError
}

func (session *MCPClientSession) Close() error {
	// Established sessions are terminated according to MCP session management:
	// https://modelcontextprotocol.io/specification/2025-11-25/basic/transports#session-management
	var closeErr error
	session.closeOnce.Do(func() {
		close(session.closed)
		closeErr = session.transport.Close()
	})
	return closeErr
}

func (session *MCPClientSession) Notifications() <-chan json.RawMessage {
	// Server-initiated notifications are forwarded to the stdio MCP client.
	return session.notifications
}
