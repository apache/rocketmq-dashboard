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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.apache.rocketmq.dashboard.cli.schema.ParamSchema;
import org.apache.rocketmq.dashboard.cli.schema.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Proxy service that bridges console requests to LLM providers.
 * Supports multiple LLM backends: OpenAI, Azure, Bedrock, Tongyi (DashScope),
 * DeepSeek, and Ollama. All communication uses OpenAI-compatible chat completions API
 * format where possible, with provider-specific URL and header adjustments.
 */
@Service
public class LlmProxyService {

    private static final Logger log = LoggerFactory.getLogger(LlmProxyService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT =
            "You are a RocketMQ operations assistant. "
            + "Use the provided tools to help the user manage their RocketMQ cluster. "
            + "Always confirm cluster context before operations. "
            + "For write operations, show what will happen before executing.";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * Send a chat message to the configured LLM provider.
     *
     * @param userMessage the user's natural language message
     * @param tools       the available tool definitions for function calling
     * @param config      the LLM configuration
     * @return JSON string with response content and any tool calls
     */
    @SuppressWarnings("unchecked")
    public String chat(String userMessage, List<ToolDefinition> tools, LlmConfig config) {
        if (!config.isEnabled() || config.getApiKey() == null || config.getApiKey().isEmpty()) {
            return buildError("LLM is not configured.");
        }
        // Build simple messages with system + user for backward compat
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> sys = new LinkedHashMap<>();
        sys.put("role", "system"); sys.put("content", SYSTEM_PROMPT);
        messages.add(sys);
        Map<String, Object> usr = new LinkedHashMap<>();
        usr.put("role", "user"); usr.put("content", userMessage);
        messages.add(usr);
        return doChat(messages, tools, config);
    }

    private String doChat(List<Map<String, Object>> messages, List<ToolDefinition> tools, LlmConfig config) {
        if (!config.isEnabled() || config.getApiKey() == null || config.getApiKey().isEmpty()) {
            return buildError("LLM is not configured. Please set apiKey and enable the provider.");
        }

        try {
            Map<String, Object> requestBody = buildChatRequest(messages, tools, config, false);
            String requestJson = objectMapper.writeValueAsString(requestBody);

            String url = buildChatUrl(config);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMinutes(5))
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8));

