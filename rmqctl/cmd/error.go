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
package cmd

import (
	"context"
	"errors"
	"fmt"
	"net"
	"strings"

	"github.com/apache/rocketmq-dashboard/rmqctl/internal/output"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/studio"
	"github.com/apache/rocketmq-dashboard/rmqctl/internal/types"
)

func invalidArgument(message string) error {
	return types.NewCLIError(
		types.CodeInvalidArgument,
		message,
		"Check the command flags and required arguments with --help.")
}

func (a *App) writeCommandError(format string, err error) {
	normalized := normalizeCLIError(err)
	cliError := &types.CLIError{
		Code:    safeDiagnosticText(normalized.Code),
		Message: safeDiagnosticText(normalized.Message),
		Hint:    safeDiagnosticText(normalized.Hint),
	}
	if strings.TrimSpace(cliError.Code) == "" {
		cliError.Code = types.CodeCommandFailed
	}
	if strings.TrimSpace(cliError.Hint) == "" {
		cliError.Hint = "Review the command arguments and current context."
	}
	if output.IsStructured(format) {
		_ = output.Structured(a.Err, format, cliError)
		return
	}
	fmt.Fprintf(a.Err, "error [%s]: %s\n", cliError.Code, cliError.Message)
	fmt.Fprintln(a.Err, "hint:", cliError.Hint)
}

type errorMatcher func(err error) (*types.CLIError, bool)

var errorMatchers = []errorMatcher{
	matchCLIError,
	matchAPIError,
	matchContextDeadline,
	matchNetError,
}

func matchCLIError(err error) (*types.CLIError, bool) {
	return errors.AsType[*types.CLIError](err)
}

func matchAPIError(err error) (*types.CLIError, bool) {
	apiError, ok := errors.AsType[*studio.APIError](err)
	if !ok {
		return nil, false
	}
	code, hint := httpErrorDetails(apiError.StatusCode)
	if strings.TrimSpace(apiError.Code) != "" {
		code = apiError.Code
	}
	if strings.TrimSpace(apiError.Hint) != "" {
		hint = apiError.Hint
	}
	return types.NewCLIError(code, apiError.Message, hint), true
}

func matchContextDeadline(err error) (*types.CLIError, bool) {
	if !errors.Is(err, context.DeadlineExceeded) {
		return nil, false
	}
	return types.NewCLIError(
		types.CodeTimeout,
		err.Error(),
		"Increase --timeout or check Studio Server availability."), true
}

func matchNetError(err error) (*types.CLIError, bool) {
	if _, ok := errors.AsType[net.Error](err); !ok {
		return nil, false
	}
	return types.NewCLIError(
		types.CodeUnavailable,
		err.Error(),
		"Check --server, network connectivity, and Studio Server status."), true
}

func normalizeCLIError(err error) *types.CLIError {
	if err == nil {
		return nil
	}
	for _, match := range errorMatchers {
		if cliError, ok := match(err); ok {
			return cliError
		}
	}
	return types.NewCLIError(
		types.CodeCommandFailed,
		err.Error(),
		"Review the command arguments and current context, or run the command with --help.")
}

func httpErrorDetails(statusCode int) (string, string) {
	code := fmt.Sprintf("HTTP_%d", statusCode)
	switch statusCode {
	case 400, 422:
		return code, "Check the command arguments and selected context."
	case 401:
		return code, "Check the configured credential and system clock."
	case 403:
		return code, "Check the current credential and required permission."
	case 404:
		return code, "Verify the resource name and selected cluster."
	case 409:
		return code, "Refresh the resource state and retry."
	case 429:
		return code, "Wait before retrying the request."
	default:
		if statusCode >= 500 {
			return code, "Check RocketMQ Studio and the downstream service, then retry."
		}
		return code, "Check the command arguments and Studio Server response."
	}
}
