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

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/types"
)

const DefaultTimeout = 30 * time.Second

const (
	toolCallPath = "/api/mcp/tools/call"
	mcpPath      = "/api/mcp"
)

type Target struct {
	Server     string
	Cluster    string
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
	if server.Scheme != "https" && !(server.Scheme == "http" && isLoopbackHost(server.Hostname())) {
		return fmt.Errorf("studio server must use HTTPS (HTTP is allowed only for loopback development)")
	}
	if server.User != nil || server.RawQuery != "" || server.Fragment != "" {
		return fmt.Errorf("studio server URL must not contain user info, a query, or a fragment")
	}
	if strings.TrimSpace(t.Cluster) == "" {
		return fmt.Errorf("studio target requires cluster")
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
	ctx, cancel := context.WithTimeout(ctx, target.Timeout)
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
	data, err := io.ReadAll(response.Body)
	if err != nil {
		return err
	}
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return responseError(response.StatusCode, data)
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
	if err := json.Unmarshal(envelope.Data, out); err != nil {
		return fmt.Errorf("invalid studio data: %w", err)
	}
	return nil
}

func responseError(statusCode int, data []byte) error {
	message := strings.TrimSpace(string(data))
	if message == "" {
		message = fmt.Sprintf("studio server returned HTTP %d", statusCode)
	}
	apiError := &APIError{
		StatusCode: statusCode,
		Message:    message,
	}
	var structured struct {
		Code    string `json:"code"`
		Message string `json:"message"`
		Hint    string `json:"hint"`
	}
	if err := json.Unmarshal(data, &structured); err == nil {
		apiError.Code = structured.Code
		if structured.Message != "" {
			apiError.Message = structured.Message
		}
		apiError.Hint = structured.Hint
		return apiError
	}
	var envelope types.ResultEnvelope
	if err := json.Unmarshal(data, &envelope); err != nil {
		return apiError
	}
	if envelope.Message != "" {
		apiError.Message = envelope.Message
	}
	return apiError
}

func hasJSONData(data json.RawMessage) bool {
	return len(data) > 0 && string(data) != "null"
}
