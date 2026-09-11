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

import (
	"encoding/json"
	"fmt"
)

type ResultEnvelope struct {
	Code    int             `json:"code"`
	Message string          `json:"message"`
	Data    json.RawMessage `json:"data"`
}

// MutationOutput is the result payload returned by L2/L3 tools.
type MutationOutput struct {
	Status       MutationStatus `json:"status"`
	Cluster      string         `json:"cluster"`
	Plan         any            `json:"plan"`
	ConfirmToken string         `json:"confirm_token,omitempty"`
	Result       any            `json:"result,omitempty"`
}

type MutationStatus string

const (
	MutationPlanned  MutationStatus = "PLANNED"
	MutationExecuted MutationStatus = "EXECUTED"
)

func DecodeMutationOutput(value any) (MutationOutput, error) {
	payload, err := json.Marshal(value)
	if err != nil {
		return MutationOutput{}, fmt.Errorf("encode mutation output: %w", err)
	}
	var mutation MutationOutput
	if err := json.Unmarshal(payload, &mutation); err != nil {
		return MutationOutput{}, fmt.Errorf("decode mutation output: %w", err)
	}
	if mutation.Status != MutationPlanned && mutation.Status != MutationExecuted {
		return MutationOutput{}, fmt.Errorf(
			"decode mutation output: unsupported status %q", mutation.Status)
	}
	return mutation, nil
}

const (
	CodeInvalidArgument = "INVALID_ARGUMENT"
	CodeTimeout         = "TIMEOUT"
	CodeUnavailable     = "UNAVAILABLE"
	CodeCommandFailed   = "COMMAND_FAILED"
)

type CLIError struct {
	Code    string `json:"code" yaml:"code"`
	Message string `json:"message" yaml:"message"`
	Hint    string `json:"hint" yaml:"hint"`
}

func (e *CLIError) Error() string {
	return e.Message
}

func NewCLIError(code string, message string, hint string) *CLIError {
	return &CLIError{Code: code, Message: message, Hint: hint}
}

type ToolCallRequest struct {
	Name      string         `json:"name"`
	Arguments map[string]any `json:"arguments"`
}
