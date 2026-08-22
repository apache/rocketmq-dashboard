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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleLlmClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private String baseUrl;
    private OpenAiCompatibleLlmClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        client = new OpenAiCompatibleLlmClient(objectMapper);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void completeShouldCallOpenAiCompatibleChatCompletions() {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(objectMapper.readTree(exchange.getRequestBody()));
            respond(exchange, 200, """
                    {"choices":[{"message":{"content":"real response"}}]}
                    """, "application/json");
        });

        String result = client.complete(config("openai", "sk-test"), "hello", "gpt-4o-mini");

        assertThat(result).isEqualTo("real response");
        assertThat(authorization.get()).isEqualTo("Bearer sk-test");
        assertThat(requestBody.get().path("model").asText()).isEqualTo("gpt-4o-mini");
        assertThat(requestBody.get().path("stream").asBoolean()).isFalse();
        assertThat(requestBody.get().path("messages").path(0).path("role").asText()).isEqualTo("user");
        assertThat(requestBody.get().path("messages").path(0).path("content").asText()).isEqualTo("hello");
        assertThat(requestBody.get().path("max_tokens").asInt()).isEqualTo(256);
    }

    @Test
    void supportsShouldNormalizeProviderIndependentlyOfDefaultLocale() {
        Locale originalLocale = Locale.getDefault();

        boolean supported;
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            supported = client.supports(config("OPENAI", "sk-test"));
        } finally {
            Locale.setDefault(originalLocale);
        }

        assertThat(supported).isTrue();
    }

    @Test
    void completeShouldNormalizeFullChatCompletionsEndpoint() {
        AtomicReference<String> requestPath = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            respond(exchange, 200, """
                    {"choices":[{"message":{"content":"normalized response"}}]}
                    """, "application/json");
        });
        LlmConfigVO config = config("openai", "sk-test");
        config.setApiBase(baseUrl + "/chat/completions/");

        String result = client.complete(config, "hello", null);

        assertThat(result).isEqualTo("normalized response");
        assertThat(requestPath.get()).isEqualTo("/v1/chat/completions");
    }

    @Test
    void completeShouldRejectInvalidApiBaseBeforeCallingUpstream() {
        LlmConfigVO config = config("openai", "sk-test");
        config.setApiBase("ftp://api.openai.com/v1");

        assertThatThrownBy(() -> client.complete(config, "hello", null))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessage("LLM API base URL is invalid")
                .satisfies(exception -> {
                    LlmGatewayException gatewayException = (LlmGatewayException) exception;
                    assertThat(gatewayException.getStatusCode()).isEqualTo(400);
                    assertThat(gatewayException.getCode()).isEqualTo("llm.config.invalid_api_base");
                    assertThat(gatewayException.getHint()).contains("https://api.openai.com/v1");
                });
    }

    @Test
    void streamShouldParseOpenAiCompatibleSseDeltas() {
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(objectMapper.readTree(exchange.getRequestBody()));
            respond(exchange, 200, """
                    data: {"choices":[{"delta":{"content":"hel"}}]}

                    data: {"choices":[{"delta":{"content":"lo"}}]}

                    data: [DONE]

                    """, "text/event-stream");
        });

        List<String> tokens = new ArrayList<>();
        client.stream(config("deepseek", "sk-deepseek"), "hello", null, tokens::add);

        assertThat(tokens).containsExactly("hel", "lo");
        assertThat(requestBody.get().path("model").asText()).isEqualTo("gpt-test");
        assertThat(requestBody.get().path("stream").asBoolean()).isTrue();
    }

    @Test
    void streamShouldExposeErrorEnvelopeFromSuccessfulResponse() {
        server.createContext("/v1/chat/completions", exchange -> respond(exchange, 200, """
                data: {"error":{"message":"quota exceeded"}}

                data: [DONE]

                """, "text/event-stream"));

        assertThatThrownBy(() -> client.stream(
                config("openai", "sk-test"), "hello", null, token -> { }))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessage("LLM provider stream failed: quota exceeded")
                .satisfies(exception -> {
                    LlmGatewayException gatewayException = (LlmGatewayException) exception;
                    assertThat(gatewayException.getStatusCode()).isEqualTo(502);
                    assertThat(gatewayException.getCode()).isEqualTo("llm.provider.stream_error");
                    assertThat(gatewayException.getHint()).contains("account quota");
                });
    }

    @Test
    void streamShouldEnforceTimeoutWhileReadingResponseBody() {
        OpenAiCompatibleLlmClient timeoutClient = new OpenAiCompatibleLlmClient(
                objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(300)).build(),
                Duration.ofMillis(300));
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try {
                exchange.getResponseBody().write("""
                        data: {"choices":[{"delta":{"content":"first"}}]}

                        """.getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        List<String> tokens = new ArrayList<>();

        assertThatThrownBy(() -> timeoutClient.stream(
                config("openai", "sk-test"), "hello", null, tokens::add))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessage("LLM provider stream timed out")
                .satisfies(exception -> {
                    LlmGatewayException gatewayException = (LlmGatewayException) exception;
                    assertThat(gatewayException.getStatusCode()).isEqualTo(504);
                    assertThat(gatewayException.getCode()).isEqualTo("llm.provider.timeout");
                });
        assertThat(tokens).containsExactly("first");
    }

    @Test
    void ollamaShouldAllowMissingApiKeyAndOmitAuthorizationHeader() {
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/v1/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, """
                    {"choices":[{"message":{"content":"local response"}}]}
                    """, "application/json");
        });

        String result = client.complete(config("ollama", ""), "hello", null);

        assertThat(result).isEqualTo("local response");
        assertThat(authorization.get()).isNull();
    }

    @Test
    void listModelsShouldCallOpenAiCompatibleModelsEndpoint() {
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/v1/models", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, """
                    {"object":"list","data":[{"id":"gpt-4o"},{"id":"gpt-4o-mini","name":"GPT-4o Mini"}]}
                    """, "application/json");
        });

        List<LlmModelItemVO> models = client.listModels(config("openai", "sk-test"));

        assertThat(authorization.get()).isEqualTo("Bearer sk-test");
        assertThat(models).extracting("id").containsExactly("gpt-4o", "gpt-4o-mini");
        assertThat(models).extracting("name").containsExactly("gpt-4o", "GPT-4o Mini");
    }

    @Test
    void listModelsShouldRejectMalformedProviderResponse() {
        server.createContext("/v1/models", exchange -> respond(exchange, 200, """
                {"object":"list","items":[]}
                """, "application/json"));

        assertThatThrownBy(() -> client.listModels(config("openai", "sk-test")))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessage("LLM provider returned a malformed model response")
                .satisfies(exception -> {
                    LlmGatewayException gatewayException = (LlmGatewayException) exception;
                    assertThat(gatewayException.getStatusCode()).isEqualTo(502);
                    assertThat(gatewayException.getCode()).isEqualTo("llm.provider.malformed_response");
                    assertThat(gatewayException.getHint()).contains("OpenAI model listing");
                });
    }

    @Test
    void completeShouldExposeUpstreamErrorMessage() {
        server.createContext("/v1/chat/completions", exchange -> respond(exchange, 401, """
                {"error":{"message":"invalid api key"}}
                """, "application/json"));

        assertThatThrownBy(() -> client.complete(config("openai", "sk-test"), "hello", null))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessage("LLM provider request failed with status 401: invalid api key")
                .satisfies(exception -> {
                    LlmGatewayException gatewayException = (LlmGatewayException) exception;
                    assertThat(gatewayException.getStatusCode()).isEqualTo(401);
                    assertThat(gatewayException.getCode()).isEqualTo("llm.provider.upstream_error");
                    assertThat(gatewayException.getHint()).contains("provider credentials");
                });
    }

    @Test
    void unsupportedProviderShouldFailBeforeCallingUpstream() {
        assertThatThrownBy(() -> client.complete(config("bedrock", "key"), "hello", null))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessage("LLM provider is not supported by the OpenAI-compatible gateway")
                .satisfies(exception -> {
                    LlmGatewayException gatewayException = (LlmGatewayException) exception;
                    assertThat(gatewayException.getStatusCode()).isEqualTo(400);
                    assertThat(gatewayException.getCode()).isEqualTo("llm.config.unsupported_provider");
                    assertThat(gatewayException.getHint()).contains("openai");
                });
    }

    @Test
    void completeShouldRejectEmptyProviderResponse() {
        server.createContext("/v1/chat/completions", exchange -> respond(exchange, 200, "", "application/json"));

        assertThatThrownBy(() -> client.complete(config("openai", "sk-test"), "hello", null))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessage("LLM provider returned an empty response")
                .satisfies(exception -> {
                    LlmGatewayException gatewayException = (LlmGatewayException) exception;
                    assertThat(gatewayException.getStatusCode()).isEqualTo(502);
                    assertThat(gatewayException.getCode()).isEqualTo("llm.provider.empty_response");
                    assertThat(gatewayException.getHint()).contains("OpenAI chat completions");
                });
    }

    @Test
    void completeShouldRejectNonJsonProviderResponse() {
        server.createContext("/v1/chat/completions",
                exchange -> respond(exchange, 200, "not-json", "text/plain"));

        assertThatThrownBy(() -> client.complete(config("openai", "sk-test"), "hello", null))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessage("LLM provider returned a non-JSON completion")
                .satisfies(exception -> {
                    LlmGatewayException gatewayException = (LlmGatewayException) exception;
                    assertThat(gatewayException.getStatusCode()).isEqualTo(502);
                    assertThat(gatewayException.getCode()).isEqualTo("llm.provider.malformed_response");
                    assertThat(gatewayException.getHint()).contains("OpenAI chat completions");
                });
    }

    @Test
    void completeShouldRejectOversizedProviderResponse() {
        client = clientWithLimit(1024);
        String body = completionBody(1025);
        server.createContext("/v1/chat/completions",
                exchange -> respond(exchange, 200, body, "application/json"));

        assertThatThrownBy(() -> client.complete(config("openai", "sk-test"), "hello", null))
                .isInstanceOfSatisfying(LlmGatewayException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(502);
                    assertThat(exception.getCode()).isEqualTo("llm.provider.response_too_large");
                    assertThat(exception.getMessage()).contains("1024 bytes");
                });
    }

    @Test
    void completeShouldAllowProviderResponseAtConfiguredLimit() {
        client = clientWithLimit(1024);
        String body = completionBody(1024);
        server.createContext("/v1/chat/completions",
                exchange -> respond(exchange, 200, body, "application/json"));

        String result = client.complete(config("openai", "sk-test"), "hello", null);

        assertThat(result).isNotEmpty();
    }

    @Test
    void listModelsShouldRejectOversizedProviderResponse() {
        client = clientWithLimit(1024);
        server.createContext("/v1/models",
                exchange -> respond(exchange, 200, "x".repeat(1025), "application/json"));

        assertThatThrownBy(() -> client.listModels(config("openai", "sk-test")))
                .isInstanceOfSatisfying(LlmGatewayException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("llm.provider.response_too_large"));
    }

    @Test
    void streamShouldRejectOversizedUpstreamErrorResponse() {
        client = clientWithLimit(1024);
        server.createContext("/v1/chat/completions",
                exchange -> respond(exchange, 500, "x".repeat(1025), "application/json"));

        assertThatThrownBy(() -> client.stream(config("openai", "sk-test"), "hello", null, token -> { }))
                .isInstanceOfSatisfying(LlmGatewayException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("llm.provider.response_too_large"));
    }

    @Test
    void streamShouldRejectOversizedSuccessfulResponse() {
        client = clientWithLimit(1024);
        String body = "data: {\"choices\":[{\"delta\":{\"content\":\""
                + "x".repeat(1024)
                + "\"}}]}\n\n";
        server.createContext("/v1/chat/completions",
                exchange -> respond(exchange, 200, body, "text/event-stream"));

        assertThatThrownBy(() -> client.stream(config("openai", "sk-test"), "hello", null, token -> { }))
                .isInstanceOfSatisfying(LlmGatewayException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(502);
                    assertThat(exception.getCode()).isEqualTo("llm.provider.response_too_large");
                    assertThat(exception.getMessage()).contains("1024 bytes");
                });
    }

    @Test
    void completeShouldRejectOversizedPromptBeforeCallingUpstream() {
        assertThatThrownBy(() -> client.complete(
                config("openai", "sk-test"),
                "x".repeat(AiPayloadGuard.MAX_OUTBOUND_PROMPT_BYTES + 1),
                null))
                .isInstanceOfSatisfying(LlmGatewayException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(400);
                    assertThat(exception.getCode()).isEqualTo("llm.request.payload_too_large");
                });
    }

    @Test
    void completeShouldRejectOversizedConfiguredModel() {
        LlmConfigVO config = config("openai", "sk-test");
        config.setModel("x".repeat(AiPayloadGuard.MAX_MODEL_BYTES + 1));

        assertThatThrownBy(() -> client.complete(config, "hello", null))
                .isInstanceOfSatisfying(LlmGatewayException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("llm.request.payload_too_large"));
    }

    @Test
    void completeShouldExposeTimeoutAsGatewayTimeout() {
        OpenAiCompatibleLlmClient timeoutClient = new OpenAiCompatibleLlmClient(
                objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(100)).build(),
                Duration.ofMillis(100));
        server.createContext("/v1/chat/completions", exchange -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, """
                    {"choices":[{"message":{"content":"late response"}}]}
                    """, "application/json");
        });

        assertThatThrownBy(() -> timeoutClient.complete(config("openai", "sk-test"), "hello", null))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessage("LLM provider request timed out")
                .satisfies(exception -> {
                    LlmGatewayException gatewayException = (LlmGatewayException) exception;
                    assertThat(gatewayException.getStatusCode()).isEqualTo(504);
                    assertThat(gatewayException.getCode()).isEqualTo("llm.provider.timeout");
                    assertThat(gatewayException.getHint()).contains("network connectivity");
                });
    }

    private LlmConfigVO config(String provider, String apiKey) {
        return LlmConfigVO.builder()
                .provider(provider)
                .apiKey(apiKey)
                .apiBase(baseUrl)
                .model("gpt-test")
                .maxTokens(256)
                .temperature(0.2)
                .enabled(true)
                .build();
    }

    private OpenAiCompatibleLlmClient clientWithLimit(int limitBytes) {
        return new OpenAiCompatibleLlmClient(objectMapper) {
            @Override
            int responseBodyLimitBytes() {
                return limitBytes;
            }
        };
    }

    private String completionBody(int length) {
        String prefix = "{\"choices\":[{\"message\":{\"content\":\"";
        String suffix = "\"}}]}";
        return prefix + "x".repeat(length - prefix.length() - suffix.length()) + suffix;
    }

    private void respond(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
