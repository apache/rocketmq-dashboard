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
	"bytes"
	"encoding/json"
	"fmt"
)

// DecodeJSON decodes a Studio payload into out. Numbers that end up in an any
// are decoded as json.Number and normalised to int64 when the literal is an
// integer that fits, so the int64 fields of the Studio contract (offsets,
// timestamps) survive the round trip instead of being rounded through float64
// above 2^53. Non-integer literals and integers too large for int64 keep the
// float64 they had before.
func DecodeJSON(data []byte, out any) error {
	target, ok := out.(*any)
	if !ok {
		return json.Unmarshal(data, out)
	}
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.UseNumber()
	var decoded any
	if err := decoder.Decode(&decoded); err != nil {
		return err
	}
	*target = normalizeJSONNumbers(decoded)
	return nil
}

// normalizeJSONNumbers rewrites the json.Number values of a decoded JSON tree
// into plain Go numbers, so downstream code keeps seeing int64 and float64
// instead of json.Number.
func normalizeJSONNumbers(value any) any {
	switch typed := value.(type) {
	case json.Number:
		if integer, err := typed.Int64(); err == nil {
			return integer
		}
		if number, err := typed.Float64(); err == nil {
			return number
		}
		return typed
	case map[string]any:
		for key, item := range typed {
			typed[key] = normalizeJSONNumbers(item)
		}
		return typed
	case []any:
		for index, item := range typed {
			typed[index] = normalizeJSONNumbers(item)
		}
		return typed
	default:
		return value
	}
}

type ResultEnvelope struct {
	Code    int             `json:"code"`
	Message string          `json:"message"`
	Data    json.RawMessage `json:"data"`
}

// MutationOutput is the result payload returned by L2/L3 tools.
type MutationOutput struct {
	Status       MutationStatus `json:"status"`
	InstanceID   string         `json:"instanceId"`
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
	// The payload is decoded again to be inspected, so it must keep integers
	// above 2^53 exact: decode the payload's any fields with json.Number and
	// normalise them back to int64/float64.
	decoder := json.NewDecoder(bytes.NewReader(payload))
	decoder.UseNumber()
	var mutation MutationOutput
	if err := decoder.Decode(&mutation); err != nil {
		return MutationOutput{}, fmt.Errorf("decode mutation output: %w", err)
	}
	mutation.Plan = normalizeJSONNumbers(mutation.Plan)
	mutation.Result = normalizeJSONNumbers(mutation.Result)
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
	CodeCanceled        = "CANCELED"
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
