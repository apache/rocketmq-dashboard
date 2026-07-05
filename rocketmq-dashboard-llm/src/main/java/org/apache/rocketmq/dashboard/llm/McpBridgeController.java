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
package org.apache.rocketmq.dashboard.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.rocketmq.dashboard.cli.schema.ParamSchema;
import org.apache.rocketmq.dashboard.cli.schema.RiskLevel;
import org.apache.rocketmq.dashboard.cli.schema.ToolDefinition;
import org.apache.rocketmq.dashboard.cli.schema.ToolRegistry;
import org.apache.rocketmq.dashboard.mcp.tools.SecurityCheckResult;
import org.apache.rocketmq.dashboard.mcp.tools.SecurityGate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that bridges the Console frontend to the LLM and MCP layers.
 * Provides endpoints for tool discovery, LLM chat, confirmed execution,
 * and LLM configuration management.
 */
@RestController
@RequestMapping("/api/llm")
public class McpBridgeController {

    private static final Logger log = LoggerFactory.getLogger(McpBridgeController.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** Store dry-run results for later confirmation. */
    private final ConcurrentHashMap<String, DryRunRecord> dryRunStore = new ConcurrentHashMap<>();

    @Autowired
    private LlmProxyService llmProxyService;

    // -----------------------------------------------------------------------
    // Tools
    // -----------------------------------------------------------------------

    /**
     * GET /api/llm/tools?cluster=xxx
     * Returns the filtered tool list for the current user and cluster.
     */
    @GetMapping("/tools")
    public Map<String, Object> getTools(@RequestParam String cluster) {
        Map<String, Object> result = new LinkedHashMap<>();

        List<ToolDefinition> allTools = ToolRegistry.getInstance().getAllTools();

        // For the controller, default to all capabilities; in production, detect from cluster
        ClusterCapability capability = ClusterCapability.all();

        // Default to ADMIN for now; in production, derive from auth context
        List<ToolDefinition> filteredTools = ToolFilter.filterForUser(allTools, "ADMIN", capability);

        List<Map<String, Object>> toolList = convertToolsToJson(filteredTools);

        result.put("tools", toolList);
        result.put("cluster", cluster);
        result.put("total", toolList.size());
        return result;
    }

    // -----------------------------------------------------------------------
    // Chat
    // -----------------------------------------------------------------------

    /**
     * POST /api/llm/chat
     * Body: { "message": "...", "cluster": "...", "history": [...] }
     * Returns: { "content": "...", "toolCalls": [...], "viewHint": "table"|"dry-run"|... }
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new LinkedHashMap<>();

        String message = (String) request.get("message");
        String cluster = (String) request.get("cluster");
        List<Map<String, Object>> history =
                (List<Map<String, Object>>) request.get("history");

        if (message == null || message.trim().isEmpty()) {
            result.put("error", "Message is required");
            return result;
        }

        // Load config
        LlmConfig config = LlmConfig.LlmConfigManager.load();

        // Check if LLM is configured
        if (!LlmConfig.LlmConfigManager.isConfigured()) {
            result.put("degraded", true);
            result.put("suggestion", "Use global search. LLM is not configured. "
                    + "Go to Settings to configure an LLM provider (OpenAI, Azure, DeepSeek, etc.).");
            return result;
        }

        // Filter tools for user
        ClusterCapability capability = ClusterCapability.all();
        List<ToolDefinition> allTools = ToolRegistry.getInstance().getAllTools();
        List<ToolDefinition> filteredTools = ToolFilter.filterForUser(allTools, "ADMIN", capability);

        // Build full prompt with history
        String fullPrompt = buildPromptWithHistory(message, history);

        // Call LLM
        String llmResponse = llmProxyService.chat(fullPrompt, filteredTools, config);

        try {
            Map<String, Object> parsed = objectMapper.readValue(llmResponse, Map.class);

            if (parsed.containsKey("error")) {
                result.put("error", parsed.get("error"));
                return result;
            }

            result.put("content", parsed.getOrDefault("content", ""));
            result.put("finishReason", parsed.getOrDefault("finishReason", "stop"));

            // Handle tool calls
            List<Map<String, Object>> toolCalls =
                    (List<Map<String, Object>>) parsed.get("toolCalls");
            if (toolCalls != null && !toolCalls.isEmpty()) {
                List<Map<String, Object>> enrichedCalls = new ArrayList<>();
                for (Map<String, Object> toolCall : toolCalls) {
                    Map<String, Object> enriched = processToolCall(
                            toolCall, cluster, "console-user");
                    enrichedCalls.add(enriched);
                }
                result.put("toolCalls", enrichedCalls);

                // Determine view hint
                result.put("viewHint", determineViewHint(enrichedCalls));
            } else {
                result.put("viewHint", "text");
                result.put("toolCalls", new ArrayList<>());
            }

        } catch (JsonProcessingException e) {
            log.error("Failed to parse LLM response: {}", e.getMessage(), e);
            result.put("content", llmResponse);
            result.put("viewHint", "text");
            result.put("toolCalls", new ArrayList<>());
        }

        return result;
    }

    // -----------------------------------------------------------------------
    // Confirm
    // -----------------------------------------------------------------------

    /**
     * POST /api/llm/confirm
     * Body: { "toolCallId": "...", "confirm": true }
     * Executes a previously dry-run tool call.
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/confirm")
    public Map<String, Object> confirmAction(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new LinkedHashMap<>();

        String toolCallId = (String) request.get("toolCallId");
        Boolean confirm = (Boolean) request.get("confirm");

        if (toolCallId == null) {
            result.put("error", "toolCallId is required");
            return result;
        }

        DryRunRecord record = dryRunStore.get(toolCallId);
        if (record == null) {
            result.put("error", "Tool call not found or already confirmed: " + toolCallId);
            return result;
        }

        if (!Boolean.TRUE.equals(confirm)) {
            dryRunStore.remove(toolCallId);
            result.put("status", "cancelled");
            result.put("message", "Operation was cancelled by user");
            return result;
        }

        try {
            // Execute the confirmed tool call
            Map<String, Object> executionResult = executeTool(
                    record.getToolName(),
                    record.getArguments(),
                    record.getCluster(),
                    record.getUsername());

            // Remove from store
            dryRunStore.remove(toolCallId);

            // Log with LlmAuditLogger
            String paramsJson = objectMapper.writeValueAsString(record.getArguments());
            String resultStr = executionResult.getOrDefault("status", "unknown").toString();
            LlmAuditLogger.log(record.getCluster(), record.getUsername(),
                    record.getToolName(), paramsJson, resultStr, "console-llm");

            result.put("status", "executed");
            result.put("toolCallId", toolCallId);
            result.put("toolName", record.getToolName());
            result.put("result", executionResult);

        } catch (Exception e) {
            log.error("Failed to execute confirmed tool: {}", e.getMessage(), e);
            result.put("status", "error");
            result.put("error", "Execution failed: " + e.getMessage());
        }

        return result;
    }

    // -----------------------------------------------------------------------
    // Config
    // -----------------------------------------------------------------------

    /**
     * GET /api/llm/config
     * Returns the current LLM config with the apiKey masked.
     */
    @GetMapping("/config")
    public LlmConfig getConfig() {
        LlmConfig config = LlmConfig.LlmConfigManager.load();
        // Mask the API key for security
        LlmConfig masked = new LlmConfig();
        masked.setProvider(config.getProvider());
        masked.setApiKey(maskApiKey(config.getApiKey()));
        masked.setApiBase(config.getApiBase());
        masked.setModel(config.getModel());
        masked.setMaxTokens(config.getMaxTokens());
        masked.setTemperature(config.getTemperature());
        masked.setEnabled(config.isEnabled());
        return masked;
    }

    /**
     * POST /api/llm/config
     * Saves the LLM configuration.
     */
    @PostMapping("/config")
    public Map<String, String> saveConfig(@RequestBody LlmConfig config) {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            // If the apiKey was masked, preserve the existing key
            if (config.getApiKey() != null && config.getApiKey().startsWith("****")) {
                LlmConfig existing = LlmConfig.LlmConfigManager.load();
                config.setApiKey(existing.getApiKey());
            }
            LlmConfig.LlmConfigManager.save(config);
            result.put("status", "saved");
        } catch (IOException e) {
            log.error("Failed to save LLM config: {}", e.getMessage(), e);
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * POST /api/llm/config/test
     * Tests the LLM connection by making a simple API call.
     */
    @PostMapping("/config/test")
    public Map<String, Object> testConnection(@RequestBody LlmConfig config) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Resolve API key from masked input
        if (config.getApiKey() != null && config.getApiKey().startsWith("****")) {
            LlmConfig existing = LlmConfig.LlmConfigManager.load();
            config.setApiKey(existing.getApiKey());
        }

        if (config.getApiKey() == null || config.getApiKey().isEmpty()) {
            result.put("success", false);
            result.put("message", "API key is required");
            return result;
        }

        try {
            // Try a simple chat completion with minimal prompt
            List<ToolDefinition> emptyTools = new ArrayList<>();
            String response = llmProxyService.chat("Hello, this is a test connection.", emptyTools, config);

            Map<String, Object> parsed = objectMapper.readValue(response, Map.class);

            if (parsed.containsKey("error")) {
                result.put("success", false);
                result.put("message", parsed.get("error").toString());
            } else {
                result.put("success", true);
                result.put("message", "Successfully connected to "
                        + (config.getProvider() != null ? config.getProvider() : "LLM provider"));
            }
        } catch (Exception e) {
            log.error("LLM connection test failed: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "Connection failed: " + e.getMessage());
        }

        return result;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Convert ToolDefinition list to MCP-compatible JSON for the frontend.
     */
    private List<Map<String, Object>> convertToolsToJson(List<ToolDefinition> tools) {
        List<Map<String, Object>> toolList = new ArrayList<>();
        for (ToolDefinition tool : tools) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", tool.getName());
            entry.put("mcpName", tool.getMcpToolName());
            entry.put("resource", tool.getResource());
            entry.put("verb", tool.getVerb());
            entry.put("riskLevel", tool.getRiskLevel().name());
            entry.put("riskLabel", tool.getRiskLevel().getLabel());
            entry.put("description", tool.getDescription());
            entry.put("returnType", tool.getReturnType());

            if (tool.getParams() != null) {
                List<Map<String, Object>> params = new ArrayList<>();
                for (ParamSchema param : tool.getParams()) {
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("name", param.getName());
                    p.put("type", param.getType());
                    p.put("required", param.isRequired());
                    p.put("description", param.getDescription());
                    p.put("defaultValue", param.getDefaultValue());
                    if (param.getAllowedValues() != null) {
                        p.put("allowedValues", param.getAllowedValues());
                    }
                    params.add(p);
                }
                entry.put("params", params);
            }

            toolList.add(entry);
        }
        return toolList;
    }

    /**
     * Build a full prompt incorporating conversation history.
     */
    @SuppressWarnings("unchecked")
    private String buildPromptWithHistory(String message, List<Map<String, Object>> history) {
        if (history == null || history.isEmpty()) {
            return message;
        }

        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> entry : history) {
            String role = (String) entry.get("role");
            Object content = entry.get("content");
            if (role != null && content != null) {
                sb.append(role).append(": ");
                if (content instanceof List) {
                    for (Object item : (List<Object>) content) {
                        if (item instanceof Map) {
                            Map<String, Object> contentPart = (Map<String, Object>) item;
                            if ("text".equals(contentPart.get("type"))) {
                                sb.append(contentPart.get("text"));
                            }
                        }
                    }
                } else {
                    sb.append(content.toString());
                }
                sb.append("\n");
            }
        }
        sb.append("user: ").append(message);
        return sb.toString();
    }

    /**
     * Process a tool call from the LLM response:
     * - Look up the tool definition
     * - Apply SecurityGate check
     * - For L2/L3: store dry-run record and return preview
     * - For L1: execute immediately
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> processToolCall(Map<String, Object> toolCall,
                                                 String cluster, String username) {
        Map<String, Object> enriched = new LinkedHashMap<>();

        String toolName = (String) toolCall.get("name");
        Map<String, Object> arguments =
                (Map<String, Object>) toolCall.get("arguments");

        enriched.put("id", toolCall.getOrDefault("id", UUID.randomUUID().toString()));
        enriched.put("name", toolName);
        enriched.put("arguments", arguments);

        // Look up tool
        ToolDefinition tool = ToolRegistry.getInstance().getTool(toolName);
        if (tool == null) {
            String nameWithHyphens = toolName.replace("_", "-");
            tool = ToolRegistry.getInstance().getTool(nameWithHyphens);
        }

        if (tool == null) {
            enriched.put("status", "not_found");
            enriched.put("message", "Tool not found: " + toolName);
            return enriched;
        }

        // Security gate check
        SecurityGate securityGate = new SecurityGate();
        SecurityCheckResult checkResult = securityGate.check(tool);

        switch (checkResult.getAction()) {
            case ALLOW:
                // L1: execute immediately
                enriched.put("status", "allowed");
                enriched.put("riskLevel", tool.getRiskLevel().name());
                enriched.put("riskLabel", tool.getRiskLevel().getLabel());
                enriched.put("autoExecute", true);
                try {
                    Map<String, Object> execResult = executeTool(
                            toolName, arguments, cluster, username);
                    enriched.put("result", execResult);
                } catch (Exception e) {
                    enriched.put("resultError", e.getMessage());
                }
                break;

            case DRY_RUN:
                // L2/L3: dry-run, store for confirmation
                String dryRunId = UUID.randomUUID().toString();
                enriched.put("id", dryRunId);
                enriched.put("status", "dry_run");
                enriched.put("riskLevel", tool.getRiskLevel().name());
                enriched.put("riskLabel", tool.getRiskLevel().getLabel());
                enriched.put("message", checkResult.getMessage());
                enriched.put("requiresConfirmation", true);

                Map<String, Object> preview = new LinkedHashMap<>();
                preview.put("operation", tool.getMcpToolName());
                preview.put("willExecute", false);
                preview.put("affectedResources", generateAffectedResources(tool, arguments));
                preview.put("changeDetails", arguments != null ? arguments : new LinkedHashMap<>());
                preview.put("estimatedDuration", "5s");
                preview.put("confirmationRequired", true);
                enriched.put("dryRunPreview", preview);

                // Store for later confirmation
                dryRunStore.put(dryRunId, new DryRunRecord(
                        dryRunId, tool.getMcpToolName(), arguments, cluster, username));
                break;

            case BLOCK:
                enriched.put("status", "blocked");
                enriched.put("riskLevel", tool.getRiskLevel().name());
                enriched.put("riskLabel", tool.getRiskLevel().getLabel());
                enriched.put("message", checkResult.getMessage());
                enriched.put("hint", "Operation blocked by security policy");
                break;
        }

        return enriched;
    }

    /**
     * Execute a tool call and return the result.
     * In production, this would connect to a live RocketMQ cluster.
     */
    private Map<String, Object> executeTool(String toolName, Map<String, Object> arguments,
                                             String cluster, String username) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tool", toolName);
        result.put("cluster", cluster);
        result.put("status", "success");

        if (arguments != null) {
            result.put("arguments", arguments);
        }

        // Look up tool for return type
        ToolDefinition tool = ToolRegistry.getInstance().getTool(toolName);
        if (tool == null) {
            String nameWithHyphens = toolName.replace("_", "-");
            tool = ToolRegistry.getInstance().getTool(nameWithHyphens);
        }

        if (tool != null) {
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
        } else {
            result.put("data", "OK");
        }

        return result;
    }

