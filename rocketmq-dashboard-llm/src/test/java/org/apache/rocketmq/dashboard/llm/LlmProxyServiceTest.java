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

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import com.sun.net.httpserver.HttpServer;
import org.apache.rocketmq.dashboard.cli.schema.ParamSchema;
import org.apache.rocketmq.dashboard.cli.schema.RiskLevel;
import org.apache.rocketmq.dashboard.cli.schema.ToolDefinition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for LlmProxyService using a local HTTP server.
 * Covers chat, chatWithMessages, chatStream, listModels, resolveFunctionName,
 * and private methods via reflection (buildChatUrl, buildModelsUrl, addAuthHeaders,
 * parseChatResponse, buildChatRequest, mapTypeToJsonSchema, escapeJson, buildError).
 */
public class LlmProxyServiceTest {

    private LlmProxyService service;
    private HttpServer server;
    private int port;

    @Before
    public void setUp() throws Exception {
        service = new LlmProxyService();
        // Start a local HTTP server on a random available port
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.setExecutor(null);
        server.start();
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ==================== Helper methods ====================

    private LlmConfig createConfig(String provider) {
        LlmConfig config = new LlmConfig();
        config.setEnabled(true);
        config.setApiKey("test-api-key");
        config.setProvider(provider);
        config.setModel("gpt-4");
        config.setMaxTokens(4096);
        config.setTemperature(0.0);
        config.setApiBase("http://127.0.0.1:" + port);
        return config;
    }

    private LlmConfig createDisabledConfig() {
        LlmConfig config = new LlmConfig();
        config.setEnabled(false);
        config.setApiKey("");
        return config;
    }

    private List<ToolDefinition> createTestTools() {
        List<ParamSchema> params = new ArrayList<>();
        params.add(ParamSchema.builder()
                .name("topic")
                .type("STRING")
                .required(true)
                .description("Topic name")
                .build());
        params.add(ParamSchema.builder()
                .name("cluster")
                .type("STRING")
                .required(false)
                .description("Cluster name")
                .defaultValue("DefaultCluster")
                .build());
        params.add(ParamSchema.builder()
                .name("mode")
                .type("ENUM")
                .required(false)
                .description("Mode")
                .allowedValues(new String[]{"read", "write"})
                .build());

        return List.of(ToolDefinition.builder()
                .name("rmq.topic.create")
                .resource("topic")
                .verb("create")
                .riskLevel(RiskLevel.L2)
                .description("Create a topic")
                .params(params)
                .returnType("VOID")
                .build());
    }

    private void registerOkHandler(String path, String responseBody) {
        server.createContext(path, exchange -> {
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
    }

    private void registerSseHandler(String path, String... dataLines) {
        server.createContext(path, exchange -> {
            StringBuilder sb = new StringBuilder();
            for (String line : dataLines) {
                sb.append("data: ").append(line).append("\n\n");
            }
            sb.append("data: [DONE]\n\n");
            byte[] response = sb.toString().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
    }

    private void registerErrorHandler(String path, int statusCode, String body) {
        server.createContext(path, exchange -> {
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
    }

    private void registerInputStreamHandler(String path, int statusCode, String body) {
        server.createContext(path, exchange -> {
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
    }

    private Object invokePrivate(String methodName, Class<?>[] paramTypes, Object[] args) throws Exception {
        Method method = LlmProxyService.class.getDeclaredMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(service, args);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokePrivateMap(String methodName, Class<?>[] paramTypes, Object[] args) throws Exception {
        return (Map<String, Object>) invokePrivate(methodName, paramTypes, args);
    }

    // ==================== chat() tests ====================

    @Test
    public void testChat_disabledConfig_returnsError() {
        String result = service.chat("hello", null, createDisabledConfig());
        assertTrue(result.contains("error"));
        assertTrue(result.contains("not configured"));
    }

    @Test
    public void testChat_emptyApiKey_returnsError() {
        LlmConfig config = new LlmConfig();
        config.setEnabled(true);
        config.setApiKey("");
        String result = service.chat("hello", null, config);
        assertTrue(result.contains("not configured"));
    }

    @Test
    public void testChat_nullApiKey_returnsError() {
        LlmConfig config = new LlmConfig();
        config.setEnabled(true);
        config.setApiKey(null);
        String result = service.chat("hello", null, config);
        assertTrue(result.contains("not configured"));
    }

    @Test
    public void testChat_success_returnsContent() {
        String chatResponse = "{\"choices\":[{\"message\":{\"content\":\"Hello!\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":2}}";
        registerOkHandler("/chat/completions", chatResponse);

        String result = service.chat("hello", null, createConfig("DEFAULT"));
        assertTrue(result.contains("Hello!"));
        assertTrue(result.contains("stop"));
    }

    @Test
    public void testChat_withToolCalls_returnsToolCalls() {
        String chatResponse = "{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":[{\"id\":\"call_1\",\"function\":{\"name\":\"rmq_topic_create\",\"arguments\":\"{\\\"topic\\\":\\\"test\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}";
        registerOkHandler("/chat/completions", chatResponse);

        String result = service.chat("create topic test", createTestTools(), createConfig("DEFAULT"));
        assertTrue(result.contains("toolCalls"));
        assertTrue(result.contains("rmq.topic.create"));
    }

    @Test
    public void testChat_httpError_returnsError() {
        registerErrorHandler("/chat/completions", 429, "{\"error\":\"rate limited\"}");
        String result = service.chat("hello", null, createConfig("DEFAULT"));
        assertTrue(result.contains("error"));
        assertTrue(result.contains("429"));
    }

    @Test
    public void testChat_connectionFailure_returnsError() {
        LlmConfig config = createConfig("DEFAULT");
        config.setApiBase("http://127.0.0.1:1"); // Port 1 - no server
        String result = service.chat("hello", null, config);
        assertTrue(result.contains("error"));
    }

    // ==================== chatWithMessages() tests ====================

    @Test
    public void testChatWithMessages_disabledConfig_returnsError() {
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "user");
        msg.put("content", "hello");
        messages.add(msg);
        String result = service.chatWithMessages(messages, null, createDisabledConfig());
        assertTrue(result.contains("not configured"));
    }

    @Test
    public void testChatWithMessages_success() {
        String chatResponse = "{\"choices\":[{\"message\":{\"content\":\"Response\"},\"finish_reason\":\"stop\"}]}";
        registerOkHandler("/chat/completions", chatResponse);

        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "user");
        msg.put("content", "hello");
        messages.add(msg);

        String result = service.chatWithMessages(messages, null, createConfig("DEFAULT"));
        assertTrue(result.contains("Response"));
    }

    // ==================== chatStream() tests ====================

    @Test
    public void testChatStream_disabledConfig_callbackReceivesError() {
        List<String> chunks = new ArrayList<>();
        Consumer<String> callback = chunks::add;
        List<Map<String, Object>> messages = new ArrayList<>();
        service.chatStream(messages, null, createDisabledConfig(), callback);
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("not configured"));
    }

    @Test
    public void testChatStream_success_callbackReceivesChunks() {
        registerSseHandler("/chat/completions",
                "{\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}",
                "{\"choices\":[{\"delta\":{\"content\":\" world\"}}]}");

        List<String> chunks = new ArrayList<>();
        Consumer<String> callback = chunks::add;
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "user");
        msg.put("content", "hi");
        messages.add(msg);

        service.chatStream(messages, null, createConfig("DEFAULT"), callback);
        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).contains("Hello"));
        assertTrue(chunks.get(1).contains("world"));
    }

    @Test
    public void testChatStream_httpError_callbackReceivesError() {
        registerErrorHandler("/chat/completions", 500, "{\"error\":{\"message\":\"server error\"}}");

        List<String> chunks = new ArrayList<>();
        Consumer<String> callback = chunks::add;
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "user");
        msg.put("content", "hi");
        messages.add(msg);

        service.chatStream(messages, null, createConfig("DEFAULT"), callback);
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("error"));
    }

    @Test
    public void testChatStream_connectionFailure_callbackReceivesError() {
        List<String> chunks = new ArrayList<>();
        Consumer<String> callback = chunks::add;
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "user");
        msg.put("content", "hi");
        messages.add(msg);

        LlmConfig config = createConfig("DEFAULT");
        config.setApiBase("http://127.0.0.1:1");
        service.chatStream(messages, null, config, callback);
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("error"));
    }

