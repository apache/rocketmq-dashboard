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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
    void completeShouldExposeUpstreamErrorMessage() {
        server.createContext("/v1/chat/completions", exchange -> respond(exchange, 401, """
                {"error":{"message":"invalid api key"}}
                """, "application/json"));

        assertThatThrownBy(() -> client.complete(config("openai", "sk-test"), "hello", null))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessage("LLM provider request failed with status 401: invalid api key");
    }

    @Test
    void unsupportedProviderShouldFailBeforeCallingUpstream() {
        assertThatThrownBy(() -> client.complete(config("bedrock", "key"), "hello", null))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessage("LLM provider is not supported by the OpenAI-compatible gateway");
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

    private void respond(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
