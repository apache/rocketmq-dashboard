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
package types

import "encoding/json"

type ResultEnvelope struct {
	Code    int             `json:"code"`
	Message string          `json:"message"`
	Data    json.RawMessage `json:"data"`
}

type ToolView struct {
	Name           string         `json:"name"`
	Version        string         `json:"version"`
	Description    string         `json:"description"`
	RiskLevel      string         `json:"riskLevel"`
	OperationLevel string         `json:"operationLevel"`
	Permission     string         `json:"permission"`
	Implemented    bool           `json:"implemented"`
	Deprecated     bool           `json:"deprecated"`
	CLI            map[string]any `json:"cli"`
}

type ToolExecutionResult struct {
	RequestID      string `json:"requestId"`
	ToolName       string `json:"toolName"`
	Source         string `json:"source"`
	OperationLevel string `json:"operationLevel"`
	DryRun         bool   `json:"dryRun"`
	Executed       bool   `json:"executed"`
	ErrorCode      string `json:"errorCode,omitempty"`
	Message        string `json:"message"`
	Result         any    `json:"result,omitempty"`
}

type ToolCallRequest struct {
	Name      string         `json:"name"`
	Arguments map[string]any `json:"arguments"`
	DryRun    bool           `json:"dryRun,omitempty"`
	Apply     bool           `json:"apply,omitempty"`
	Source    string         `json:"source"`
}
