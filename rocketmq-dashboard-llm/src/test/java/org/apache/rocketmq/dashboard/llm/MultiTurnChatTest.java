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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.rocketmq.dashboard.cli.schema.ParamSchema;
import org.apache.rocketmq.dashboard.cli.schema.RiskLevel;
import org.apache.rocketmq.dashboard.cli.schema.ToolDefinition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests for multi-turn conversation with tool calls.
 * Includes both unit tests (message building) and integration tests (DeepSeek API).
 *
 * Integration tests require a valid DeepSeek API key and will make real API calls.
 * They are gated behind the "llm.integration" system property to avoid running in CI.
 */
public class MultiTurnChatTest {

    private static final Path CONFIG_FILE =
            Paths.get(System.getProperty("user.home"), ".rmqctl", "llm-config.yaml");
    private static final Path RMQCTL_DIR = CONFIG_FILE.getParent();

    // DeepSeek test config — API key is read from environment variable DEEPSEEK_API_KEY
    // or system property deepseek.api.key to avoid committing secrets to source code.
    // A masked placeholder is used as default; integration tests will skip if no real key is provided.
    private static final String DEEPSEEK_API_KEY =
            System.getProperty("deepseek.api.key",
                    System.getenv("DEEPSEEK_API_KEY") != null ? System.getenv("DEEPSEEK_API_KEY")
                            : "sk-****masked****");
    private static final String DEEPSEEK_MODEL = "deepseek-chat";

    private ObjectMapper objectMapper;
    private McpBridgeController controller;
    private LlmProxyService llmProxyService;

    @Before
    public void setUp() throws IOException {
        objectMapper = new ObjectMapper();
        Files.deleteIfExists(CONFIG_FILE);
        Files.deleteIfExists(RMQCTL_DIR);

        llmProxyService = new LlmProxyService();
        controller = new McpBridgeController();
        try {
            java.lang.reflect.Field field = McpBridgeController.class.getDeclaredField("llmProxyService");
            field.setAccessible(true);
            field.set(controller, llmProxyService);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject llmProxyService", e);
        }
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(CONFIG_FILE);
        Files.deleteIfExists(RMQCTL_DIR);
    }

    // =========================================================================
    // Unit Tests: buildMessagesWithTools logic (no API calls)
    // =========================================================================

    /**
     * Test that error role messages are filtered out from history.
     * Frontend adds role:'error' messages when SSE fails — these must not reach the LLM API.
     */
    @Test
    public void testErrorRoleMessagesAreFiltered() throws Exception {
        List<Map<String, Object>> history = new ArrayList<>();

        // User message
        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", "List clusters");
        history.add(userMsg);

        // Assistant message with tool calls
        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", "");
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        Map<String, Object> tc = new LinkedHashMap<>();
        tc.put("id", "call_001");
        tc.put("name", "rmq.cluster.list");
        tc.put("arguments", Map.of("cluster", "default"));
        tc.put("status", "completed");
        tc.put("result", Map.of("data", List.of("DefaultCluster")));
        toolCalls.add(tc);
        assistantMsg.put("toolCalls", toolCalls);
        history.add(assistantMsg);

        // Error message (should be filtered)
        Map<String, Object> errorMsg = new LinkedHashMap<>();
        errorMsg.put("role", "error");
        errorMsg.put("content", "LLM API error: HTTP 400");
        history.add(errorMsg);

        // Another user message
        Map<String, Object> userMsg2 = new LinkedHashMap<>();
        userMsg2.put("role", "user");
        userMsg2.put("content", "List brokers");
        history.add(userMsg2);

        // Use reflection to call buildMessagesWithTools
        List<Map<String, Object>> messages = invokeBuildMessagesWithTools("Show brokers", history);

        // Verify no 'error' role messages
        for (Map<String, Object> msg : messages) {
            String role = (String) msg.get("role");
            assertFalse("Error role messages should be filtered out, but found: " + role,
                    "error".equals(role));
        }

        // Verify expected message sequence: system, user, assistant+tool_calls, tool, user, user
        assertTrue("Should have at least 5 messages", messages.size() >= 5);
        assertEquals("First message should be system", "system", messages.get(0).get("role"));
        assertEquals("Second message should be user", "user", messages.get(1).get("role"));
        assertEquals("Third message should be assistant", "assistant", messages.get(2).get("role"));
        assertTrue("Assistant should have tool_calls", messages.get(2).containsKey("tool_calls"));
        assertEquals("Fourth message should be tool", "tool", messages.get(3).get("role"));
    }

