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
	"errors"
	"fmt"
	"log/slog"

	mcptransport "github.com/mark3labs/mcp-go/client/transport"
	"github.com/mark3labs/mcp-go/mcp"
)

// SendMessage sends a JSON-RPC request or notification to Studio Server.
// For requests, it returns the response payload and true. For notifications,
// it returns nil and false. It automatically reinitializes the session on
// 404/session-terminated errors.
func (session *MCPClientSession) SendMessage(ctx context.Context, payload json.RawMessage) (json.RawMessage, bool, error) {
	var bindErr error
	payload, bindErr = bindToolInstance(payload, session.cluster)
	if bindErr != nil {
		return nil, false, bindErr
	}
	// Client-originated messages follow the Streamable HTTP POST rules:
	// https://modelcontextprotocol.io/specification/2025-11-25/basic/transports#sending-messages-to-the-server
	if err := session.transport.Start(ctx); err != nil {
		return nil, false, err
	}
	decoded, err := decodeMCPMessage(payload)
	if err != nil {
		return nil, false, err
	}
	callCtx := ctx
	cancel := func() {}
	if session.timeout > 0 {
		callCtx, cancel = context.WithTimeout(ctx, session.timeout)
	}
	defer cancel()

	if decoded.request != nil {
		return session.sendRequest(callCtx, *decoded.request)
	}
	return nil, false, session.sendNotification(callCtx, *decoded.notification)
}

type decodedMCPMessage struct {
	request      *mcptransport.JSONRPCRequest
	notification *mcp.JSONRPCNotification
}

func decodeMCPMessage(payload json.RawMessage) (decodedMCPMessage, error) {
	var fields map[string]json.RawMessage
	if err := json.Unmarshal(payload, &fields); err != nil {
		return decodedMCPMessage{}, fmt.Errorf("invalid MCP JSON-RPC message: %w", err)
	}
	if fields == nil {
		return decodedMCPMessage{}, fmt.Errorf("invalid MCP JSON-RPC message: expected object")
	}
	_, hasMethod := fields["method"]
	_, hasID := fields["id"]
	if hasMethod && hasID {
		var request mcptransport.JSONRPCRequest
		if err := json.Unmarshal(payload, &request); err != nil {
			return decodedMCPMessage{}, fmt.Errorf("invalid MCP JSON-RPC request: %w", err)
		}
		return decodedMCPMessage{request: &request}, nil
	}
	if hasMethod {
		var notification mcp.JSONRPCNotification
		if err := json.Unmarshal(payload, &notification); err != nil {
			return decodedMCPMessage{}, fmt.Errorf("invalid MCP JSON-RPC notification: %w", err)
		}
		return decodedMCPMessage{notification: &notification}, nil
	}
	return decodedMCPMessage{}, fmt.Errorf("invalid MCP JSON-RPC message: missing method")
}

func (session *MCPClientSession) sendRequest(
	ctx context.Context,
	request mcptransport.JSONRPCRequest,
) (json.RawMessage, bool, error) {
	var response *mcptransport.JSONRPCResponse
	err := session.sendWithReconnect(ctx, request.Method == string(mcp.MethodInitialize), func() error {
		var sendErr error
		response, sendErr = session.transport.SendRequest(ctx, request)
		return sendErr
	})
	if err != nil {
		return nil, false, err
	}
	if response == nil {
		return nil, false, fmt.Errorf("empty MCP JSON-RPC response")
	}
	if response.ID.String() != request.ID.String() {
		return nil, false, fmt.Errorf(
			"MCP JSON-RPC response id %s does not match request id %s",
			response.ID.String(), request.ID.String())
	}
	if request.Method == string(mcp.MethodInitialize) && response.Error == nil {
		if err := session.recordInitialization(request, response); err != nil {
			return nil, false, err
		}
	}
	payload, err := json.Marshal(response)
	if err != nil {
		return nil, false, fmt.Errorf("encode MCP JSON-RPC response: %w", err)
	}
	return payload, true, nil
}

func (session *MCPClientSession) sendNotification(
	ctx context.Context,
	notification mcp.JSONRPCNotification,
) error {
	// The initialized notification is mandatory after a successful initialize
	// exchange; other client notifications use the same HTTP POST path.
	// MCP 2025-11-25: https://modelcontextprotocol.io/specification/2025-11-25/basic/lifecycle#initialization
	err := session.sendWithReconnect(ctx, false, func() error {
		return session.transport.SendNotification(ctx, notification)
	})
	if err == nil && notification.Method == string(mcp.MethodNotificationInitialized) {
		session.state.Lock()
		session.state.ready = true
		session.state.Unlock()
	}
	return err
}

func (session *MCPClientSession) sendWithReconnect(ctx context.Context, skipReconnect bool, send func() error) error {
	session.sendMu.RLock()
	generation, hadSession := session.sendSnapshot()
	err := send()
	session.sendMu.RUnlock()
	if err != nil && hadSession && !skipReconnect && errors.Is(err, mcptransport.ErrSessionTerminated) {
		if reconnectErr := session.reinitialize(ctx, generation); reconnectErr != nil {
			return reconnectErr
		}
		session.sendMu.RLock()
		err = send()
		session.sendMu.RUnlock()
	}
	return err
}

