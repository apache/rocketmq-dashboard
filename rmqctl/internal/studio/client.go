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
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/types"
)

const DefaultServerURL = "http://127.0.0.1:8888"

type Target struct {
	Server  string
	Token   string
	Cluster string
	Timeout time.Duration
	Debug   bool
}

type Client struct {
	HTTP  *http.Client
	Debug io.Writer
}

type MCPSSESession struct {
	client   Client
	target   Target
	cancel   context.CancelFunc
	stream   *http.Response
	reader   *bufio.Reader
	endpoint string
}

func NewClient(debug io.Writer) Client {
	return Client{
		HTTP:  http.DefaultClient,
		Debug: debug,
	}
}

func (c Client) ListTools(target Target, cluster string) ([]types.ToolView, error) {
	path := "/api/ai/tools"
	if cluster != "" {
		path += "?cluster=" + url.QueryEscape(cluster)
	}
	var tools []types.ToolView
	if err := c.doJSON(target, http.MethodGet, path, nil, &tools); err != nil {
		return nil, err
	}
	return tools, nil
}

func (c Client) CallTool(
	target Target,
	name string,
	arguments map[string]any,
	dryRun bool,
	apply bool,
) (types.ToolExecutionResult, error) {
	var result types.ToolExecutionResult
	request := types.ToolCallRequest{
		Name:      name,
		Arguments: arguments,
		DryRun:    dryRun,
		Apply:     apply,
		Source:    "CLI",
	}
	if err := c.doJSON(target, http.MethodPost, "/api/ai/tools/call", request, &result); err != nil {
		return result, err
	}
	return result, nil
}

func (c Client) CallMCP(target Target, payload json.RawMessage) (json.RawMessage, bool, error) {
	ctx, cancel := context.WithTimeout(context.Background(), target.Timeout)
	defer cancel()
	request, err := http.NewRequestWithContext(ctx, http.MethodPost,
		strings.TrimRight(target.Server, "/")+mcpPath("/api/ai/mcp", target), bytes.NewReader(payload))
	if err != nil {
		return nil, false, err
	}
	request.Header.Set("Accept", "application/json")
	request.Header.Set("Content-Type", "application/json")
	if target.Token != "" {
		request.Header.Set("Authorization", "Bearer "+target.Token)
	}
	if target.Debug && c.Debug != nil {
		fmt.Fprintf(c.Debug, "%s %s\n", http.MethodPost, request.URL.String())
	}
	response, err := c.HTTP.Do(request)
	if err != nil {
		return nil, false, err
	}
	defer response.Body.Close()
	data, err := io.ReadAll(response.Body)
	if err != nil {
		return nil, false, err
	}
	if response.StatusCode == http.StatusNoContent {
		return nil, false, nil
	}
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return nil, false, fmt.Errorf("studio server returned HTTP %d: %s", response.StatusCode, string(data))
	}
	if len(data) == 0 {
		return nil, false, nil
	}
	return json.RawMessage(data), true, nil
}

func (c Client) CallMCPSSE(target Target, payload json.RawMessage) (json.RawMessage, bool, error) {
	ctx, cancel := context.WithTimeout(context.Background(), target.Timeout)
	defer cancel()
	request, err := http.NewRequestWithContext(ctx, http.MethodPost,
		strings.TrimRight(target.Server, "/")+mcpPath("/api/ai/mcp/sse", target), bytes.NewReader(payload))
	if err != nil {
		return nil, false, err
	}
	request.Header.Set("Accept", "text/event-stream")
	request.Header.Set("Content-Type", "application/json")
	if target.Token != "" {
		request.Header.Set("Authorization", "Bearer "+target.Token)
	}
	if target.Debug && c.Debug != nil {
		fmt.Fprintf(c.Debug, "%s %s\n", http.MethodPost, request.URL.String())
	}
	response, err := c.HTTP.Do(request)
	if err != nil {
		return nil, false, err
	}
	defer response.Body.Close()
	data, err := io.ReadAll(response.Body)
	if err != nil {
		return nil, false, err
	}
	if response.StatusCode == http.StatusNoContent || len(data) == 0 {
		return nil, false, nil
	}
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return nil, false, fmt.Errorf("studio server returned HTTP %d: %s", response.StatusCode, string(data))
	}
	payload, ok := mcpSSEData(data)
	if !ok {
		return nil, false, nil
	}
	return payload, true, nil
}

