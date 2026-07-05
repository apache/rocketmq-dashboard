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

    public McpToolRegistry(SecurityGate securityGate) {
        this.securityGate = securityGate;
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
                return buildSuccessResult(tool, arguments);

            case DRY_RUN:
                return buildDryRunResult(tool, arguments, checkResult);

            case BLOCK:
                return buildBlockedResult(tool, checkResult);

            default:
                return buildErrorResult("Unknown security action", "SECURITY_ERROR");
        }
    }

    /**
     * Build a success result for allowed operations (primarily L1 read-only tools).
     * Returns a mock/simulated result since the MCP server does not connect
     * to a live RocketMQ cluster by default.
     */
    private String buildSuccessResult(ToolDefinition tool, Map<String, Object> arguments) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", tool.getMcpToolName());
        result.put("resource", tool.getResource());
        result.put("verb", tool.getVerb());
        result.put("riskLevel", tool.getRiskLevel().name());
        result.put("status", "success");

        if (arguments != null && !arguments.isEmpty()) {
            result.put("arguments", arguments);
        }

        // Generate mock data based on return type
        switch (tool.getReturnType()) {
            case "LIST":
                result.put("data", generateMockList(tool));
                break;
            case "OBJECT":
                result.put("data", generateMockObject(tool, arguments));
                break;
            case "VOID":
                result.put("data", "OK");
                break;
            default:
                result.put("data", generateMockObject(tool, arguments));
        }

        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            log.error("Error serializing result: {}", e.getMessage(), e);
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

    // ---- Mock data generators ------------------------------------------------

    private List<Map<String, Object>> generateMockList(ToolDefinition tool) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            switch (tool.getResource()) {
                case "topic":
                    item.put("name", "Topic-" + i);
                    item.put("status", "ACTIVE");
                    item.put("queueNums", 8);
                    item.put("perm", 6);
                    break;
                case "group":
                    item.put("name", "Group-" + i);
                    item.put("status", "ACTIVE");
                    item.put("consumeMode", "CLUSTER");
                    break;
                case "cluster":
                    item.put("name", "Cluster-" + i);
                    item.put("status", "HEALTHY");
                    break;
                case "namespace":
                    item.put("name", "ns-" + i);
                    item.put("status", "ACTIVE");
                    break;
                case "acl":
                    item.put("policyId", "policy-" + i);
                    item.put("username", "user-" + i);
                    item.put("decision", "ALLOW");
                    break;
                case "broker":
                    item.put("name", "broker-" + i);
                    item.put("status", "RUNNING");
                    item.put("version", "5.5.0");
                    break;
                case "client":
                    item.put("clientId", "client-" + i);
                    item.put("type", "CONSUMER");
                    item.put("version", "5.5.0");
                    break;
                case "message":
                    item.put("msgId", "7F00000100002A9F" + i);
                    item.put("topic", "Topic-" + i);
                    break;
                case "capabilities":
                    item.put("feature", "capability-" + i);
                    item.put("supported", true);
                    break;
                default:
                    item.put("id", "item-" + i);
                    item.put("name", tool.getResource() + "-" + i);
            }
            list.add(item);
        }
        return list;
    }

    private Map<String, Object> generateMockObject(ToolDefinition tool, Map<String, Object> arguments) {
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("name", arguments != null && arguments.containsKey("topic")
                ? arguments.get("topic")
                : tool.getResource() + "-detail");
        obj.put("status", "ACTIVE");
        obj.put("type", tool.getResource());
        if (arguments != null) {
            obj.put("requestedParams", arguments);
        }
        return obj;
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