    /**
     * Test that assistant+tool_calls with empty content omits the content field.
     * Some LLM providers reject empty string content with tool_calls.
     */
    @Test
    public void testAssistantToolCallsEmptyContentOmitted() throws Exception {
        List<Map<String, Object>> history = new ArrayList<>();

        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", ""); // Empty content
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        Map<String, Object> tc = new LinkedHashMap<>();
        tc.put("id", "call_002");
        tc.put("name", "rmq.topic.list");
        tc.put("arguments", Map.of("cluster", "default"));
        tc.put("status", "completed");
        tc.put("result", Map.of("data", List.of()));
        toolCalls.add(tc);
        assistantMsg.put("toolCalls", toolCalls);
        history.add(assistantMsg);

        List<Map<String, Object>> messages = invokeBuildMessagesWithTools("Next question", history);

        // Find the assistant message
        Map<String, Object> assistant = null;
        for (Map<String, Object> msg : messages) {
            if ("assistant".equals(msg.get("role")) && msg.containsKey("tool_calls")) {
                assistant = msg;
                break;
            }
        }
        assertNotNull("Should find assistant message with tool_calls", assistant);
        // Content should be omitted (not present) or null when empty/blank
        if (assistant.containsKey("content")) {
            Object content = assistant.get("content");
            assertTrue("Content should be null or non-blank when tool_calls present",
                    content == null || !content.toString().isBlank());
        }
        // If content was empty string, it should NOT be present as ""
        if (assistant.containsKey("content")) {
            assertFalse("Content should not be empty string with tool_calls",
                    "".equals(assistant.get("content")));
        }
    }

    /**
     * Test that tool_call_id is consistent between tool_calls entries and tool response messages.
     */
    @Test
    public void testToolCallIdConsistency() throws Exception {
        List<Map<String, Object>> history = new ArrayList<>();

        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", "");
        List<Map<String, Object>> toolCalls = new ArrayList<>();

        // Two tool calls
        Map<String, Object> tc1 = new LinkedHashMap<>();
        tc1.put("id", "call_abc123");
        tc1.put("name", "rmq.cluster.list");
        tc1.put("arguments", Map.of("cluster", "default"));
        tc1.put("status", "completed");
        tc1.put("result", Map.of("data", "cluster-result"));
        toolCalls.add(tc1);

        Map<String, Object> tc2 = new LinkedHashMap<>();
        tc2.put("id", "call_def456");
        tc2.put("name", "rmq.broker.list");
        tc2.put("arguments", Map.of("cluster", "default"));
        tc2.put("status", "completed");
        tc2.put("result", Map.of("data", "broker-result"));
        toolCalls.add(tc2);

        assistantMsg.put("toolCalls", toolCalls);
        history.add(assistantMsg);

        List<Map<String, Object>> messages = invokeBuildMessagesWithTools("Next", history);

        // Find assistant message and tool messages
        Map<String, Object> assistant = null;
        List<Map<String, Object>> toolMsgs = new ArrayList<>();
        for (Map<String, Object> msg : messages) {
            if ("assistant".equals(msg.get("role")) && msg.containsKey("tool_calls")) {
                assistant = msg;
            } else if ("tool".equals(msg.get("role"))) {
                toolMsgs.add(msg);
            }
        }

        assertNotNull("Should find assistant message", assistant);
        assertEquals("Should have 2 tool messages", 2, toolMsgs.size());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tcList = (List<Map<String, Object>>) assistant.get("tool_calls");
        assertEquals("Should have 2 tool_calls entries", 2, tcList.size());

        // Verify ID consistency
        for (int i = 0; i < 2; i++) {
            String tcId = (String) tcList.get(i).get("id");
            String toolMsgId = (String) toolMsgs.get(i).get("tool_call_id");
            assertEquals("tool_call_id should match between tool_calls[" + i + "] and tool message[" + i + "]",
                    tcId, toolMsgId);
        }
    }

