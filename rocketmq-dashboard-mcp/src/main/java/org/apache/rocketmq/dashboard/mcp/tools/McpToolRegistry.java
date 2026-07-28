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
package org.apache.rocketmq.dashboard.mcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.rocketmq.dashboard.cli.executor.ToolExecutor;
import org.apache.rocketmq.dashboard.cli.schema.ParamSchema;
import org.apache.rocketmq.dashboard.cli.schema.ToolDefinition;
import org.apache.rocketmq.dashboard.cli.schema.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP-facing tool registry that loads ToolDefinitions from the CLI's
 * {@link ToolRegistry} singleton as the single source of truth, and exposes
 * them via MCP-compliant {@code tools/list} and {@code tools/call} methods.
 */
public class McpToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpToolRegistry.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecurityGate securityGate;
    private final ToolExecutor toolExecutor;

    /**
     * Creates a registry backed by a real {@link ToolExecutor}: tools/call
     * executes allowed operations against the cluster via MQAdminExt.
     */
    public McpToolRegistry(SecurityGate securityGate) {
        this(securityGate, new ToolExecutor());
    }

    /**
     * Creates a registry with an explicit executor, mainly for tests that
     * inject a stub {@link ToolExecutor}.
     */
    public McpToolRegistry(SecurityGate securityGate, ToolExecutor toolExecutor) {
        this.securityGate = securityGate;
        this.toolExecutor = toolExecutor;
    }

    /**
     * Build an MCP-compliant JSON array of all tool definitions.
     * Each entry includes: name, description, inputSchema.
     *
     * @return JSON string for the tools/list response
     */
    public String handleToolsList() {
        List<ToolDefinition> tools = ToolRegistry.getInstance().getAllTools();
        List<Map<String, Object>> toolList = new ArrayList<>();

        for (ToolDefinition tool : tools) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", tool.getMcpToolName());
            entry.put("description", tool.getDescription());

            // Build inputSchema
            Map<String, Object> inputSchema = new LinkedHashMap<>();
            inputSchema.put("type", "object");

            Map<String, Object> properties = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();

            if (tool.getParams() != null) {
                for (ParamSchema param : tool.getParams()) {
                    Map<String, Object> prop = new LinkedHashMap<>();
                    prop.put("type", mapType(param.getType()));
                    prop.put("description", param.getDescription());
                    if (param.getDefaultValue() != null) {
                        prop.put("default", param.getDefaultValue());
                    }
                    if (param.getAllowedValues() != null && param.getAllowedValues().length > 0) {
                        prop.put("enum", param.getAllowedValues());
                    }
                    properties.put(param.getName(), prop);

                    if (param.isRequired()) {
                        required.add(param.getName());
                    }
                }
            }

            inputSchema.put("properties", properties);
            if (!required.isEmpty()) {
                inputSchema.put("required", required);
            }

            entry.put("inputSchema", inputSchema);
            toolList.add(entry);
        }

        try {
            return objectMapper.writeValueAsString(toolList);
        } catch (JsonProcessingException e) {
            log.error("Error serializing tools list: {}", e.getMessage(), e);
            return "[]";
        }
    }

    /**
     * Dispatch a tool call by name with the given arguments.
     * Applies security gate checks based on the tool's risk level.
     *
     * @param toolName  the MCP tool name (e.g. "rmq.topic.list")
     * @param arguments the arguments map from the JSON-RPC request
     * @return JSON string with the result, formatted for the MCP response
     */
    @SuppressWarnings("unchecked")
    public String handleToolsCall(String toolName, Map<String, Object> arguments) {
        // Resolve tool name: accept both "rmq.topic.list" and "rmq.topic.list"
        ToolDefinition tool = ToolRegistry.getInstance().getTool(toolName);
        if (tool == null) {
            // Try with underscores-to-hyphens conversion
            String nameWithHyphens = toolName.replace("_", "-");
            tool = ToolRegistry.getInstance().getTool(nameWithHyphens);
        }

        if (tool == null) {
            return buildErrorResult("Tool not found: " + toolName, "TOOL_NOT_FOUND");
        }

        // Security check
        SecurityCheckResult checkResult = securityGate.check(tool);

        switch (checkResult.getAction()) {
            case ALLOW:
                return buildLiveResult(tool, arguments);

            case DRY_RUN:
                // A confirmed dry-run executes for real (mirrors CLI --confirm)
                if (isConfirmed(arguments)) {
                    return buildLiveResult(tool, arguments);
                }
                return buildDryRunResult(tool, arguments, checkResult);

            case BLOCK:
                return buildBlockedResult(tool, checkResult);

            default:
                return buildErrorResult("Unknown security action", "SECURITY_ERROR");
        }
    }

    private boolean isConfirmed(Map<String, Object> arguments) {
        if (arguments == null) {
            return false;
        }
        Object confirm = arguments.get("confirm");
        return Boolean.TRUE.equals(confirm) || "true".equalsIgnoreCase(String.valueOf(confirm));
    }

    /**
     * Execute the tool against the live cluster through the shared
     * {@link ToolExecutor} and wrap the outcome in the MCP result envelope.
     * Execution failures surface as explicit errors instead of silently
     * falling back to mock data.
     */
    private String buildLiveResult(ToolDefinition tool, Map<String, Object> arguments) {
        if (toolExecutor == null) {
            return buildErrorResult("No tool executor configured", "EXECUTION_ERROR");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", tool.getMcpToolName());
        result.put("resource", tool.getResource());
        result.put("verb", tool.getVerb());
        result.put("riskLevel", tool.getRiskLevel().name());

        try {
            Object data = toolExecutor.execute(tool, arguments);
            result.put("status", "success");
            result.put("live", true);
            result.put("data", data != null ? data : "OK");
        } catch (UnsupportedOperationException e) {
            log.warn("Tool {} not supported for live execution: {}", tool.getName(), e.getMessage());
            return buildErrorResult(e.getMessage(), "UNSUPPORTED");
        } catch (Exception e) {
            log.error("Live execution failed for tool {}: {}", tool.getName(), e.getMessage(), e);
            return buildErrorResult("Execution failed: " + e.getMessage(), "EXECUTION_ERROR");
        }

        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            log.error("Error serializing live result: {}", e.getMessage(), e);
            return "{}";
        }
    }

    /**
     * Build a dry-run result for L2 (controlled mutation) operations.
     * Returns a preview of what would happen without actually executing.
     */
    private String buildDryRunResult(ToolDefinition tool, Map<String, Object> arguments,
                                      SecurityCheckResult checkResult) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", tool.getMcpToolName());
        result.put("resource", tool.getResource());
        result.put("verb", tool.getVerb());
        result.put("riskLevel", tool.getRiskLevel().name());
        result.put("status", "dry_run");
        result.put("message", checkResult.getMessage());

        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("operation", tool.getMcpToolName());
        preview.put("willExecute", false);
        preview.put("affectedResources", generateAffectedResources(tool, arguments));
        preview.put("changeDetails", arguments != null ? arguments : new LinkedHashMap<>());
        preview.put("estimatedDuration", "5s");
        preview.put("confirmationRequired", true);

        result.put("dryRunData", preview);

        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            log.error("Error serializing dry-run result: {}", e.getMessage(), e);
            return "{}";
        }
    }

    /**
     * Build a blocked result for L3 (dangerous) operations.
     */
    private String buildBlockedResult(ToolDefinition tool, SecurityCheckResult checkResult) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", tool.getMcpToolName());
        result.put("resource", tool.getResource());
        result.put("verb", tool.getVerb());
        result.put("riskLevel", tool.getRiskLevel().name());
        result.put("status", "blocked");
        result.put("message", checkResult.getMessage());
        result.put("hint", "To enable dangerous operations, restart the MCP server with --enable-dangerous-ops");

        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            log.error("Error serializing blocked result: {}", e.getMessage(), e);
            return "{}";
        }
    }

    private String buildErrorResult(String message, String code) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "error");
        result.put("code", code);
        result.put("message", message);
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return "{\"status\":\"error\",\"message\":\"" + message + "\"}";
        }
    }

    private List<String> generateAffectedResources(ToolDefinition tool, Map<String, Object> arguments) {
        List<String> resources = new ArrayList<>();
        String resourceType = tool.getResource().toUpperCase();
        if (arguments != null) {
            for (String key : new String[]{"topic", "group", "cluster", "name", "username", "brokerName"}) {
                if (arguments.containsKey(key)) {
                    resources.add(resourceType + ":" + arguments.get(key));
                    break;
                }
            }
        }
        if (resources.isEmpty()) {
            resources.add(resourceType + ":<unspecified>");
        }
        return resources;
    }

    /**
     * Map ParamSchema type to JSON Schema type.
     */
    private String mapType(String paramType) {
        if (paramType == null) {
            return "string";
        }
        switch (paramType.toUpperCase()) {
            case "STRING":
                return "string";
            case "INT":
            case "LONG":
                return "integer";
            case "BOOLEAN":
                return "boolean";
            case "ENUM":
                return "string";
            default:
                return "string";
        }
    }

    public SecurityGate getSecurityGate() {
        return securityGate;
    }
}
