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
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/types"
)

const DefaultTimeout = 30 * time.Second

const (
	toolCallPath = "/api/mcp/tools/call"
	mcpPath      = "/api/mcp"
)

type Target struct {
	Server     string
	InstanceID string
	Credential Credential
	Timeout    time.Duration
}

type Credential struct {
	AccessKey string
	SecretKey string
}

func (t Target) BaseURL() string {
	return strings.TrimRight(t.Server, "/")
}

func (t Target) validate() error {
	server, err := url.Parse(t.Server)
	if err != nil || server.Hostname() == "" {
		return fmt.Errorf("invalid Studio Server URL %q", t.Server)
	}
	if server.Scheme != "https" && !(server.Scheme == "http" && isPrivateHost(server.Hostname())) {
		return fmt.Errorf("studio server must use HTTPS (HTTP is allowed only for loopback or private-network addresses)")
	}
	if server.User != nil || server.RawQuery != "" || server.Fragment != "" {
		return fmt.Errorf("studio server URL must not contain user info, a query, or a fragment")
	}
	if strings.TrimSpace(t.InstanceID) == "" {
		return fmt.Errorf("studio target requires an instance id; pass --instance-id explicitly")
	}
	if t.Credential.AccessKey == "" || t.Credential.SecretKey == "" {
		return fmt.Errorf("studio target requires accessKey and secretKey")
	}
	return nil
}

type Client struct {
	httpClient *http.Client
}

type APIError struct {
	StatusCode int
	Code       string
	Message    string
	Hint       string
}

func (e *APIError) Error() string {
	return e.Message
}

// NewClient wraps an *http.Client for Studio API calls. Network operations
// validate the dependency when it is used so offline commands can still be
// constructed when an App has no HTTP client.
func NewClient(httpClient *http.Client) Client {
	return Client{httpClient: httpClient}
}

func NewHTTPClient() *http.Client {
	return &http.Client{
		CheckRedirect: noRedirectPolicy,
	}
}

func (c Client) request(ctx context.Context, target Target, method string, path string, body any, out any) error {
	signedClient, err := signedHTTPClient(c.httpClient, target)
	if err != nil {
		return err
	}
	var reader io.Reader
	if body != nil {
		payload, err := json.Marshal(body)
		if err != nil {
			return err
		}
		reader = bytes.NewReader(payload)
	}
	// A non-positive timeout must not be passed to context.WithTimeout, which would expire
	// the context immediately and fail every request. Mirror mcp_message.go and treat it as
	// "no client-side timeout".
	var cancel context.CancelFunc
	if target.Timeout > 0 {
		ctx, cancel = context.WithTimeout(ctx, target.Timeout)
	} else {
		ctx, cancel = context.WithCancel(ctx)
	}
	defer cancel()
	requestURL, err := url.JoinPath(target.BaseURL(), path)
	if err != nil {
		return err
	}
	request, err := http.NewRequestWithContext(ctx, method, requestURL, reader)
	if err != nil {
		return err
	}
	request.Header.Set("Accept", "application/json")
	if body != nil {
		request.Header.Set("Content-Type", "application/json")
	}
	response, err := signedClient.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		// The error body is server-controlled diagnostics. Read it with the
		// same bounded read the MCP transport applies (statusErrorBodyLimit):
		// a misbehaving endpoint answering with a huge HTML or plain-text blob
		// must surface as the generic HTTP status message, not be buffered
		// whole and echoed into the CLI's error output.
		payload, readErr := io.ReadAll(io.LimitReader(response.Body, statusErrorBodyLimit+1))
		if readErr != nil || len(payload) > statusErrorBodyLimit {
			return &APIError{
				StatusCode: response.StatusCode,
				Message:    fmt.Sprintf("studio server returned HTTP %d", response.StatusCode),
				Hint:       "The upstream error body could not be read within the diagnostic budget.",
			}
		}
		return responseError(response.StatusCode, payload)
	}
	data, err := io.ReadAll(response.Body)
	if err != nil {
		return err
	}
	envelope := types.ResultEnvelope{}
	if err := json.Unmarshal(data, &envelope); err != nil {
		return fmt.Errorf("invalid studio response: %w", err)
	}
	if envelope.Code != 200 {
		return responseError(envelope.Code, data)
	}
	if !hasJSONData(envelope.Data) {
		return nil
	}
	// Passthrough commands decode into an `any`, where encoding/json would represent every
	// number as float64 and silently corrupt int64 values beyond 2^53 (RocketMQ offsets and
	// timestamps). UseNumber keeps the literal; decoding into typed struct fields is unaffected.
	decoder := json.NewDecoder(bytes.NewReader(envelope.Data))
	decoder.UseNumber()
	if err := decoder.Decode(out); err != nil {
		return fmt.Errorf("invalid studio data: %w", err)
	}
	return nil
}

// responseError builds the APIError from a body that was already read within
// the diagnostic budget. Message and Hint stay server-controlled text: bound
// them anyway, so a structured envelope whose fields carry an oversized blob
// cannot smuggle the unbounded read back into the CLI's error output.
func responseError(statusCode int, data []byte) error {
	message := strings.TrimSpace(string(data))
	if message == "" {
		message = fmt.Sprintf("studio server returned HTTP %d", statusCode)
	}
	apiError := &APIError{
		StatusCode: statusCode,
		Message:    boundedDiagnostic(message),
	}
	var structured struct {
		Code    string `json:"code"`
		Message string `json:"message"`
		Hint    string `json:"hint"`
	}
	if err := json.Unmarshal(data, &structured); err == nil {
		apiError.Code = boundedDiagnostic(strings.TrimSpace(structured.Code))
		if structured.Message != "" {
			apiError.Message = boundedDiagnostic(structured.Message)
		}
		apiError.Hint = boundedDiagnostic(structured.Hint)
		return apiError
	}
	var envelope types.ResultEnvelope
	if err := json.Unmarshal(data, &envelope); err != nil {
		return apiError
	}
	if envelope.Message != "" {
		apiError.Message = boundedDiagnostic(envelope.Message)
	}
	return apiError
}

// boundedDiagnostic caps one diagnostic field, cutting on a UTF-8 boundary so
// multi-byte runes are not split. The bound matches the raw-body read: any
// single field already fits within statusErrorBodyLimit, this only stops one
// oversized structured field from growing the display.
func boundedDiagnostic(value string) string {
	value = strings.TrimSpace(value)
	if len(value) <= statusErrorBodyLimit {
		return value
	}
	end := statusErrorBodyLimit
	for end > 0 && !utf8.RuneStart(value[end]) {
		end--
	}
	return value[:end]
}

func hasJSONData(data json.RawMessage) bool {
	return len(data) > 0 && string(data) != "null"
}