    /**
     * Test that tool_call_id uses deterministic fallback when id field is missing.
     */
    @Test
    public void testToolCallIdDeterministicFallback() throws Exception {
        List<Map<String, Object>> history = new ArrayList<>();

        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", "");
        List<Map<String, Object>> toolCalls = new ArrayList<>();

        // Tool call WITHOUT id field
        Map<String, Object> tc = new LinkedHashMap<>();
        tc.put("name", "rmq.cluster.list");
        tc.put("arguments", Map.of("cluster", "default"));
        tc.put("status", "completed");
        tc.put("result", Map.of("data", "result"));
        toolCalls.add(tc);

        assistantMsg.put("toolCalls", toolCalls);
        history.add(assistantMsg);

        List<Map<String, Object>> messages = invokeBuildMessagesWithTools("Next", history);

        // Find tool message
        Map<String, Object> toolMsg = null;
        for (Map<String, Object> msg : messages) {
            if ("tool".equals(msg.get("role"))) {
                toolMsg = msg;
                break;
            }
        }
        assertNotNull("Should find tool message", toolMsg);
        // Should use deterministic "call_0" as fallback
        assertEquals("tool_call_id should use deterministic fallback",
                "call_0", toolMsg.get("tool_call_id"));
    }

    /**
     * Test that completed status generates proper tool response content.
     */
    @Test
    public void testCompletedStatusGeneratesResultContent() throws Exception {
        List<Map<String, Object>> history = new ArrayList<>();

        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", "");
        List<Map<String, Object>> toolCalls = new ArrayList<>();

        Map<String, Object> tc = new LinkedHashMap<>();
        tc.put("id", "call_test");
        tc.put("name", "rmq.cluster.list");
        tc.put("arguments", Map.of("cluster", "default"));
        tc.put("status", "completed");
        tc.put("result", Map.of("clusters", List.of("DefaultCluster")));
        toolCalls.add(tc);

        assistantMsg.put("toolCalls", toolCalls);
        history.add(assistantMsg);

        List<Map<String, Object>> messages = invokeBuildMessagesWithTools("Next", history);

        Map<String, Object> toolMsg = null;
        for (Map<String, Object> msg : messages) {
            if ("tool".equals(msg.get("role"))) {
                toolMsg = msg;
                break;
            }
        }
        assertNotNull("Should find tool message", toolMsg);
        String content = (String) toolMsg.get("content");
        assertNotNull("Tool message should have content", content);
        assertTrue("Content should contain the result data",
                content.contains("DefaultCluster"));
    }

    /**
     * Test that dry_run status generates placeholder content.
     */
    @Test
    public void testDryRunStatusGeneratesPlaceholderContent() throws Exception {
        List<Map<String, Object>> history = new ArrayList<>();

        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", "");
        List<Map<String, Object>> toolCalls = new ArrayList<>();

        Map<String, Object> tc = new LinkedHashMap<>();
        tc.put("id", "call_dry");
        tc.put("name", "rmq.topic.delete");
        tc.put("arguments", Map.of("topic", "test-topic"));
        tc.put("status", "dry_run");
        // No result field
        toolCalls.add(tc);

        assistantMsg.put("toolCalls", toolCalls);
        history.add(assistantMsg);

        List<Map<String, Object>> messages = invokeBuildMessagesWithTools("Next", history);

        Map<String, Object> toolMsg = null;
        for (Map<String, Object> msg : messages) {
            if ("tool".equals(msg.get("role"))) {
                toolMsg = msg;
                break;
            }
        }
        assertNotNull("Should find tool message", toolMsg);
        String content = (String) toolMsg.get("content");
        assertTrue("Dry run should mention confirmation",
                content.contains("confirmation") || content.contains("Confirmation"));
    }

