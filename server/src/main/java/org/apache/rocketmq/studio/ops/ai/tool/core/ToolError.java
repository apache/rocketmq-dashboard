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
package org.apache.rocketmq.studio.ops.ai.tool.core;

import org.springframework.http.HttpStatus;

/**
 * Tool error scenarios and their HTTP status, public error code and recovery hint.
 * Multiple scenarios may share the same public code.
 */
public enum ToolError {

    TOKEN_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "UNAVAILABLE",
            "Tool confirmation tokens are unavailable because studio.ai.token-secret is not configured.",
            "Configure studio.ai.token-secret or STUDIO_AI_TOKEN_SECRET with a base64-encoded key of at least 32 bytes."),

    L3_DISABLED(HttpStatus.FORBIDDEN, "PERMISSION_DENIED",
            "L3 tools are disabled by server policy.",
            "Ask an administrator to enable studio.ai.allow-l3-tools before retrying."),

    L3_APPROVAL_REQUIRED(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT",
            "L3 tool is blocked by default and requires break_glass=true before execution: %s",
            "Set break_glass=true after reviewing the preview plan."),

    L3_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT",
            "Tool requires a non-empty reason before L3 execution: %s",
            "Provide a non-empty reason that explains the L3 operation."),

    CONFIRMATION_TOKEN_REQUIRED(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT",
            "Tool requires confirm_token from a matching preview: %s",
            "Run the tool with --dry-run, then retry without --dry-run and with the returned confirm_token."),

    CONFIRMATION_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT",
            "Tool confirm_token is invalid, expired, or does not match the tool, caller, Instance or preview input. Tool: %s",
            "Run with --dry-run again and retry with its fresh token without changing the operation input."),

    TOOL_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT",
            "Tool name is required",
            "Call tools/list and provide one of the returned tool names."),

    TOOL_NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND",
            "Tool not found: %s",
            "Call tools/list to discover the available tool names."),

    CLUSTER_TYPE_UNAVAILABLE(HttpStatus.BAD_REQUEST, "TOOL_CAPABILITY_UNSUPPORTED",
            "Cluster type is unavailable: %s",
            "Refresh the cluster metadata or select a cluster with a known type."),

    CAPABILITY_CLUSTER_REQUIRED(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT",
            "Capability lookup requires a cluster",
            "Select a cluster and retry the tool call."),

    INSTANCE_NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND",
            "Instance not found: %s",
            "List the configured Instances and select an existing Instance."),

    TOOL_CLUSTER_REQUIRED(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT",
            "cluster must be a non-empty target name",
            "Set cluster to a registered instance name or a configured physical cluster name."),

    TOOL_TARGET_MISMATCH(HttpStatus.FORBIDDEN, "PERMISSION_DENIED",
            "cluster does not match the authenticated cluster",
            "Use the same cluster for authentication and tool arguments."),

    TOOL_CAPABILITY_UNSUPPORTED(HttpStatus.BAD_REQUEST, "TOOL_CAPABILITY_UNSUPPORTED",
            "Instance target does not support tool: %s. Call rmq.capabilities or select another target.",
            "Call rmq.capabilities for the selected Instance or select another target."),

    ADMIN_REQUIRED(HttpStatus.FORBIDDEN, "PERMISSION_DENIED",
            "Admin permission required for tool: %s",
            "Log in as an admin user, or use a low-risk read-only tool that does not require admin permission."),

    INPUT_VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT",
            "Tool input validation failed for %s: %s",
            "Correct the reported fields according to the tool input schema and retry."),

    UNEXPECTED_EXECUTION_FAILURE(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
            "Tool execution failed unexpectedly.",
            "Retry once; if the failure persists, contact an administrator."),

    ACL_ID_INVALID(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT",
            "ACL id must be numeric: %s",
            "Use the numeric ACL id returned by rmq.acl.list."),

    ROLE_NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND",
            "Role not found: %s",
            "List the roles for the selected Instance and choose an existing role."),

    DLQ_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND",
            "DLQ group not found: %s",
            "Call rmq.dlq.list and select a group with retained dead-letter messages."),

    DLQ_STATISTICS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "UNAVAILABLE",
            "DLQ statistics are unavailable for group: %s",
            "Restore broker connectivity and preview again before clearing the DLQ."),

    OFFSET_RESET_PREVIEW_UNSAFE(HttpStatus.CONFLICT, "CONFLICT",
            "Consumer offset reset preview is incomplete or unsafe for group: %s",
            "Inspect the preview warnings and target, correct them, then preview again."),

    RESEND_TARGET_TOPIC_REQUIRED(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT",
            "target topic is required when the source message has no topic",
            "Provide targetTopic explicitly and retry the preview."),

    PROXY_NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND",
            "Proxy not found in cluster %s: %s",
            "Call rmq.proxy.list and select an address returned for this cluster."),

    MESSAGE_PROPERTIES_INVALID(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT",
            "properties must be a JSON object string of string key-value pairs",
            "Provide properties as a JSON string such as {\"key1\":\"value1\"} and retry."),

    TOOL_PAYLOAD_INVALID(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT",
            "%s",
            "Correct the tool name or arguments and retry the tool call."),

    TOOL_CALL_REQUIRED(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT",
            "Tool call request is required",
            "Provide a JSON object containing name and arguments."),

    MCP_AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
            "MCP authentication is required",
            "Configure valid MCP credentials and retry the tool call."),

    MCP_AUTHENTICATION_CONTEXT_MISSING(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
            "MCP transport authentication context is missing.",
            "Configure valid MCP credentials and retry the tool call."),

    MCP_AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
            "%s",
            "Configure valid MCP credentials and retry the MCP request."),

    MCP_AUTHENTICATION_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
            "MCP authentication failed unexpectedly.",
            "Retry once; if the failure persists, contact an administrator."),

    REQUEST_BODY_INVALID(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT",
            "Tool request body is invalid.",
            "Correct the tool request and retry."),

    REQUEST_PARAMETER_REQUIRED(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT",
            "Missing required tool parameter: %s",
            "Correct the tool request and retry."),

    REQUEST_PARAMETER_INVALID(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT",
            "Invalid tool parameter: %s",
            "Correct the tool request and retry."),

    EXECUTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "TOOL_EXECUTION_FAILED",
            "%s",
            "Review the failure message; no specific recovery action is available.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String messageTemplate;
    private final String hint;

    ToolError(HttpStatus httpStatus, String code, String messageTemplate, String hint) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.messageTemplate = messageTemplate;
        this.hint = hint;
    }

    public ToolExecutionException exception(Object... arguments) {
        return new ToolExecutionException(httpStatus, code, message(arguments), hint);
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String code() {
        return code;
    }

    public String message(Object... arguments) {
        return messageTemplate.formatted(arguments);
    }

    public String hint() {
        return hint;
    }
}
