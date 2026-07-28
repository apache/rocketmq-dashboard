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
package org.apache.rocketmq.studio.ops.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Component
public class OpenAiCompatibleLlmClient {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String MODELS_PATH = "/models";
    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("openai", "deepseek", "tongyi", "ollama");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    @Autowired
    public OpenAiCompatibleLlmClient(ObjectMapper objectMapper) {
        this(objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build(), Duration.ofSeconds(60));
    }

    OpenAiCompatibleLlmClient(ObjectMapper objectMapper, HttpClient httpClient, Duration requestTimeout) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
    }

    public boolean supports(LlmConfigVO config) {
        return config != null && SUPPORTED_PROVIDERS.contains(normalize(config.getProvider()));
    }

    public String complete(LlmConfigVO config, String prompt, String modelOverride) {
        validate(config);
        Map<String, Object> requestBody = requestBody(config, prompt, modelOverride, false);
        HttpRequest request = request(config, "application/json", requestBody);
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw upstreamException(response.statusCode(), response.body());
            }
            return parseCompletion(response.body());
        } catch (HttpTimeoutException exception) {
            throw new LlmGatewayException(504, "llm.provider.timeout",
                    "LLM provider request timed out",
                    "Check the provider base URL and network connectivity, then retry.", exception);
        } catch (IOException exception) {
            throw new LlmGatewayException(502, "llm.provider.io_error",
                    "Failed to call LLM provider",
                    "Check the provider endpoint, TLS settings, and network connectivity.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmGatewayException(502, "llm.provider.interrupted",
                    "LLM provider call was interrupted", "Retry the request.", exception);
        }
    }

    public List<LlmModelItemVO> listModels(LlmConfigVO config) {
        validateModelListing(config);
        HttpRequest request = getRequest(config, modelsUri(config));
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw upstreamException(response.statusCode(), response.body());
            }
            return parseModels(response.body());
        } catch (HttpTimeoutException exception) {
            throw new LlmGatewayException(504, "llm.provider.timeout",
                    "LLM provider model request timed out",
                    "Check the provider base URL and network connectivity, then retry.", exception);
        } catch (IOException exception) {
            throw new LlmGatewayException(502, "llm.provider.io_error",
                    "Failed to list LLM provider models",
                    "Check the provider endpoint, TLS settings, and network connectivity.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmGatewayException(502, "llm.provider.interrupted",
                    "LLM provider model request was interrupted", "Retry the request.", exception);
        }
    }

    public void stream(LlmConfigVO config, String prompt, String modelOverride, Consumer<String> tokenConsumer) {
        validate(config);
        Map<String, Object> requestBody = requestBody(config, prompt, modelOverride, true);
        HttpRequest request = request(config, "text/event-stream", requestBody);
        try {
            HttpResponse<java.io.InputStream> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw upstreamException(response.statusCode(),
                        new String(response.body().readAllBytes(), StandardCharsets.UTF_8));
            }
            parseStream(response, tokenConsumer);
        } catch (HttpTimeoutException exception) {
            throw new LlmGatewayException(504, "llm.provider.timeout",
                    "LLM provider stream timed out",
                    "Check the provider base URL and network connectivity, then retry.", exception);
        } catch (IOException exception) {
            throw new LlmGatewayException(502, "llm.provider.io_error",
                    "Failed to stream from LLM provider",
                    "Check the provider endpoint, TLS settings, and network connectivity.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmGatewayException(502, "llm.provider.interrupted",
                    "LLM provider stream was interrupted", "Retry the request.", exception);
        }
    }

    private void parseStream(HttpResponse<java.io.InputStream> response, Consumer<String> tokenConsumer)
            throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            List<String> dataLines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (emitStreamEvent(dataLines, tokenConsumer)) {
                        return;
                    }
                    dataLines.clear();
                    continue;
                }
                if (line.startsWith("data:")) {
                    String value = line.substring(5);
                    dataLines.add(value.startsWith(" ") ? value.substring(1) : value);
                }
            }
            emitStreamEvent(dataLines, tokenConsumer);
        }
    }

    private boolean emitStreamEvent(List<String> dataLines, Consumer<String> tokenConsumer) {
        if (dataLines.isEmpty()) {
            return false;
        }
        String data = String.join("\n", dataLines);
        if ("[DONE]".equals(data)) {
            return true;
        }
        String token = parseDelta(data);
        if (StringUtils.hasText(token)) {
            tokenConsumer.accept(token);
        }
        return false;
    }

    private HttpRequest getRequest(LlmConfigVO config, URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .GET();
        if (StringUtils.hasText(config.getApiKey())) {
            builder.header("Authorization", "Bearer " + config.getApiKey().trim());
        }
        return builder.build();
    }

    private HttpRequest request(LlmConfigVO config, String accept, Map<String, Object> body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(chatCompletionsUri(config))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", accept)
                .POST(HttpRequest.BodyPublishers.ofString(writeJson(body), StandardCharsets.UTF_8));
        if (StringUtils.hasText(config.getApiKey())) {
            builder.header("Authorization", "Bearer " + config.getApiKey().trim());
        }
        return builder.build();
    }

    private Map<String, Object> requestBody(LlmConfigVO config, String prompt, String modelOverride,
                                            boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", StringUtils.hasText(modelOverride) ? modelOverride.trim() : config.getModel().trim());
        body.put("messages", List.of(Map.of(
                "role", "user",
                "content", StringUtils.hasText(prompt) ? prompt.trim() : "")));
        body.put("temperature", config.getTemperature());
        body.put("max_tokens", config.getMaxTokens());
        body.put("stream", stream);
        return body;
    }

    private URI chatCompletionsUri(LlmConfigVO config) {
        return providerUri(config, CHAT_COMPLETIONS_PATH);
    }

    private URI modelsUri(LlmConfigVO config) {
        return providerUri(config, MODELS_PATH);
    }

    private URI providerUri(LlmConfigVO config, String path) {
        String baseUrl = normalizeApiBase(config.getApiBase());
        return URI.create(baseUrl + path);
    }

    private String normalizeApiBase(String apiBase) {
        String baseUrl = apiBase.trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (baseUrl.endsWith(CHAT_COMPLETIONS_PATH)) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - CHAT_COMPLETIONS_PATH.length());
            while (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
        }
        return baseUrl;
    }

    private void validate(LlmConfigVO config) {
        validateModelListing(config);
        if (!StringUtils.hasText(config.getModel())) {
            throw new LlmGatewayException(400, "llm.config.missing_model",
                    "LLM model is required",
                    "Select or enter a model before sending a request.");
        }
    }

    private void validateModelListing(LlmConfigVO config) {
        if (!supports(config)) {
            throw new LlmGatewayException(400, "llm.config.unsupported_provider",
                    "LLM provider is not supported by the OpenAI-compatible gateway",
                    "Use one of: openai, deepseek, tongyi, ollama.");
        }
        if (!StringUtils.hasText(config.getApiBase())) {
            throw new LlmGatewayException(400, "llm.config.missing_api_base",
                    "LLM API base URL is required",
                    "Configure the provider base URL, for example https://api.openai.com/v1.");
        }
        if (!isValidApiBase(config.getApiBase())) {
            throw new LlmGatewayException(400, "llm.config.invalid_api_base",
                    "LLM API base URL is invalid",
                    "Use an http or https base URL such as https://api.openai.com/v1.");
        }
        if (!"ollama".equals(normalize(config.getProvider())) && !StringUtils.hasText(config.getApiKey())) {
            throw new LlmGatewayException(400, "llm.config.missing_api_key",
                    "LLM API key is required",
                    "Configure an API key for this provider, or select ollama for a local provider.");
        }
    }

    private boolean isValidApiBase(String apiBase) {
        try {
            URI uri = new URI(normalizeApiBase(apiBase));
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            return ("http".equals(scheme) || "https".equals(scheme)) && StringUtils.hasText(uri.getHost());
        } catch (IllegalArgumentException | URISyntaxException exception) {
            return false;
        }
    }

    private List<LlmModelItemVO> parseModels(String body) {
        if (!StringUtils.hasText(body)) {
            throw new LlmGatewayException(502, "llm.provider.empty_response",
                    "LLM provider returned an empty model response",
                    "Check whether the provider endpoint is compatible with OpenAI model listing.");
        }
        try {
            JsonNode data = objectMapper.readTree(body).path("data");
            if (!data.isArray()) {
                throw new LlmGatewayException(502, "llm.provider.malformed_response",
                        "LLM provider returned a malformed model response",
                        "Check whether the provider endpoint is compatible with OpenAI model listing.");
            }
            List<LlmModelItemVO> models = new ArrayList<>();
            data.forEach(item -> {
                String id = item.isTextual() ? item.asText() : item.path("id").asText();
                if (StringUtils.hasText(id)) {
                    models.add(new LlmModelItemVO(id, item.path("name").asText(id)));
                }
            });
            if (models.isEmpty()) {
                throw new LlmGatewayException(502, "llm.provider.empty_models",
                        "LLM provider returned no models",
                        "Check whether the configured account can access any models.");
            }
            return models;
        } catch (JsonProcessingException exception) {
            throw new LlmGatewayException(502, "llm.provider.malformed_response",
                    "LLM provider returned a non-JSON model response",
                    "Check whether the provider endpoint is compatible with OpenAI model listing.", exception);
        }
    }

    private String parseCompletion(String body) {
        if (!StringUtils.hasText(body)) {
            throw new LlmGatewayException(502, "llm.provider.empty_response",
                    "LLM provider returned an empty response",
                    "Check whether the provider endpoint is compatible with OpenAI chat completions.");
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            if (!StringUtils.hasText(content)) {
                throw new LlmGatewayException(502, "llm.provider.empty_completion",
                        "LLM provider returned an empty completion",
                        "Check the selected model and provider response format.");
            }
            return content;
        } catch (JsonProcessingException exception) {
            throw new LlmGatewayException(502, "llm.provider.malformed_response",
                    "LLM provider returned a non-JSON completion",
                    "Check whether the provider endpoint is compatible with OpenAI chat completions.", exception);
        }
    }

    private String parseDelta(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            return root.path("choices").path(0).path("delta").path("content").asText("");
        } catch (JsonProcessingException exception) {
            throw new LlmGatewayException(502, "llm.provider.malformed_stream_event",
                    "LLM provider returned a malformed stream event",
                    "Check whether the provider emits OpenAI-compatible SSE data events.", exception);
        }
    }

    private LlmGatewayException upstreamException(int statusCode, String body) {
        String message = "LLM provider request failed with status " + statusCode;
        try {
            JsonNode root = objectMapper.readTree(body);
            String errorMessage = root.path("error").path("message").asText();
            if (StringUtils.hasText(errorMessage)) {
                message += ": " + errorMessage;
            }
        } catch (JsonProcessingException ignored) {
            // Keep the status-only message when the upstream error is not JSON.
        }
        return new LlmGatewayException(statusCode, "llm.provider.upstream_error", message,
                "Check the provider credentials, model name, and account quota.");
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new LlmGatewayException(500, "llm.request.serialize_failed",
                    "Failed to serialize LLM request", "Check the request payload.", exception);
        }
    }

    private String normalize(String provider) {
        return provider == null ? "" : provider.trim().toLowerCase();
    }
}