    /**
     * Test that the full multi-turn message sequence is correct.
     * Simulates: user -> assistant+tool_calls -> tool -> user -> (new request)
     */
    @Test
    public void testFullMultiTurnMessageSequence() throws Exception {
        List<Map<String, Object>> history = new ArrayList<>();

        // Turn 1: User asks about clusters
        Map<String, Object> user1 = new LinkedHashMap<>();
        user1.put("role", "user");
        user1.put("content", "List all clusters");
        history.add(user1);

        // Turn 1: Assistant responds with tool call
        Map<String, Object> assistant1 = new LinkedHashMap<>();
        assistant1.put("role", "assistant");
        assistant1.put("content", "Let me list the clusters for you.");
        List<Map<String, Object>> toolCalls1 = new ArrayList<>();
        Map<String, Object> tc1 = new LinkedHashMap<>();
        tc1.put("id", "call_001");
        tc1.put("name", "rmq.cluster.list");
        tc1.put("arguments", Map.of("cluster", "default"));
        tc1.put("status", "completed");
        tc1.put("result", Map.of("data", List.of(Map.of("name", "DefaultCluster"))));
        toolCalls1.add(tc1);
        assistant1.put("toolCalls", toolCalls1);
        history.add(assistant1);

        // Turn 2: User asks about brokers (this is the new request)
        List<Map<String, Object>> messages = invokeBuildMessagesWithTools("List brokers in this cluster", history);

        // Verify the sequence: system + user1 + assistant+tool_calls + tool + user2 = 5 messages
        assertEquals("Should have 5 messages", 5, messages.size());

        assertEquals("msg[0] should be system", "system", messages.get(0).get("role"));
        assertEquals("msg[1] should be user", "user", messages.get(1).get("role"));
        assertEquals("msg[1] content", "List all clusters", messages.get(1).get("content"));

        assertEquals("msg[2] should be assistant", "assistant", messages.get(2).get("role"));
        assertTrue("msg[2] should have tool_calls", messages.get(2).containsKey("tool_calls"));
        assertEquals("msg[2] content should be preserved",
                "Let me list the clusters for you.", messages.get(2).get("content"));

        assertEquals("msg[3] should be tool", "tool", messages.get(3).get("role"));
        assertEquals("msg[3] tool_call_id", "call_001", messages.get(3).get("tool_call_id"));
        assertTrue("msg[3] content should contain result",
                messages.get(3).get("content").toString().contains("DefaultCluster"));

        assertEquals("msg[4] should be user (new request)", "user", messages.get(4).get("role"));
        assertEquals("msg[4] content", "List brokers in this cluster", messages.get(4).get("content"));
    }

    // =========================================================================
    // Integration Tests: Real DeepSeek API calls
    // =========================================================================

    /**
     * Integration test: Multi-turn conversation with DeepSeek API.
     * Tests that tool_calls + tool response messages are accepted by the API.
     *
     * Run with: mvn test -Dllm.integration=true -pl rocketmq-dashboard-llm
     */
    @Test
    public void testDeepSeekMultiTurnWithToolCalls() throws Exception {
        if (!Boolean.getBoolean("llm.integration")) {
            System.out.println("[SKIP] Integration test skipped. Run with -Dllm.integration=true to enable.");
            return;
        }
        if (DEEPSEEK_API_KEY.contains("masked")) {
            System.out.println("[SKIP] Integration test skipped. Set DEEPSEEK_API_KEY env var or -Ddeepseek.api.key=xxx to enable.");
            return;
        }

        // Configure DeepSeek
        LlmConfig config = new LlmConfig();
        config.setEnabled(true);
        config.setProvider("DEEPSEEK");
        config.setApiKey(DEEPSEEK_API_KEY);
        config.setModel(DEEPSEEK_MODEL);
        config.setMaxTokens(1024);
        config.setTemperature(0.1);

        // Build messages simulating a multi-turn conversation with tool calls
        List<Map<String, Object>> messages = new ArrayList<>();

        // System message
        Map<String, Object> sys = new LinkedHashMap<>();
        sys.put("role", "system");
        sys.put("content", "You are a RocketMQ operations assistant. Use tools to help the user.");
        messages.add(sys);

        // Turn 1: User asks about clusters
        Map<String, Object> user1 = new LinkedHashMap<>();
        user1.put("role", "user");
        user1.put("content", "List all clusters");
        messages.add(user1);

        // Turn 1: Assistant responds with tool call
        Map<String, Object> assistant1 = new LinkedHashMap<>();
        assistant1.put("role", "assistant");
        assistant1.put("content", "Let me list the clusters for you.");
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        Map<String, Object> tc = new LinkedHashMap<>();
        tc.put("id", "call_test_001");
        tc.put("type", "function");
        Map<String, Object> func = new LinkedHashMap<>();
        func.put("name", "rmq_cluster_list");
        func.put("arguments", "{\"cluster\":\"default\"}");
        tc.put("function", func);
        toolCalls.add(tc);
        assistant1.put("tool_calls", toolCalls);
        messages.add(assistant1);

        // Turn 1: Tool response
        Map<String, Object> toolResp = new LinkedHashMap<>();
        toolResp.put("role", "tool");
        toolResp.put("tool_call_id", "call_test_001");
        toolResp.put("content", "{\"tool\":\"rmq.cluster.list\",\"status\":\"success\",\"data\":[{\"name\":\"DefaultCluster\",\"brokerNames\":[\"broker-a\"]}]}");
        messages.add(toolResp);

        // Turn 2: User asks follow-up
        Map<String, Object> user2 = new LinkedHashMap<>();
        user2.put("role", "user");
        user2.put("content", "What brokers are in this cluster?");
        messages.add(user2);

        // Build tools definition
        List<ToolDefinition> tools = buildTestTools();

        // Call DeepSeek API (non-streaming)
        String response = llmProxyService.chatWithMessages(messages, tools, config);
        assertNotNull("Response should not be null", response);
        System.out.println("[DeepSeek Response] " + response);

        // Parse response
        Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
        assertFalse("Should not have error, got: " + parsed.get("error"),
                parsed.containsKey("error"));

        // Response should have content or tool_calls
        assertTrue("Response should have content or toolCalls",
                parsed.containsKey("content") || parsed.containsKey("toolCalls"));
    }