    @Test
    public void testChatStream_httpError_unparseableBody() {
        registerErrorHandler("/chat/completions", 502, "bad gateway html");

        List<String> chunks = new ArrayList<>();
        Consumer<String> callback = chunks::add;
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "user");
        msg.put("content", "hi");
        messages.add(msg);

        service.chatStream(messages, null, createConfig("DEFAULT"), callback);
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("error"));
    }

    // ==================== listModels() tests ====================

    @Test
    public void testListModels_disabledConfig_returnsEmptyList() {
        List<Map<String, Object>> result = service.listModels(createDisabledConfig());
        assertTrue(result.isEmpty());
    }

    @Test
    public void testListModels_success_returnsModelList() {
        String modelsResponse = "{\"data\":[{\"id\":\"gpt-4\"},{\"id\":\"gpt-3.5-turbo\"}]}";
        registerOkHandler("/models", modelsResponse);

        LlmConfig config = createConfig("OPENAI");
        config.setApiBase("http://127.0.0.1:" + port);
        List<Map<String, Object>> result = service.listModels(config);
        assertEquals(2, result.size());
        assertEquals("gpt-4", result.get(0).get("id"));
        assertEquals("gpt-3.5-turbo", result.get(1).get("id"));
    }

    @Test
    public void testListModels_httpError_returnsEmptyList() {
        registerErrorHandler("/models", 500, "error");

        LlmConfig config = createConfig("OPENAI");
        config.setApiBase("http://127.0.0.1:" + port);
        List<Map<String, Object>> result = service.listModels(config);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testListModels_connectionFailure_returnsEmptyList() {
        LlmConfig config = createConfig("OPENAI");
        config.setApiBase("http://127.0.0.1:1");
        List<Map<String, Object>> result = service.listModels(config);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testListModels_nullData_returnsEmptyList() {
        String modelsResponse = "{}";
        registerOkHandler("/models", modelsResponse);

        LlmConfig config = createConfig("OPENAI");
        config.setApiBase("http://127.0.0.1:" + port);
        List<Map<String, Object>> result = service.listModels(config);
        assertTrue(result.isEmpty());
    }

    // ==================== resolveFunctionName() tests ====================

    @Test
    public void testResolveFunctionName_knownMapping() throws Exception {
        // First call chat with tools to populate functionNameToToolName map
        String chatResponse = "{\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}";
        registerOkHandler("/chat/completions", chatResponse);
        service.chat("test", createTestTools(), createConfig("DEFAULT"));

        // The tool name rmq.topic.create should be mapped from rmq_topic_create
        String resolved = service.resolveFunctionName("rmq_topic_create");
        assertEquals("rmq.topic.create", resolved);
    }

    @Test
    public void testResolveFunctionName_unknown_returnsSameName() {
        String resolved = service.resolveFunctionName("unknown_function");
        assertEquals("unknown_function", resolved);
    }

    // ==================== buildChatUrl() via reflection ====================

    @Test
    public void testBuildChatUrl_openai() throws Exception {
        LlmConfig config = new LlmConfig();
        config.setProvider("OPENAI");
        String url = (String) invokePrivate("buildChatUrl", new Class[]{LlmConfig.class}, new Object[]{config});
        assertEquals("https://api.openai.com/v1/chat/completions", url);
    }

    @Test
    public void testBuildChatUrl_azure() throws Exception {
        LlmConfig config = new LlmConfig();
        config.setProvider("AZURE");
        config.setApiBase("https://my-resource.openai.azure.com");
        config.setModel("my-deployment");
        String url = (String) invokePrivate("buildChatUrl", new Class[]{LlmConfig.class}, new Object[]{config});
        assertEquals("https://my-resource.openai.azure.com/openai/deployments/my-deployment/chat/completions", url);
    }

    @Test
    public void testBuildChatUrl_deepseek() throws Exception {
        LlmConfig config = new LlmConfig();
        config.setProvider("DEEPSEEK");
        String url = (String) invokePrivate("buildChatUrl", new Class[]{LlmConfig.class}, new Object[]{config});
        assertEquals("https://api.deepseek.com/v1/chat/completions", url);
    }

    @Test
    public void testBuildChatUrl_tongyi() throws Exception {
        LlmConfig config = new LlmConfig();
        config.setProvider("TONGYI");
        String url = (String) invokePrivate("buildChatUrl", new Class[]{LlmConfig.class}, new Object[]{config});
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", url);
    }

    @Test
    public void testBuildChatUrl_bedrock() throws Exception {
        LlmConfig config = new LlmConfig();
        config.setProvider("BEDROCK");
        config.setApiBase("https://bedrock.us-east-1.amazonaws.com");
        String url = (String) invokePrivate("buildChatUrl", new Class[]{LlmConfig.class}, new Object[]{config});
        assertEquals("https://bedrock.us-east-1.amazonaws.com/chat/completions", url);
    }

    @Test
    public void testBuildChatUrl_ollama() throws Exception {
        LlmConfig config = new LlmConfig();
        config.setProvider("OLLAMA");
        config.setApiBase("http://localhost:11434");
        String url = (String) invokePrivate("buildChatUrl", new Class[]{LlmConfig.class}, new Object[]{config});
        assertEquals("http://localhost:11434/api/chat", url);
    }

    @Test
    public void testBuildChatUrl_ollama_defaultBase() throws Exception {
        LlmConfig config = new LlmConfig();
        config.setProvider("OLLAMA");
        config.setApiBase(null);
        String url = (String) invokePrivate("buildChatUrl", new Class[]{LlmConfig.class}, new Object[]{config});
        assertEquals("http://localhost:11434/api/chat", url);
    }

    @Test
    public void testBuildChatUrl_customProvider() throws Exception {
        LlmConfig config = new LlmConfig();
        config.setProvider("CUSTOM");
        config.setApiBase("https://custom.llm.api");
        String url = (String) invokePrivate("buildChatUrl", new Class[]{LlmConfig.class}, new Object[]{config});
        assertEquals("https://custom.llm.api/chat/completions", url);
    }

    @Test
    public void testBuildChatUrl_nullProvider_defaultsToOpenai() throws Exception {
        LlmConfig config = new LlmConfig();
        config.setProvider(null);
        String url = (String) invokePrivate("buildChatUrl", new Class[]{LlmConfig.class}, new Object[]{config});
        assertEquals("https://api.openai.com/v1/chat/completions", url);
    }

    @Test
    public void testBuildChatUrl_azure_trailingSlash() throws Exception {
        LlmConfig config = new LlmConfig();
        config.setProvider("AZURE");
        config.setApiBase("https://my-resource.openai.azure.com/");
        config.setModel("my-model");
        String url = (String) invokePrivate("buildChatUrl", new Class[]{LlmConfig.class}, new Object[]{config});
        assertEquals("https://my-resource.openai.azure.com/openai/deployments/my-model/chat/completions", url);
    }

    @Test
    public void testBuildChatUrl_azure_nullModel_defaultsToGpt4() throws Exception {
        LlmConfig config = new LlmConfig();
        config.setProvider("AZURE");
        config.setApiBase("https://my-resource.openai.azure.com");
        config.setModel(null);
        String url = (String) invokePrivate("buildChatUrl", new Class[]{LlmConfig.class}, new Object[]{config});
        assertEquals("https://my-resource.openai.azure.com/openai/deployments/gpt-4/chat/completions", url);
    }

    // ==================== buildModelsUrl() via reflection ====================

    @Test
    public void testBuildModelsUrl_openai() throws Exception {
        LlmConfig config = new LlmConfig();
        config.setProvider("OPENAI");
        config.setApiBase(null);
        String url = (String) invokePrivate("buildModelsUrl", new Class[]{LlmConfig.class}, new Object[]{config});
        assertEquals("https://api.openai.com/v1/models", url);
    }

    @Test
    public void testBuildModelsUrl_deepseek() throws Exception {
        LlmConfig config = new LlmConfig();
        config.setProvider("DEEPSEEK");
        config.setApiBase(null);
        String url = (String) invokePrivate("buildModelsUrl", new Class[]{LlmConfig.class}, new Object[]{config});
        assertEquals("https://api.deepseek.com/v1/models", url);
    }

    @Test
    public void testBuildModelsUrl_tongyi() throws Exception {
        LlmConfig config = new LlmConfig();
        config.setProvider("TONGYI");
        config.setApiBase(null);
        String url = (String) invokePrivate("buildModelsUrl", new Class[]{LlmConfig.class}, new Object[]{config});
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1/models", url);
    }

    @Test
    public void testBuildModelsUrl_ollama() throws Exception {
        LlmConfig config = new LlmConfig();
        config.setProvider("OLLAMA");
        config.setApiBase("http://localhost:11434");
        String url = (String) invokePrivate("buildModelsUrl", new Class[]{LlmConfig.class}, new Object[]{config});
        assertEquals("http://localhost:11434/api/tags", url);
    }

    @Test
    public void testBuildModelsUrl_azure() throws Exception {
        LlmConfig config = new LlmConfig();
        config.setProvider("AZURE");
        config.setApiBase("https://my-resource.openai.azure.com");
        String url = (String) invokePrivate("buildModelsUrl", new Class[]{LlmConfig.class}, new Object[]{config});
        assertEquals("https://my-resource.openai.azure.com/openai/models", url);
    }

    @Test
    public void testBuildModelsUrl_customBase() throws Exception {
        LlmConfig config = new LlmConfig();
        config.setProvider("OPENAI");
        config.setApiBase("https://custom.api.com");
        String url = (String) invokePrivate("buildModelsUrl", new Class[]{LlmConfig.class}, new Object[]{config});
        assertEquals("https://custom.api.com/models", url);
    }

    @Test
    public void testBuildModelsUrl_ollama_defaultBase() throws Exception {
        LlmConfig config = new LlmConfig();
        config.setProvider("OLLAMA");
        config.setApiBase(null);
        String url = (String) invokePrivate("buildModelsUrl", new Class[]{LlmConfig.class}, new Object[]{config});
        assertEquals("http://localhost:11434/api/tags", url);
    }

    @Test
    public void testBuildModelsUrl_unknownProvider_emptyBase() throws Exception {
        LlmConfig config = new LlmConfig();
        config.setProvider("UNKNOWN");
        config.setApiBase(null);
        String url = (String) invokePrivate("buildModelsUrl", new Class[]{LlmConfig.class}, new Object[]{config});
        assertEquals("/v1/models", url);
    }

    // ==================== addAuthHeaders() via reflection ====================

    @Test
    public void testAddAuthHeaders_default_bearer() throws Exception {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost/test"))
                .GET();
        LlmConfig config = new LlmConfig();
        config.setProvider("OPENAI");
        config.setApiKey("sk-test123");

        invokePrivate("addAuthHeaders", new Class[]{java.net.http.HttpRequest.Builder.class, LlmConfig.class},
                new Object[]{builder, config});

        java.net.http.HttpRequest request = builder.build();
        assertEquals("Bearer sk-test123", request.headers().firstValue("Authorization").orElse(""));
    }

    @Test
    public void testAddAuthHeaders_azure_apiKey() throws Exception {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost/test"))
                .GET();
        LlmConfig config = new LlmConfig();
        config.setProvider("AZURE");
        config.setApiKey("azure-key-123");

        invokePrivate("addAuthHeaders", new Class[]{java.net.http.HttpRequest.Builder.class, LlmConfig.class},
                new Object[]{builder, config});

        java.net.http.HttpRequest request = builder.build();
        assertEquals("azure-key-123", request.headers().firstValue("api-key").orElse(""));
        // Azure should NOT have Authorization header
        assertFalse(request.headers().firstValue("Authorization").isPresent());
    }

    @Test
    public void testAddAuthHeaders_ollama_withKey() throws Exception {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost/test"))
                .GET();
        LlmConfig config = new LlmConfig();
        config.setProvider("OLLAMA");
        config.setApiKey("ollama-key");

        invokePrivate("addAuthHeaders", new Class[]{java.net.http.HttpRequest.Builder.class, LlmConfig.class},
                new Object[]{builder, config});

        java.net.http.HttpRequest request = builder.build();
        assertEquals("Bearer ollama-key", request.headers().firstValue("Authorization").orElse(""));
    }

    @Test
    public void testAddAuthHeaders_ollama_noKey() throws Exception {
        java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost/test"))
                .GET();
        LlmConfig config = new LlmConfig();
        config.setProvider("OLLAMA");
        config.setApiKey("");

        invokePrivate("addAuthHeaders", new Class[]{java.net.http.HttpRequest.Builder.class, LlmConfig.class},
                new Object[]{builder, config});

        java.net.http.HttpRequest request = builder.build();
        // Ollama without key should NOT have Authorization header
        assertFalse(request.headers().firstValue("Authorization").isPresent());
    }

    // ==================== parseChatResponse() via reflection ====================

    @Test
    public void testParseChatResponse_withContent() throws Exception {
        String response = "{\"choices\":[{\"message\":{\"content\":\"Hello!\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":5}}";
        String result = (String) invokePrivate("parseChatResponse", new Class[]{String.class}, new Object[]{response});
        assertTrue(result.contains("Hello!"));
        assertTrue(result.contains("stop"));
    }

    @Test
    public void testParseChatResponse_withToolCalls() throws Exception {
        String response = "{\"choices\":[{\"message\":{\"tool_calls\":[{\"id\":\"call_1\",\"function\":{\"name\":\"test_func\",\"arguments\":\"{\\\"key\\\":\\\"val\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}";
        String result = (String) invokePrivate("parseChatResponse", new Class[]{String.class}, new Object[]{response});
        assertTrue(result.contains("toolCalls"));
        assertTrue(result.contains("test_func"));
    }

    @Test
    public void testParseChatResponse_withToolCalls_invalidArguments() throws Exception {
        String response = "{\"choices\":[{\"message\":{\"tool_calls\":[{\"id\":\"call_1\",\"function\":{\"name\":\"test_func\",\"arguments\":\"not-valid-json\"}}]},\"finish_reason\":\"tool_calls\"}]}";
        String result = (String) invokePrivate("parseChatResponse", new Class[]{String.class}, new Object[]{response});
        assertTrue(result.contains("toolCalls"));
        assertTrue(result.contains("not-valid-json"));
    }

    @Test
    public void testParseChatResponse_emptyChoices() throws Exception {
        String response = "{\"choices\":[]}";
        String result = (String) invokePrivate("parseChatResponse", new Class[]{String.class}, new Object[]{response});
        assertNotNull(result);
    }

    @Test
    public void testParseChatResponse_nullChoices() throws Exception {
        String response = "{}";
        String result = (String) invokePrivate("parseChatResponse", new Class[]{String.class}, new Object[]{response});
        assertNotNull(result);
    }

    @Test
    public void testParseChatResponse_nullMessage() throws Exception {
        String response = "{\"choices\":[{\"message\":null}]}";
        String result = (String) invokePrivate("parseChatResponse", new Class[]{String.class}, new Object[]{response});
        assertNotNull(result);
    }

    @Test
    public void testParseChatResponse_withUsage() throws Exception {
        String response = "{\"choices\":[{\"message\":{\"content\":\"Hi\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}";
        String result = (String) invokePrivate("parseChatResponse", new Class[]{String.class}, new Object[]{response});
        assertTrue(result.contains("usage"));
    }

    @Test
    public void testParseChatResponse_invalidJson() throws Exception {
        String response = "not valid json {{{";
        String result = (String) invokePrivate("parseChatResponse", new Class[]{String.class}, new Object[]{response});
        assertTrue(result.contains("content"));
    }

    // ==================== buildChatRequest() via reflection ====================

    @Test
    @SuppressWarnings("unchecked")
    public void testBuildChatRequest_basicFields() throws Exception {
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "user");
        msg.put("content", "hello");
        messages.add(msg);

        LlmConfig config = new LlmConfig();
        config.setModel("gpt-4");
        config.setMaxTokens(4096);
        config.setTemperature(0.5);

        Map<String, Object> result = invokePrivateMap("buildChatRequest",
                new Class[]{List.class, List.class, LlmConfig.class, boolean.class},
                new Object[]{messages, null, config, false});

        assertEquals("gpt-4", result.get("model"));
        assertEquals(4096, result.get("max_tokens"));
        assertEquals(0.5, result.get("temperature"));
        assertEquals(false, result.get("stream"));
        assertEquals(messages, result.get("messages"));
        assertNull(result.get("tools"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBuildChatRequest_withTools() throws Exception {
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "user");
        msg.put("content", "hello");
        messages.add(msg);

        LlmConfig config = new LlmConfig();
        config.setModel("gpt-4");
        config.setMaxTokens(4096);
        config.setTemperature(0.0);

        Map<String, Object> result = invokePrivateMap("buildChatRequest",
                new Class[]{List.class, List.class, LlmConfig.class, boolean.class},
                new Object[]{messages, createTestTools(), config, true});

        assertEquals(true, result.get("stream"));
        assertNotNull(result.get("tools"));
        List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
        assertEquals(1, tools.size());
        assertEquals("function", tools.get(0).get("type"));
        Map<String, Object> function = (Map<String, Object>) tools.get(0).get("function");
        assertEquals("rmq_topic_create", function.get("name"));
        assertEquals("auto", result.get("tool_choice"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBuildChatRequest_toolParams() throws Exception {
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "user");
        msg.put("content", "hello");
        messages.add(msg);

        LlmConfig config = new LlmConfig();
        config.setModel(null);
        config.setMaxTokens(2048);
        config.setTemperature(0.7);

        Map<String, Object> result = invokePrivateMap("buildChatRequest",
                new Class[]{List.class, List.class, LlmConfig.class, boolean.class},
                new Object[]{messages, createTestTools(), config, false});

        assertEquals("gpt-4", result.get("model")); // null model defaults to gpt-4
        Map<String, Object> function = (Map<String, Object>) ((List<Map<String, Object>>) result.get("tools")).get(0).get("function");
        Map<String, Object> parameters = (Map<String, Object>) function.get("parameters");
        Map<String, Object> properties = (Map<String, Object>) parameters.get("properties");
        assertEquals(3, properties.size());
        assertTrue(properties.containsKey("topic"));
        assertTrue(properties.containsKey("cluster"));
        assertTrue(properties.containsKey("mode"));

        // Check required params
        List<String> required = (List<String>) parameters.get("required");
        assertEquals(1, required.size());
        assertEquals("topic", required.get(0));

        // Check enum
        Map<String, Object> modeProp = (Map<String, Object>) properties.get("mode");
        assertTrue(modeProp.containsKey("enum"));

        // Check default value
        Map<String, Object> clusterProp = (Map<String, Object>) properties.get("cluster");
        assertEquals("DefaultCluster", clusterProp.get("default"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBuildChatRequest_emptyTools() throws Exception {
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "user");
        msg.put("content", "hello");
        messages.add(msg);

        LlmConfig config = new LlmConfig();
        config.setModel("gpt-4");
        config.setMaxTokens(4096);
        config.setTemperature(0.0);

        Map<String, Object> result = invokePrivateMap("buildChatRequest",
                new Class[]{List.class, List.class, LlmConfig.class, boolean.class},
                new Object[]{messages, List.of(), config, false});

        assertNull(result.get("tools"));
        assertNull(result.get("tool_choice"));
    }

    // ==================== mapTypeToJsonSchema() via reflection ====================

    @Test
    public void testMapTypeToJsonSchema_string() throws Exception {
        String result = (String) invokePrivate("mapTypeToJsonSchema", new Class[]{String.class}, new Object[]{"STRING"});
        assertEquals("string", result);
    }

    @Test
    public void testMapTypeToJsonSchema_int() throws Exception {
        String result = (String) invokePrivate("mapTypeToJsonSchema", new Class[]{String.class}, new Object[]{"INT"});
        assertEquals("integer", result);
    }

    @Test
    public void testMapTypeToJsonSchema_long() throws Exception {
        String result = (String) invokePrivate("mapTypeToJsonSchema", new Class[]{String.class}, new Object[]{"LONG"});
        assertEquals("integer", result);
    }

    @Test
    public void testMapTypeToJsonSchema_boolean() throws Exception {
        String result = (String) invokePrivate("mapTypeToJsonSchema", new Class[]{String.class}, new Object[]{"BOOLEAN"});
        assertEquals("boolean", result);
    }

    @Test
    public void testMapTypeToJsonSchema_enum() throws Exception {
        String result = (String) invokePrivate("mapTypeToJsonSchema", new Class[]{String.class}, new Object[]{"ENUM"});
        assertEquals("string", result);
    }

    @Test
    public void testMapTypeToJsonSchema_null() throws Exception {
        String result = (String) invokePrivate("mapTypeToJsonSchema", new Class[]{String.class}, new Object[]{null});
        assertEquals("string", result);
    }

    @Test
    public void testMapTypeToJsonSchema_unknown() throws Exception {
        String result = (String) invokePrivate("mapTypeToJsonSchema", new Class[]{String.class}, new Object[]{"UNKNOWN"});
        assertEquals("string", result);
    }

    // ==================== escapeJson() via reflection ====================

    @Test
    public void testEscapeJson_null() throws Exception {
        String result = (String) invokePrivate("escapeJson", new Class[]{String.class}, new Object[]{null});
        assertEquals("", result);
    }

    @Test
    public void testEscapeJson_backslash() throws Exception {
        String result = (String) invokePrivate("escapeJson", new Class[]{String.class}, new Object[]{"path\\to\\file"});
        assertEquals("path\\\\to\\\\file", result);
    }

    @Test
    public void testEscapeJson_quotes() throws Exception {
        String result = (String) invokePrivate("escapeJson", new Class[]{String.class}, new Object[]{"say \"hello\""});
        assertEquals("say \\\"hello\\\"", result);
    }

    @Test
    public void testEscapeJson_newline() throws Exception {
        String result = (String) invokePrivate("escapeJson", new Class[]{String.class}, new Object[]{"line1\nline2"});
        assertEquals("line1\\nline2", result);
    }

    @Test
    public void testEscapeJson_tab() throws Exception {
        String result = (String) invokePrivate("escapeJson", new Class[]{String.class}, new Object[]{"col1\tcol2"});
        assertEquals("col1\\tcol2", result);
    }

    // ==================== buildError() via reflection ====================

    @Test
    public void testBuildError_normal() throws Exception {
        String result = (String) invokePrivate("buildError", new Class[]{String.class}, new Object[]{"test error"});
        assertTrue(result.contains("test error"));
        assertTrue(result.contains("error"));
    }

    // ==================== Integration: chat with specific providers ====================

    @Test
    public void testChat_customProvider() {
        // CUSTOM provider uses apiBase + "/chat/completions"
        String chatResponse = "{\"choices\":[{\"message\":{\"content\":\"Custom response\"},\"finish_reason\":\"stop\"}]}";
        registerOkHandler("/chat/completions", chatResponse);

        LlmConfig config = new LlmConfig();
        config.setEnabled(true);
        config.setApiKey("test-key");
        config.setProvider("CUSTOM");
        config.setApiBase("http://127.0.0.1:" + port);
        String result = service.chat("hello", null, config);
        assertTrue(result.contains("Custom response"));
    }

    @Test
    public void testChat_toolCallWithArgumentsParsing() {
        // Test that tool call arguments are parsed from JSON string to Map
        String chatResponse = "{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":[{\"id\":\"call_1\",\"function\":{\"name\":\"rmq_topic_create\",\"arguments\":\"{\\\"topic\\\":\\\"test-topic\\\",\\\"cluster\\\":\\\"DefaultCluster\\\"}\"}}]},\"finish_reason\":\"tool_calls\"}]}";
        registerOkHandler("/chat/completions", chatResponse);

        String result = service.chat("create topic", createTestTools(), createConfig("CUSTOM"));
        assertTrue(result.contains("rmq.topic.create"));
        assertTrue(result.contains("test-topic"));
    }

    @Test
    public void testChat_nullContentInMessage() {
        String chatResponse = "{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":[]},\"finish_reason\":\"stop\"}]}";
        registerOkHandler("/chat/completions", chatResponse);

        String result = service.chat("hello", null, createConfig("CUSTOM"));
        assertNotNull(result);
        // Should not crash even with null content
    }

    @Test
    public void testChatStream_doneSignal() {
        // Test that [DONE] signal properly terminates the stream
        registerSseHandler("/chat/completions",
                "{\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}");

        List<String> chunks = new ArrayList<>();
        Consumer<String> callback = chunks::add;
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "user");
        msg.put("content", "hi");
        messages.add(msg);

        service.chatStream(messages, null, createConfig("CUSTOM"), callback);
        // Should receive only 1 chunk (not [DONE])
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("Hi"));
    }

    @Test
    public void testChatStream_emptyStream() {
        // Stream with only [DONE]
        registerSseHandler("/chat/completions", new String[]{});

        List<String> chunks = new ArrayList<>();
        Consumer<String> callback = chunks::add;
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "user");
        msg.put("content", "hi");
        messages.add(msg);

        service.chatStream(messages, null, createConfig("CUSTOM"), callback);
        // Should receive no chunks
        assertEquals(0, chunks.size());
    }

    @Test
    public void testListModels_modelNameFallback() {
        // When model has no "name" field, it should fall back to "id"
        String modelsResponse = "{\"data\":[{\"id\":\"model-1\"}]}";
        registerOkHandler("/models", modelsResponse);

        LlmConfig config = createConfig("OPENAI");
        config.setApiBase("http://127.0.0.1:" + port);
        List<Map<String, Object>> result = service.listModels(config);
        assertEquals(1, result.size());
        assertEquals("model-1", result.get(0).get("id"));
        assertEquals("model-1", result.get(0).get("name")); // Falls back to id
    }
}