            addAuthHeaders(requestBuilder, config);

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return parseChatResponse(response.body());
            } else {
                log.error("LLM API returned status {}: {}", response.statusCode(), response.body());
                return buildError("LLM API error: HTTP " + response.statusCode() + " - " + response.body());
            }
        } catch (IOException | InterruptedException e) {
            log.error("Failed to call LLM API: {}", e.getMessage(), e);
            return buildError("Failed to connect to LLM provider: " + e.getMessage());
        }
    }

    /**
     * Send a streaming chat message using pre-built messages array (supports multi-turn).
     */
    @SuppressWarnings("unchecked")
    public void chatStream(List<Map<String, Object>> messages, List<ToolDefinition> tools,
                           LlmConfig config, Consumer<String> chunkCallback) {
        if (!config.isEnabled() || config.getApiKey() == null || config.getApiKey().isEmpty()) {
            chunkCallback.accept(buildError("LLM is not configured."));
            return;
        }

        try {
            Map<String, Object> requestBody = buildChatRequest(messages, tools, config, true);
            String requestJson = objectMapper.writeValueAsString(requestBody);

            // Log messages summary for debugging multi-turn issues
            if (log.isDebugEnabled()) {
                log.debug("LLM stream request: {} messages, model={}, provider={}",
                        messages.size(), config.getModel(), config.getProvider());
                for (int i = 0; i < messages.size(); i++) {
                    Map<String, Object> msg = messages.get(i);
                    String role = String.valueOf(msg.get("role"));
                    boolean hasToolCalls = msg.containsKey("tool_calls");
                    boolean hasToolCallId = msg.containsKey("tool_call_id");
                    log.debug("  msg[{}]: role={}, hasToolCalls={}, hasToolCallId={}",
                            i, role, hasToolCalls, hasToolCallId);
                }
            }

            String url = buildChatUrl(config);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMinutes(5))
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8));

            addAuthHeaders(requestBuilder, config);

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                // Parse SSE events from the response body
                String body = response.body();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                new java.io.ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)),
                                StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);
                            if ("[DONE]".equals(data.trim())) {
                                break;
                            }
                            chunkCallback.accept(data);
                        }
                    }
                }
            } else {
                // Read and include the error response body for debugging
                String errorBody = response.body();
                log.error("LLM streaming API returned HTTP {}: {}", response.statusCode(), errorBody);
                // Try to extract a meaningful error message from the response body
                String errorMessage = "HTTP " + response.statusCode();
                try {
                    Map<String, Object> errorObj = objectMapper.readValue(errorBody, Map.class);
                    Object error = errorObj.get("error");
                    if (error instanceof Map) {
                        Object msg = ((Map<?, ?>) error).get("message");
                        if (msg != null) {
                            errorMessage = msg.toString();
                        }
                    }
                } catch (Exception parseEx) {
                    // If we can't parse the error body, include a truncated version
                    if (errorBody != null && errorBody.length() > 200) {
                        errorMessage += " - " + errorBody.substring(0, 200) + "...";
                    } else if (errorBody != null && !errorBody.isEmpty()) {
                        errorMessage += " - " + errorBody;
                    }
                }
                chunkCallback.accept(buildError("LLM streaming error: " + errorMessage));
            }
        } catch (IOException | InterruptedException e) {
            log.error("Failed to call LLM streaming API: {}", e.getMessage(), e);
            chunkCallback.accept(buildError("Streaming failed: " + e.getMessage()));
        }
    }

    /**
     * List available models from the configured LLM provider.
     * Calls the /v1/models (OpenAI-compatible) endpoint.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listModels(LlmConfig config) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!config.isEnabled() || config.getApiKey() == null || config.getApiKey().isEmpty()) {
            return result;
        }

        try {
            String baseUrl = buildModelsUrl(config);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .GET();

            addAuthHeaders(requestBuilder, config);

            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                Map<String, Object> body = objectMapper.readValue(response.body(), Map.class);
                List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
                if (data != null) {
                    for (Map<String, Object> model : data) {
                        Map<String, Object> entry = new LinkedHashMap<>();
                        entry.put("id", model.get("id"));
                        entry.put("name", model.getOrDefault("id", "unknown"));
                        result.add(entry);
                    }
                }
            } else {
                log.error("listModels API returned status {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("Failed to list models from provider: {}", e.getMessage());
        }
        return result;
    }

    /**
     * Build the models listing URL for the given provider.
     */
    private String buildModelsUrl(LlmConfig config) {
        String provider = config.getProvider() != null
                ? config.getProvider().toUpperCase() : "OPENAI";
        switch (provider) {
            case "OLLAMA":
                String ollamaBase = config.getApiBase() != null
                        ? config.getApiBase().replaceAll("/$", "") : "http://localhost:11434";
                return ollamaBase + "/api/tags";
            case "AZURE":
                String azureBase = config.getApiBase() != null
                        ? config.getApiBase().replaceAll("/$", "") : "";
                return azureBase + "/openai/models";
            default:
                // OpenAI, DeepSeek, Tongyi, and other compatible providers
                String customBase = config.getApiBase() != null
                        ? config.getApiBase().replaceAll("/$", "") : "";
                if (!customBase.isEmpty()) {
                    return customBase + "/models";
                }
                // Use known defaults
                switch (provider) {
                    case "OPENAI":
                        return "https://api.openai.com/v1/models";
                    case "DEEPSEEK":
                        return "https://api.deepseek.com/v1/models";
                    case "TONGYI":
                        return "https://dashscope.aliyuncs.com/compatible-mode/v1/models";
                    default:
                        return customBase + "/v1/models";
                }
        }
    }

    /**
     * Build the OpenAI-compatible chat completion request body.
     * @param messages pre-built messages array (system + history + current user message)
     */
    private Map<String, Object> buildChatRequest(List<Map<String, Object>> messages,
                                                  List<ToolDefinition> tools,
                                                  LlmConfig config, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel() != null ? config.getModel() : "gpt-4");
        body.put("max_tokens", config.getMaxTokens());
        body.put("temperature", config.getTemperature());
        body.put("stream", stream);
        body.put("messages", messages);

        // Build tool definitions in OpenAI function calling format
        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> toolList = new ArrayList<>();
            for (ToolDefinition tool : tools) {
                Map<String, Object> toolEntry = new LinkedHashMap<>();
                toolEntry.put("type", "function");

                Map<String, Object> function = new LinkedHashMap<>();
                String llmFunctionName = tool.getMcpToolName().replace('.', '_');
                functionNameToToolName.put(llmFunctionName, tool.getMcpToolName());
                function.put("name", llmFunctionName);
                function.put("description", tool.getDescription());

                // Build parameters schema
                Map<String, Object> parameters = new LinkedHashMap<>();
                parameters.put("type", "object");

                Map<String, Object> properties = new LinkedHashMap<>();
                List<String> required = new ArrayList<>();

                if (tool.getParams() != null) {
                    for (ParamSchema param : tool.getParams()) {
                        Map<String, Object> prop = new LinkedHashMap<>();
                        prop.put("type", mapTypeToJsonSchema(param.getType()));
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

                parameters.put("properties", properties);
                if (!required.isEmpty()) {
                    parameters.put("required", required);
                }

                function.put("parameters", parameters);
                toolEntry.put("function", function);
                toolList.add(toolEntry);
            }
            body.put("tools", toolList);
            body.put("tool_choice", "auto");
        }

        return body;
    }

    private final Map<String, String> functionNameToToolName = new ConcurrentHashMap<>();

    /**
     * POST /api/llm/resolveFunctionName
     * @return toolName
     */
    public String resolveFunctionName(String llmFunctionName) {
        return functionNameToToolName.getOrDefault(llmFunctionName, llmFunctionName);
    }

    /**
     * Build the chat completions URL for the given provider.
     */
    private String buildChatUrl(LlmConfig config) {
        String provider = config.getProvider() != null
                ? config.getProvider().toUpperCase() : "OPENAI";
        switch (provider) {
            case "OPENAI":
                return "https://api.openai.com/v1/chat/completions";
            case "AZURE":
                String base = config.getApiBase() != null
                        ? config.getApiBase().replaceAll("/$", "") : "";
                String model = config.getModel() != null ? config.getModel() : "gpt-4";
                return base + "/openai/deployments/" + model + "/chat/completions";
            case "DEEPSEEK":
                return "https://api.deepseek.com/v1/chat/completions";
            case "TONGYI":
                return "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
            case "BEDROCK":
                String bedrockBase = config.getApiBase() != null
                        ? config.getApiBase().replaceAll("/$", "") : "";
                return bedrockBase + "/chat/completions";
            case "OLLAMA":
                String ollamaBase = config.getApiBase() != null
                        ? config.getApiBase().replaceAll("/$", "") : "http://localhost:11434";
                return ollamaBase + "/api/chat";
            default:
                String customBase = config.getApiBase() != null
                        ? config.getApiBase().replaceAll("/$", "") : "";
                return customBase + "/chat/completions";
        }
    }

    /**
     * Add authentication headers based on the provider.
     */
    private void addAuthHeaders(HttpRequest.Builder builder, LlmConfig config) {
        String provider = config.getProvider() != null
                ? config.getProvider().toUpperCase() : "OPENAI";
        switch (provider) {
            case "AZURE":
                builder.header("api-key", config.getApiKey());
                break;
            case "OLLAMA":
                // Ollama typically runs locally without auth; add if key present
                if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
                    builder.header("Authorization", "Bearer " + config.getApiKey());
                }
                break;
            default:
                builder.header("Authorization", "Bearer " + config.getApiKey());
                break;
        }
    }

    /**
     * Parse the chat completion response and extract content + tool calls.
     */
    @SuppressWarnings("unchecked")
    private String parseChatResponse(String responseBody) {
        try {
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            Map<String, Object> result = new LinkedHashMap<>();

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                Map<String, Object> message = (Map<String, Object>) choice.get("message");

                if (message != null) {
                    // Extract text content
                    Object content = message.get("content");
                    if (content != null) {
                        result.put("content", content.toString());
                    }

                    // Extract tool calls
                    List<Map<String, Object>> toolCalls =
                            (List<Map<String, Object>>) message.get("tool_calls");
                    if (toolCalls != null && !toolCalls.isEmpty()) {
                        List<Map<String, Object>> parsedCalls = new ArrayList<>();
                        for (Map<String, Object> call : toolCalls) {
                            Map<String, Object> parsedCall = new LinkedHashMap<>();
                            parsedCall.put("id", call.get("id"));
                            Map<String, Object> function =
                                    (Map<String, Object>) call.get("function");
                            if (function != null) {
                                String llmFunctionName = (String) function.get("name");
                                String originalName = functionNameToToolName.getOrDefault(
                                        llmFunctionName, llmFunctionName);
                                parsedCall.put("name", originalName);
                                String arguments = (String) function.get("arguments");
                                if (arguments != null) {
                                    try {
                                        parsedCall.put("arguments",
                                                objectMapper.readValue(arguments, Map.class));
                                    } catch (IOException e) {
                                        parsedCall.put("arguments", arguments);
                                    }
                                }
                            }
                            parsedCalls.add(parsedCall);
                        }
                        result.put("toolCalls", parsedCalls);
                    }
                }

                // Include finish reason
                result.put("finishReason", choice.get("finish_reason"));
            }

            // Include usage info
            Map<String, Object> usage = (Map<String, Object>) response.get("usage");
            if (usage != null) {
                result.put("usage", usage);
            }

            return objectMapper.writeValueAsString(result);
        } catch (IOException e) {
            log.error("Failed to parse chat response: {}", e.getMessage(), e);
            return "{\"content\":\"" + escapeJson(responseBody) + "\"}";
        }
    }

    private String buildError(String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("error", message);
        try {
            return objectMapper.writeValueAsString(error);
        } catch (IOException e) {
            return "{\"error\":\"" + escapeJson(message) + "\"}";
        }
    }

    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String mapTypeToJsonSchema(String paramType) {
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
}
