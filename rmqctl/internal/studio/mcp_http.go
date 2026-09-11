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
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
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
	cluster   string
	transport mcptransport.HTTPConnection
	timeout   time.Duration

	closeOnce     sync.Once
	closed        chan struct{}
	notifications chan json.RawMessage

	// sendMu allows ordinary messages to be concurrent while preventing any
	// message from entering a replacement session before reinitialization and
	// its initialized notification have completed.
	sendMu sync.RWMutex
	state  struct {
		sync.RWMutex
		initialize *mcptransport.JSONRPCRequest
		generation uint64
		ready      bool
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
		cluster:       target.Cluster,
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

func (transport mcpStatusRoundTripper) RoundTrip(request *http.Request) (*http.Response, error) {
	response, err := transport.base.RoundTrip(request)
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
	return response, nil
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