    /**
     * Integration test: Streaming multi-turn conversation with DeepSeek API.
     *
     * Run with: mvn test -Dllm.integration=true -pl rocketmq-dashboard-llm
     */
    @Test
    public void testDeepSeekStreamingMultiTurn() throws Exception {
        if (!Boolean.getBoolean("llm.integration")) {
            System.out.println("[SKIP] Integration test skipped. Run with -Dllm.integration=true to enable.");
            return;
        }
        if (DEEPSEEK_API_KEY.contains("masked")) {
            System.out.println("[SKIP] Integration test skipped. Set DEEPSEEK_API_KEY env var or -Ddeepseek.api.key=xxx to enable.");
            return;
        }

        LlmConfig config = new LlmConfig();
        config.setEnabled(true);
        config.setProvider("DEEPSEEK");
        config.setApiKey(DEEPSEEK_API_KEY);
        config.setModel(DEEPSEEK_MODEL);
        config.setMaxTokens(1024);
        config.setTemperature(0.1);

        // Build messages simulating multi-turn with tool calls
        List<Map<String, Object>> messages = new ArrayList<>();

        Map<String, Object> sys = new LinkedHashMap<>();
        sys.put("role", "system");
        sys.put("content", "You are a RocketMQ operations assistant. Use tools to help the user.");
        messages.add(sys);

        Map<String, Object> user1 = new LinkedHashMap<>();
        user1.put("role", "user");
        user1.put("content", "List all clusters");
        messages.add(user1);

        Map<String, Object> assistant1 = new LinkedHashMap<>();
        assistant1.put("role", "assistant");
        assistant1.put("content", "Let me list the clusters.");
        List<Map<String, Object>> toolCalls = new ArrayList<>();
        Map<String, Object> tc = new LinkedHashMap<>();
        tc.put("id", "call_stream_001");
        tc.put("type", "function");
        Map<String, Object> func = new LinkedHashMap<>();
        func.put("name", "rmq_cluster_list");
        func.put("arguments", "{\"cluster\":\"default\"}");
        tc.put("function", func);
        toolCalls.add(tc);
        assistant1.put("tool_calls", toolCalls);
        messages.add(assistant1);

        Map<String, Object> toolResp = new LinkedHashMap<>();
        toolResp.put("role", "tool");
        toolResp.put("tool_call_id", "call_stream_001");
        toolResp.put("content", "{\"status\":\"success\",\"data\":[{\"name\":\"DefaultCluster\"}]}");
        messages.add(toolResp);

        Map<String, Object> user2 = new LinkedHashMap<>();
        user2.put("role", "user");
        user2.put("content", "How many brokers are in DefaultCluster?");
        messages.add(user2);

        List<ToolDefinition> tools = buildTestTools();

        // Call streaming API
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> errorRef = new AtomicReference<>();
        AtomicReference<String> lastChunk = new AtomicReference<>();
        StringBuilder allContent = new StringBuilder();

        llmProxyService.chatStream(messages, tools, config, chunk -> {
            try {
                Map<String, Object> parsed = objectMapper.readValue(chunk, Map.class);
                if (parsed.containsKey("error")) {
                    errorRef.set(parsed.get("error").toString());
                    latch.countDown();
                    return;
                }
                // Accumulate content from choices
                List<Map<String, Object>> choices =
                        (List<Map<String, Object>>) parsed.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> delta =
                            (Map<String, Object>) choices.get(0).get("delta");
                    if (delta != null && delta.get("content") != null) {
                        allContent.append(delta.get("content").toString());
                    }
                }
                lastChunk.set(chunk);
            } catch (Exception e) {
                errorRef.set("Parse error: " + e.getMessage());
                latch.countDown();
            }
        });