    /**
     * Determine the view hint for the response based on tool calls.
     */
    private String determineViewHint(List<Map<String, Object>> toolCalls) {
        if (toolCalls.isEmpty()) {
            return "text";
        }
        boolean hasDryRun = toolCalls.stream()
                .anyMatch(tc -> "dry_run".equals(tc.get("status")));
        boolean hasList = toolCalls.stream()
                .anyMatch(tc -> {
                    Map<String, Object> result =
                            (Map<String, Object>) tc.get("result");
                    return result != null && result.containsKey("data")
                            && result.get("data") instanceof List;
                });

        if (hasDryRun) {
            return "dry-run";
        }
        if (hasList) {
            return "table";
        }
        return "text";
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return apiKey;
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    private List<String> generateAffectedResources(ToolDefinition tool,
                                                    Map<String, Object> arguments) {
        List<String> resources = new ArrayList<>();
        String resourceType = tool.getResource().toUpperCase();
        if (arguments != null) {
            for (String key : new String[]{"topic", "group", "cluster", "name",
                    "username", "brokerName"}) {
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

    private Map<String, Object> generateMockObject(ToolDefinition tool,
                                                    Map<String, Object> arguments) {
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

    // -----------------------------------------------------------------------
    // Inner class
    // -----------------------------------------------------------------------

    /**
     * Stores a dry-run record for later confirmation.
     */
    private static class DryRunRecord {
        private final String id;
        private final String toolName;
        private final Map<String, Object> arguments;
        private final String cluster;
        private final String username;

        DryRunRecord(String id, String toolName, Map<String, Object> arguments,
                     String cluster, String username) {
            this.id = id;
            this.toolName = toolName;
            this.arguments = arguments;
            this.cluster = cluster;
            this.username = username;
        }

        public String getId() {
            return id;
        }

        public String getToolName() {
            return toolName;
        }

        public Map<String, Object> getArguments() {
            return arguments;
        }

        public String getCluster() {
            return cluster;
        }

        public String getUsername() {
            return username;
        }
    }
}
