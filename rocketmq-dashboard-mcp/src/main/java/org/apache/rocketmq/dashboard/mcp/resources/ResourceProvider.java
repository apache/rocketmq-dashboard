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
package org.apache.rocketmq.dashboard.mcp.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.rocketmq.dashboard.cli.executor.ToolExecutor;
import org.apache.rocketmq.dashboard.cli.schema.ToolDefinition;
import org.apache.rocketmq.dashboard.cli.schema.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides MCP resource endpoints for listing and reading RocketMQ resources.
 * Resources expose cluster topology data as structured URIs.
 *
 * <p>resources/read executes against the live cluster through the shared
 * {@link ToolExecutor} (same path as tools/call). Failures surface as
 * explicit errors instead of silently falling back to mock data.</p>
 */
public class ResourceProvider {

    private static final Logger log = LoggerFactory.getLogger(ResourceProvider.class);

    /** Maps each resource URI to the read-only tool that backs it. */
    private static final Map<String, String> URI_TO_TOOL = Map.of(
            "rmq://topics", "rmq.topic.list",
            "rmq://groups", "rmq.group.list",
            "rmq://clients", "rmq.client.list");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ToolExecutor toolExecutor;

    /** Creates a provider backed by a real {@link ToolExecutor}. */
    public ResourceProvider() {
        this(new ToolExecutor());
    }

    /** Visible for tests: inject a stub {@link ToolExecutor}. */
    public ResourceProvider(ToolExecutor toolExecutor) {
        this.toolExecutor = toolExecutor;
    }

    /**
     * Return the MCP resources/list response as a JSON array.
     * Exposes three resource URIs: topics, groups, clients.
     *
     * @return JSON string for the resources/list response
     */
    public String handleResourcesList() {
        List<Map<String, Object>> resources = new ArrayList<>();

        Map<String, Object> topics = new LinkedHashMap<>();
        topics.put("uri", "rmq://topics");
        topics.put("name", "RocketMQ Topics");
        topics.put("description", "List of all topics in the connected RocketMQ cluster");
        topics.put("mimeType", "application/json");
        resources.add(topics);

        Map<String, Object> groups = new LinkedHashMap<>();
        groups.put("uri", "rmq://groups");
        groups.put("name", "RocketMQ Consumer Groups");
        groups.put("description", "List of all consumer groups in the connected RocketMQ cluster");
        groups.put("mimeType", "application/json");
        resources.add(groups);

        Map<String, Object> clients = new LinkedHashMap<>();
        clients.put("uri", "rmq://clients");
        clients.put("name", "RocketMQ Clients");
        clients.put("description", "List of all connected clients in the RocketMQ cluster");
        clients.put("mimeType", "application/json");
        resources.add(clients);

        try {
            return objectMapper.writeValueAsString(resources);
        } catch (JsonProcessingException e) {
            log.error("Error serializing resources list: {}", e.getMessage(), e);
            return "[]";
        }
    }

    /**
     * Read a specific resource by URI against the live cluster.
     *
     * @param uri the resource URI (e.g. "rmq://topics")
     * @return JSON string with the resource content, or an error payload
     */
    public String handleResourcesRead(String uri) {
        if (uri == null) {
            return buildError("Resource URI is required", "INVALID_URI");
        }

        String toolName = URI_TO_TOOL.get(uri);
        if (toolName == null) {
            return buildError("Unknown resource URI: " + uri, "INVALID_URI");
        }

        ToolDefinition tool = ToolRegistry.getInstance().getTool(toolName);
        if (tool == null) {
            return buildError("No tool registered for resource: " + uri, "EXECUTION_ERROR");
        }

        Object data;
        try {
            // Empty arguments: the cluster is resolved from the CLI context,
            // exactly like tools/call without an explicit cluster argument.
            data = toolExecutor.execute(tool, new LinkedHashMap<>());
        } catch (Exception e) {
            log.error("Failed to read resource {} via {}: {}", uri, toolName, e.getMessage(), e);
            return buildError("Failed to read resource " + uri + ": " + e.getMessage(),
                    "EXECUTION_ERROR");
        }

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("uri", uri);
        content.put("mimeType", "application/json");
        content.put("live", true);
        content.put("data", data != null ? data : new ArrayList<>());

        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            log.error("Error serializing resource content: {}", e.getMessage(), e);
            return "{}";
        }
    }

    private String buildError(String message, String errorType) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("error", message);
        error.put("errorType", errorType);
        try {
            return objectMapper.writeValueAsString(error);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"" + message + "\"}";
        }
    }
}