func (c Client) CallMCPSSESession(target Target, payload json.RawMessage) (json.RawMessage, bool, error) {
	session, err := c.OpenMCPSSESession(target)
	if err != nil {
		return nil, false, err
	}
	defer session.Close()
	return session.Call(payload)
}

func (c Client) OpenMCPSSESession(target Target) (*MCPSSESession, error) {
	ctx, cancel := context.WithCancel(context.Background())
	connectTimer := time.AfterFunc(target.Timeout, cancel)
	connect, err := http.NewRequestWithContext(ctx, http.MethodGet,
		strings.TrimRight(target.Server, "/")+mcpPath("/api/ai/mcp/sse", target), nil)
	if err != nil {
		connectTimer.Stop()
		cancel()
		return nil, err
	}
	connect.Close = true
	connect.Header.Set("Accept", "text/event-stream")
	if target.Token != "" {
		connect.Header.Set("Authorization", "Bearer "+target.Token)
	}
	if target.Debug && c.Debug != nil {
		fmt.Fprintf(c.Debug, "%s %s\n", http.MethodGet, connect.URL.String())
	}
	stream, err := c.HTTP.Do(connect)
	if err != nil {
		connectTimer.Stop()
		cancel()
		return nil, err
	}
	if stream.StatusCode < 200 || stream.StatusCode >= 300 {
		data, _ := io.ReadAll(stream.Body)
		stream.Body.Close()
		connectTimer.Stop()
		cancel()
		return nil, fmt.Errorf("studio server returned HTTP %d: %s", stream.StatusCode, string(data))
	}
	reader := bufio.NewReader(stream.Body)
	event, err := readMCPSSEEvent(reader)
	if err != nil {
		stream.Body.Close()
		connectTimer.Stop()
		cancel()
		return nil, err
	}
	if event.Name != "endpoint" || strings.TrimSpace(event.Data) == "" {
		stream.Body.Close()
		connectTimer.Stop()
		cancel()
		return nil, fmt.Errorf("MCP SSE endpoint event is missing")
	}
	connectTimer.Stop()
	return &MCPSSESession{
		client:   c,
		target:   target,
		cancel:   cancel,
		stream:   stream,
		reader:   reader,
		endpoint: strings.TrimSpace(event.Data),
	}, nil
}

func (session *MCPSSESession) Call(payload json.RawMessage) (json.RawMessage, bool, error) {
	if err := session.client.postMCPSSESessionMessage(
		session.target,
		session.endpoint,
		payload,
	); err != nil {
		return nil, false, err
	}
	if !mcpPayloadExpectsResponse(payload) {
		return nil, false, nil
	}
	event, err := readMCPSSEEvent(session.reader)
	if err != nil {
		return nil, false, err
	}
	if event.Name != "message" || strings.TrimSpace(event.Data) == "" {
		return nil, false, fmt.Errorf("MCP SSE message event is missing")
	}
	return json.RawMessage(strings.TrimSpace(event.Data)), true, nil
}

func (session *MCPSSESession) Close() {
	if session.cancel != nil {
		session.cancel()
	}
	if session.stream != nil {
		session.stream.Body.Close()
	}
}

func (c Client) postMCPSSESessionMessage(
	target Target,
	endpoint string,
	payload json.RawMessage,
) error {
	ctx, cancel := context.WithTimeout(context.Background(), target.Timeout)
	defer cancel()
	request, err := http.NewRequestWithContext(ctx, http.MethodPost,
		mcpSSESessionMessageURL(target.Server, endpoint), bytes.NewReader(payload))
	if err != nil {
		return err
	}
	request.Header.Set("Accept", "application/json")
	request.Header.Set("Content-Type", "application/json")
	if target.Token != "" {
		request.Header.Set("Authorization", "Bearer "+target.Token)
	}
	if target.Debug && c.Debug != nil {
		fmt.Fprintf(c.Debug, "%s %s\n", http.MethodPost, request.URL.String())
	}
	response, err := c.HTTP.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	data, err := io.ReadAll(response.Body)
	if err != nil {
		return err
	}
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return fmt.Errorf("studio server returned HTTP %d: %s", response.StatusCode, string(data))
	}
	return nil
}

