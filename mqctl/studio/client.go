// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Package studio is a client for RocketMQ Studio over /api/ai.
package studio

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

type Result[T any] struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
	Data    T      `json:"data"`
}

const successCode = 200

type ToolVO struct {
	Name               string   `json:"name"`
	Description        string   `json:"description"`
	Parameters         any      `json:"parameters"`
	RiskLevel          string   `json:"riskLevel"`
	Permission         string   `json:"permission"`
	RequiredCapability []string `json:"requiredCapabilities"`
	OutputSchema       any      `json:"outputSchema"`
	ViewHint           string   `json:"viewHint"`
	Deprecated         bool     `json:"deprecated"`
	Replacement        string   `json:"replacement"`
}

type Client struct {
	BaseURL    string
	HTTPClient *http.Client
}

func New(baseURL string) *Client {
	return &Client{
		BaseURL:    strings.TrimRight(baseURL, "/"),
		HTTPClient: &http.Client{Timeout: 30 * time.Second},
	}
}

func (c *Client) ListTools(cluster string) ([]ToolVO, error) {
	u := c.BaseURL + "/api/ai/tools"
	if cluster != "" {
		q := url.Values{}
		q.Set("cluster", cluster)
		u = u + "?" + q.Encode()
	}
	resp, err := c.HTTPClient.Get(u)
	if err != nil {
		return nil, fmt.Errorf("list tools: %w", err)
	}
	defer resp.Body.Close()
	var res Result[[]ToolVO]
	if err := decodeBody(resp, &res); err != nil {
		return nil, err
	}
	if res.Code != successCode {
		return nil, fmt.Errorf("list tools: studio error %d: %s", res.Code, res.Message)
	}
	return res.Data, nil
}

func (c *Client) ExecuteTool(name string, input map[string]any) (any, error) {
	body, err := json.Marshal(input)
	if err != nil {
		return nil, fmt.Errorf("execute %s: marshal input: %w", name, err)
	}
	u := c.BaseURL + "/api/ai/tools/" + url.PathEscape(name) + "/execute"
	req, err := http.NewRequest(http.MethodPost, u, bytes.NewReader(body))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("execute %s: %w", name, err)
	}
	defer resp.Body.Close()
	var res Result[any]
	if err := decodeBody(resp, &res); err != nil {
		return nil, err
	}
	if res.Code != successCode {
		return nil, fmt.Errorf("execute %s: studio error %d: %s", name, res.Code, res.Message)
	}
	return res.Data, nil
}

func decodeBody(resp *http.Response, dst any) error {
	if resp.StatusCode >= 400 {
		b, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("http %d: %s", resp.StatusCode, strings.TrimSpace(string(b)))
	}
	if err := json.NewDecoder(resp.Body).Decode(dst); err != nil {
		return fmt.Errorf("decode response: %w", err)
	}
	return nil
}
