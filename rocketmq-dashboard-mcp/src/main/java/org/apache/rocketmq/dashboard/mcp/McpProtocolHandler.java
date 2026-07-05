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
package org.apache.rocketmq.dashboard.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.apache.rocketmq.dashboard.mcp.resources.ResourceProvider;
import org.apache.rocketmq.dashboard.mcp.tools.McpToolRegistry;
import org.apache.rocketmq.dashboard.mcp.transport.McpMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles MCP JSON-RPC protocol messages.
 * <p>
 * Implements the standard MCP protocol methods:
 * <ul>
 *   <li>{@code initialize} — server handshake with capabilities</li>
 *   <li>{@code tools/list} — list available tools (from ToolRegistry)</li>
 *   <li>{@code tools/call} — call a tool with security gate</li>
 *   <li>{@code resources/list} — list available resources</li>
 *   <li>{@code resources/read} — read a specific resource</li>
 * </ul>
 */
public class McpProtocolHandler implements McpMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(McpProtocolHandler.class);

    private static final String JSONRPC_VERSION = "2.0";
    private static final String PROTOCOL_VERSION = "2024-11-05";
    private static final String SERVER_NAME = "rocketmq-dashboard-mcp";
    private static final String SERVER_VERSION = "2.1.1-SNAPSHOT";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final McpToolRegistry toolRegistry;
    private final ResourceProvider resourceProvider;

    public McpProtocolHandler(McpToolRegistry toolRegistry, ResourceProvider resourceProvider) {
        this.toolRegistry = toolRegistry;
        this.resourceProvider = resourceProvider;
    }

    @Override
    public String handleMessage(String jsonMessage) {
        try {
            JsonNode root = objectMapper.readTree(jsonMessage);

            // Extract JSON-RPC fields
            String method = root.has("method") ? root.get("method").asText() : null;
            JsonNode idNode = root.get("id");
            String id = idNode != null && !idNode.isNull() ? idNode.asText() : null;

            if (method == null) {
                return buildErrorResponse(id, -32600, "Invalid Request: missing method");
            }

            log.debug("Handling method: {} (id={})", method, id);

            String result;
            switch (method) {
                case "initialize":
                    result = handleInitialize();
                    break;
                case "tools/list":
                    result = toolRegistry.handleToolsList();
                    break;
                case "tools/call":
                    result = handleToolsCall(root);
                    break;
                case "resources/list":
                    result = resourceProvider.handleResourcesList();
                    break;
                case "resources/read":
                    result = handleResourcesRead(root);
                    break;
                default:
                    return buildErrorResponse(id, -32601,
                            "Method not found: " + method);
            }

            return buildSuccessResponse(id, result);

        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON message: {}", e.getMessage(), e);
            return buildErrorResponse(null, -32700, "Parse error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error handling message: {}", e.getMessage(), e);
            return buildErrorResponse(null, -32603, "Internal error: " + e.getMessage());
        }
    }

    /**
     * Handle the initialize handshake.
     * Returns server info and capabilities (tools and resources).
     */
    private String handleInitialize() throws JsonProcessingException {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);

        ObjectNode serverInfo = objectMapper.createObjectNode();
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        result.set("serverInfo", serverInfo);

        ObjectNode capabilities = objectMapper.createObjectNode();

        ObjectNode toolsCap = objectMapper.createObjectNode();
        toolsCap.put("listChanged", false);
        capabilities.set("tools", toolsCap);

        ObjectNode resourcesCap = objectMapper.createObjectNode();
        resourcesCap.put("subscribe", false);
        resourcesCap.put("listChanged", false);
        capabilities.set("resources", resourcesCap);

        result.set("capabilities", capabilities);

        return objectMapper.writeValueAsString(result);
    }

    /**
     * Handle tools/call request.
     * Extracts tool name and arguments from the params.
     */
    @SuppressWarnings("unchecked")
    private String handleToolsCall(JsonNode root) {
        JsonNode params = root.get("params");
        if (params == null) {
            return "{\"status\":\"error\",\"message\":\"Missing params\"}";
        }

        String toolName = params.has("name") ? params.get("name").asText() : null;
        if (toolName == null) {
            return "{\"status\":\"error\",\"message\":\"Missing tool name\"}";
        }

        Map<String, Object> arguments = null;
        if (params.has("arguments") && !params.get("arguments").isNull()) {
            try {
                arguments = objectMapper.convertValue(params.get("arguments"), Map.class);
            } catch (Exception e) {
                log.warn("Error parsing arguments: {}", e.getMessage());
            }
        }

        return toolRegistry.handleToolsCall(toolName, arguments);
    }

    /**
     * Handle resources/read request.
     * Extracts the URI from params.
     */
    private String handleResourcesRead(JsonNode root) {
        JsonNode params = root.get("params");
        if (params == null) {
            return "{\"error\":\"Missing params\"}";
        }

        String uri = params.has("uri") ? params.get("uri").asText() : null;
        return resourceProvider.handleResourcesRead(uri);
    }

    // ---- JSON-RPC response builders ------------------------------------------

    private String buildSuccessResponse(String id, String resultPayload) {
        try {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", JSONRPC_VERSION);
            if (id != null) {
                response.put("id", id);
            }

            // Parse resultPayload as JSON if possible, otherwise as string
            try {
                JsonNode resultNode = objectMapper.readTree(resultPayload);
                response.set("result", resultNode);
            } catch (JsonProcessingException e) {
                response.put("result", resultPayload);
            }

            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            log.error("Error building success response: {}", e.getMessage(), e);
            return "{}";
        }
    }

    private String buildErrorResponse(String id, int code, String message) {
        try {
            ObjectNode response = objectMapper.createObjectNode();
            response.put("jsonrpc", JSONRPC_VERSION);
            if (id != null) {
                response.put("id", id);
            }

            ObjectNode error = objectMapper.createObjectNode();
            error.put("code", code);
            error.put("message", message);
            response.set("error", error);

            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            log.error("Error building error response: {}", e.getMessage(), e);
            return "{}";
        }
    }
}