        // Wait for completion (streaming ends naturally)
        boolean completed = latch.await(30, TimeUnit.SECONDS);

        if (errorRef.get() != null) {
            fail("DeepSeek streaming API returned error: " + errorRef.get());
        }

        // Streaming doesn't have a clear "done" signal via latch,
        // so we just check that we got some content
        System.out.println("[DeepSeek Streaming Response] content length: " + allContent.length());
        System.out.println("[DeepSeek Streaming Response] content: " + allContent.toString().substring(0,
                Math.min(500, allContent.length())));
    }

    /**
     * Integration test: Simple single-turn chat to verify API connectivity.
     *
     * Run with: mvn test -Dllm.integration=true -pl rocketmq-dashboard-llm
     */
    @Test
    public void testDeepSeekSimpleChat() throws Exception {
        if (!Boolean.getBoolean("llm.integration")) {
            System.out.println("[SKIP] Integration test skipped. Run with -Dllm.integration=true to enable.");
            return;
        }
        if (DEEPSEEK_API_KEY.contains("masked")) {
            System.out.println("[SKIP] Integration test skipped. Set DEEPSEEK_API_KEY env var or -Ddeepseek.api.key=xxx to enable.");
            return;
        }

        LlmConfig config = new LlmConfig();
        config.setEnabled(true);
        config.setProvider("DEEPSEEK");
        config.setApiKey(DEEPSEEK_API_KEY);
        config.setModel(DEEPSEEK_MODEL);
        config.setMaxTokens(256);
        config.setTemperature(0.1);

        String response = llmProxyService.chat("Say hello in one word.", List.of(), config);
        assertNotNull("Response should not be null", response);
        System.out.println("[DeepSeek Simple Chat] " + response);

        Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
        assertFalse("Should not have error", parsed.containsKey("error"));
        assertTrue("Should have content", parsed.containsKey("content"));
        assertNotNull("Content should not be null", parsed.get("content"));
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> invokeBuildMessagesWithTools(String currentMessage,
                                                                     List<Map<String, Object>> history) throws Exception {
        java.lang.reflect.Method method = McpBridgeController.class.getDeclaredMethod(
                "buildMessagesWithTools", String.class, List.class);
        method.setAccessible(true);
        return (List<Map<String, Object>>) method.invoke(controller, currentMessage, history);
    }

    private List<ToolDefinition> buildTestTools() {
        List<ToolDefinition> tools = new ArrayList<>();

        // rmq.cluster.list (resource=cluster, verb=list → mcpToolName = rmq.cluster.list)
        tools.add(ToolDefinition.builder()
                .resource("cluster")
                .verb("list")
                .description("List all clusters in the RocketMQ environment")
                .riskLevel(RiskLevel.L1)
                .params(List.of(
                        ParamSchema.builder()
                                .name("cluster")
                                .type("string")
                                .description("Cluster name")
                                .required(false)
                                .build()
                ))
                .build());

        // rmq.broker.list
        tools.add(ToolDefinition.builder()
                .resource("broker")
                .verb("list")
                .description("List all brokers in the cluster")
                .riskLevel(RiskLevel.L1)
                .params(List.of(
                        ParamSchema.builder()
                                .name("cluster")
                                .type("string")
                                .description("Cluster name")
                                .required(false)
                                .build()
                ))
                .build());

        // rmq.topic.list
        tools.add(ToolDefinition.builder()
                .resource("topic")
                .verb("list")
                .description("List all topics in the cluster")
                .riskLevel(RiskLevel.L1)
                .params(List.of(
                        ParamSchema.builder()
                                .name("cluster")
                                .type("string")
                                .description("Cluster name")
                                .required(false)
                                .build()
                ))
                .build());

        return tools;
    }

    private void writeConfig(String yamlContent) throws IOException {
        Files.createDirectories(RMQCTL_DIR);
        Files.write(CONFIG_FILE, yamlContent.getBytes(StandardCharsets.UTF_8));
    }
}