// sendSnapshot returns the current session generation and whether a session
// id is already established. Callers must hold sendMu (at least RLock) so the
// snapshot is consistent with the transport state used for the send.
func (session *MCPClientSession) sendSnapshot() (uint64, bool) {
	session.state.RLock()
	generation := session.state.generation
	session.state.RUnlock()
	return generation, session.transport.GetSessionId() != ""
}

func (session *MCPClientSession) recordInitialization(
	request mcptransport.JSONRPCRequest,
	response *mcptransport.JSONRPCResponse,
) error {
	if err := session.applyInitialization(response); err != nil {
		return err
	}
	session.state.Lock()
	session.state.initialize = new(request)
	session.state.ready = false
	session.state.Unlock()
	return nil
}

func (session *MCPClientSession) applyInitialization(response *mcptransport.JSONRPCResponse) error {
	// Subsequent HTTP requests must carry the protocol version negotiated here.
	// MCP 2025-11-25: https://modelcontextprotocol.io/specification/2025-11-25/basic/transports#protocol-version-header
	protocolVersion, err := initializationProtocolVersion(response)
	if err != nil {
		return err
	}
	if protocolVersion != "" {
		session.transport.SetProtocolVersion(protocolVersion)
	}
	session.state.Lock()
	session.state.generation++
	session.state.Unlock()
	return nil
}

func initializationProtocolVersion(response *mcptransport.JSONRPCResponse) (string, error) {
	var result struct {
		ProtocolVersion string `json:"protocolVersion"`
	}
	if err := json.Unmarshal(response.Result, &result); err != nil {
		return "", fmt.Errorf("decode MCP initialize result: %w", err)
	}
	return result.ProtocolVersion, nil
}

func (session *MCPClientSession) reinitialize(ctx context.Context, expectedGeneration uint64) error {
	// A 404 for an established session requires a fresh initialize exchange.
	// MCP 2025-11-25: https://modelcontextprotocol.io/specification/2025-11-25/basic/transports#session-management
	session.sendMu.Lock()
	defer session.sendMu.Unlock()
	session.state.RLock()
	if session.state.generation != expectedGeneration {
		session.state.RUnlock()
		return nil
	}
	initialize := session.state.initialize
	session.state.RUnlock()
	if initialize == nil {
		return fmt.Errorf("MCP session terminated before initialization could be replayed")
	}
	response, err := session.transport.SendRequest(ctx, *initialize)
	if err != nil {
		return fmt.Errorf("reinitialize MCP session: %w", err)
	}
	if response == nil || response.Error != nil {
		return fmt.Errorf("reinitialize MCP session: initialize failed")
	}
	if err := session.applyInitialization(response); err != nil {
		return err
	}
	// The session is freshly established, so the initialized notification must
	// always be replayed regardless of the pre-reconnect ready snapshot.
	initialized := mcp.JSONRPCNotification{
		JSONRPC: mcp.JSONRPC_VERSION,
		Notification: mcp.Notification{
			Method: string(mcp.MethodNotificationInitialized),
		},
	}
	if err := session.transport.SendNotification(ctx, initialized); err != nil {
		return fmt.Errorf("reinitialize MCP session: send initialized notification: %w", err)
	}
	session.state.Lock()
	session.state.ready = true
	session.state.Unlock()
	return nil
}

func (session *MCPClientSession) forwardNotification(notification mcp.JSONRPCNotification) {
	// A POST SSE response may carry related server notifications before the
	// JSON-RPC response for the originating request.
	// MCP 2025-11-25: https://modelcontextprotocol.io/specification/2025-11-25/basic/transports#sending-messages-to-the-server
	payload, err := json.Marshal(notification)
	if err != nil {
		return
	}
	session.enqueueNotification(payload)
}

func (session *MCPClientSession) enqueueNotification(payload json.RawMessage) {
	select {
	case session.notifications <- payload:
	case <-session.closed:
	default:
		slog.Warn("MCP notification dropped: notification buffer is full",
			"bufferSize", notificationBufferSize)
	}
}

// bindToolInstance fills only an absent tools/call argument. Explicit values remain subject
// to the server's canonical Instance identity check against X-RMQ-Cluster.
func bindToolInstance(payload json.RawMessage, cluster string) (json.RawMessage, error) {
	var message map[string]json.RawMessage
	if err := json.Unmarshal(payload, &message); err != nil {
		return nil, err
	}
	var method string
	if err := json.Unmarshal(message["method"], &method); err != nil || method != "tools/call" || cluster == "" {
		return payload, nil
	}
	var params map[string]json.RawMessage
	if err := json.Unmarshal(message["params"], &params); err != nil || params == nil {
		return payload, nil
	}
	arguments := map[string]json.RawMessage{}
	if raw, ok := params["arguments"]; ok {
		if err := json.Unmarshal(raw, &arguments); err != nil || arguments == nil {
			return payload, nil
		}
	}
	if _, exists := arguments["cluster"]; exists {
		return payload, nil
	}
	arguments["cluster"], _ = json.Marshal(cluster)
	params["arguments"], _ = json.Marshal(arguments)
	message["params"], _ = json.Marshal(params)
	return json.Marshal(message)
}