func (c Client) doJSON(target Target, method string, path string, body any, out any) error {
	var reader io.Reader
	if body != nil {
		payload, err := json.Marshal(body)
		if err != nil {
			return err
		}
		reader = bytes.NewReader(payload)
	}
	ctx, cancel := context.WithTimeout(context.Background(), target.Timeout)
	defer cancel()
	request, err := http.NewRequestWithContext(ctx, method, strings.TrimRight(target.Server, "/")+path, reader)
	if err != nil {
		return err
	}
	request.Header.Set("Accept", "application/json")
	if body != nil {
		request.Header.Set("Content-Type", "application/json")
	}
	if target.Token != "" {
		request.Header.Set("Authorization", "Bearer "+target.Token)
	}
	if target.Debug && c.Debug != nil {
		fmt.Fprintf(c.Debug, "%s %s\n", method, request.URL.String())
	}
	response, err := c.HTTP.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	data, err := io.ReadAll(response.Body)
	if err != nil {
		return err
	}
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return fmt.Errorf("studio server returned HTTP %d: %s", response.StatusCode, string(data))
	}
	envelope := types.ResultEnvelope{}
	if err := json.Unmarshal(data, &envelope); err != nil {
		return fmt.Errorf("invalid studio response: %w", err)
	}
	if envelope.Code != 200 {
		return fmt.Errorf("studio server returned code %d: %s", envelope.Code, envelope.Message)
	}
	if len(envelope.Data) == 0 || string(envelope.Data) == "null" {
		return nil
	}
	if err := json.Unmarshal(envelope.Data, out); err != nil {
		return fmt.Errorf("invalid studio data: %w", err)
	}
	return nil
}

func mcpPath(path string, target Target) string {
	if target.Cluster != "" {
		return path + "?cluster=" + url.QueryEscape(target.Cluster)
	}
	return path
}

func mcpSSEData(data []byte) (json.RawMessage, bool) {
	var payloadLines []string
	normalized := strings.ReplaceAll(string(data), "\r\n", "\n")
	for _, line := range strings.Split(normalized, "\n") {
		if strings.HasPrefix(line, "data:") {
			payloadLines = append(payloadLines, strings.TrimSpace(strings.TrimPrefix(line, "data:")))
		}
	}
	if len(payloadLines) == 0 {
		return nil, false
	}
	payload := strings.TrimSpace(strings.Join(payloadLines, "\n"))
	if payload == "" {
		return nil, false
	}
	return json.RawMessage(payload), true
}

type mcpSSEEvent struct {
	Name string
	Data string
}

func readMCPSSEEvent(reader *bufio.Reader) (mcpSSEEvent, error) {
	event := mcpSSEEvent{Name: "message"}
	var dataLines []string
	seenField := false
	for {
		line, err := reader.ReadString('\n')
		if err != nil && line == "" {
			if seenField {
				event.Data = strings.Join(dataLines, "\n")
				return event, nil
			}
			return mcpSSEEvent{}, fmt.Errorf("MCP SSE stream ended before event: %w", err)
		}
		line = strings.TrimRight(line, "\r\n")
		switch {
		case line == "":
			if seenField {
				event.Data = strings.Join(dataLines, "\n")
				return event, nil
			}
		case strings.HasPrefix(line, ":"):
		case strings.HasPrefix(line, "event:"):
			seenField = true
			event.Name = strings.TrimSpace(strings.TrimPrefix(line, "event:"))
		case strings.HasPrefix(line, "data:"):
			seenField = true
			dataLines = append(dataLines, strings.TrimSpace(strings.TrimPrefix(line, "data:")))
		}
		if err != nil && seenField {
			event.Data = strings.Join(dataLines, "\n")
			return event, nil
		}
	}
}

func mcpSSESessionMessageURL(server string, endpoint string) string {
	endpoint = strings.TrimSpace(endpoint)
	if parsed, err := url.Parse(endpoint); err == nil && parsed.IsAbs() {
		return endpoint
	}
	if !strings.HasPrefix(endpoint, "/") {
		endpoint = "/" + endpoint
	}
	return strings.TrimRight(server, "/") + endpoint
}

func mcpPayloadExpectsResponse(payload json.RawMessage) bool {
	var request struct {
		ID     *json.RawMessage `json:"id"`
		Method string           `json:"method"`
	}
	if err := json.Unmarshal(payload, &request); err != nil {
		return true
	}
	return request.ID != nil || !strings.HasPrefix(request.Method, "notifications/")
}
