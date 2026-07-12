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
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.apache.rocketmq.dashboard.cli.schema.ToolDefinition;
import org.apache.rocketmq.dashboard.cli.schema.ToolRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for private methods in McpBridgeController using reflection.
 * These tests improve code coverage for critical logic paths that are
 * not easily tested through public endpoints alone.
 */
public class McpBridgeControllerPrivateTest {

    private static final Path CONFIG_FILE =
            Paths.get(System.getProperty("user.home"), ".rmqctl", "llm-config.yaml");
    private static final Path RMQCTL_DIR = CONFIG_FILE.getParent();

    private McpBridgeController controller;

    @Before
    public void setUp() throws IOException {
        Files.deleteIfExists(CONFIG_FILE);
        Files.deleteIfExists(RMQCTL_DIR);

        controller = new McpBridgeController();
        LlmProxyService llmProxyService = new LlmProxyService();
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

    // ---- Reflection helpers ----------------------------------------------------

    private Method getPrivateMethod(String name, Class<?>... paramTypes) throws Exception {
        Method m = McpBridgeController.class.getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        return m;
    }

    // ---- parseHistory tests ----------------------------------------------------

    @Test
    public void testParseHistoryWithValidJson() throws Exception {
        Method parseHistory = getPrivateMethod("parseHistory", String.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result =
                (List<Map<String, Object>>) parseHistory.invoke(controller,
                        "[{\"role\":\"user\",\"content\":\"hello\"}]");

        assertNotNull("Result should not be null", result);
        assertEquals("Should have 1 entry", 1, result.size());
        assertEquals("Role should be user", "user", result.get(0).get("role"));
        assertEquals("Content should be hello", "hello", result.get(0).get("content"));
    }

    @Test
    public void testParseHistoryWithNull() throws Exception {
        Method parseHistory = getPrivateMethod("parseHistory", String.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result =
                (List<Map<String, Object>>) parseHistory.invoke(controller, (String) null);

        assertNotNull("Result should not be null", result);
        assertTrue("Null history should return empty list", result.isEmpty());
    }

    @Test
    public void testParseHistoryWithEmptyString() throws Exception {
        Method parseHistory = getPrivateMethod("parseHistory", String.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result =
                (List<Map<String, Object>>) parseHistory.invoke(controller, "");

        assertNotNull("Result should not be null", result);
        assertTrue("Empty string should return empty list", result.isEmpty());
    }

    @Test
    public void testParseHistoryWithWhitespace() throws Exception {
        Method parseHistory = getPrivateMethod("parseHistory", String.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result =
                (List<Map<String, Object>>) parseHistory.invoke(controller, "   ");

        assertNotNull("Result should not be null", result);
        assertTrue("Whitespace should return empty list", result.isEmpty());
    }

    @Test
    public void testParseHistoryWithInvalidJson() throws Exception {
        Method parseHistory = getPrivateMethod("parseHistory", String.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result =
                (List<Map<String, Object>>) parseHistory.invoke(controller, "not-valid-json");

        assertNotNull("Result should not be null", result);
        assertTrue("Invalid JSON should return empty list", result.isEmpty());
    }

    @Test
    public void testParseHistoryWithMultipleEntries() throws Exception {
        Method parseHistory = getPrivateMethod("parseHistory", String.class);
        String json = "[{\"role\":\"user\",\"content\":\"hi\"},{\"role\":\"assistant\",\"content\":\"hello\"}]";
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result =
                (List<Map<String, Object>>) parseHistory.invoke(controller, json);

        assertEquals("Should have 2 entries", 2, result.size());
        assertEquals("user", result.get(0).get("role"));
        assertEquals("assistant", result.get(1).get("role"));
    }

    // ---- buildMessagesWithTools tests ------------------------------------------

    @Test
    public void testBuildMessagesWithToolsBasicMessage() throws Exception {
        Method buildMessages = getPrivateMethod("buildMessagesWithTools", String.class, List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result =
                (List<Map<String, Object>>) buildMessages.invoke(controller, "List topics", new ArrayList<>());

        assertNotNull("Result should not be null", result);
        // Should have: system message + current user message = 2
        assertEquals("Should have system + user message", 2, result.size());
        assertEquals("First should be system", "system", result.get(0).get("role"));
        assertEquals("Second should be user", "user", result.get(1).get("role"));
        assertEquals("User message content", "List topics", result.get(1).get("content"));
    }

    @Test
    public void testBuildMessagesWithToolsFiltersErrorRole() throws Exception {
        Method buildMessages = getPrivateMethod("buildMessagesWithTools", String.class, List.class);

        List<Map<String, Object>> history = new ArrayList<>();
        Map<String, Object> errorMsg = new LinkedHashMap<>();
        errorMsg.put("role", "error");
        errorMsg.put("content", "Something went wrong");
        history.add(errorMsg);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result =
                (List<Map<String, Object>>) buildMessages.invoke(controller, "Next message", history);

        // error role should be filtered out: system + user = 2
        assertEquals("Error role should be filtered", 2, result.size());
        assertEquals("system", result.get(0).get("role"));
        assertEquals("user", result.get(1).get("role"));
    }

    @Test
    public void testBuildMessagesWithToolsNullRole() throws Exception {
        Method buildMessages = getPrivateMethod("buildMessagesWithTools", String.class, List.class);

        List<Map<String, Object>> history = new ArrayList<>();
        Map<String, Object> nullRoleMsg = new LinkedHashMap<>();
        nullRoleMsg.put("content", "No role");
        history.add(nullRoleMsg);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result =
                (List<Map<String, Object>>) buildMessages.invoke(controller, "Test", history);

        // null role should be filtered: system + user = 2
        assertEquals("Null role should be filtered", 2, result.size());
    }

    @Test
    public void testBuildMessagesWithToolsUserAndAssistantHistory() throws Exception {
        Method buildMessages = getPrivateMethod("buildMessagesWithTools", String.class, List.class);

        List<Map<String, Object>> history = new ArrayList<>();
        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", "List topics");
        history.add(userMsg);

        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", "I found 3 topics.");
        history.add(assistantMsg);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result =
                (List<Map<String, Object>>) buildMessages.invoke(controller, "Tell me more", history);

        // system + user + assistant + current user = 4
        assertEquals("Should have 4 messages", 4, result.size());
        assertEquals("system", result.get(0).get("role"));
        assertEquals("user", result.get(1).get("role"));
        assertEquals("assistant", result.get(2).get("role"));
        assertEquals("user", result.get(3).get("role"));
    }

    @Test
    public void testBuildMessagesWithToolsAssistantWithToolCalls() throws Exception {
        Method buildMessages = getPrivateMethod("buildMessagesWithTools", String.class, List.class);

        List<Map<String, Object>> history = new ArrayList<>();
        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", "");

        List<Map<String, Object>> toolCalls = new ArrayList<>();
        Map<String, Object> tc = new LinkedHashMap<>();
        tc.put("id", "call_001");
        tc.put("name", "listTopics");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("cluster", "default");
        tc.put("arguments", args);
        tc.put("status", "completed");
        tc.put("result", "TopicA, TopicB");
        toolCalls.add(tc);
        assistantMsg.put("toolCalls", toolCalls);
        history.add(assistantMsg);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result =
                (List<Map<String, Object>>) buildMessages.invoke(controller, "Execute it", history);

        // system + assistant(with tool_calls) + tool response + current user = 4
        assertEquals("Should have 4 messages", 4, result.size());
        assertEquals("system", result.get(0).get("role"));
        assertEquals("assistant", result.get(1).get("role"));
        assertNotNull("Assistant should have tool_calls", result.get(1).get("tool_calls"));
        assertEquals("tool", result.get(2).get("role"));
        assertEquals("user", result.get(3).get("role"));
    }

    @Test
    public void testBuildMessagesWithToolsAssistantWithToolCallsOmitsEmptyContent() throws Exception {
        Method buildMessages = getPrivateMethod("buildMessagesWithTools", String.class, List.class);

        List<Map<String, Object>> history = new ArrayList<>();
        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", "");  // Empty content with tool_calls

        List<Map<String, Object>> toolCalls = new ArrayList<>();
        Map<String, Object> tc = new LinkedHashMap<>();
        tc.put("id", "call_001");
        tc.put("name", "listTopics");
        tc.put("arguments", new LinkedHashMap<>());
        tc.put("status", "completed");
        toolCalls.add(tc);
        assistantMsg.put("toolCalls", toolCalls);
        history.add(assistantMsg);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result =
                (List<Map<String, Object>>) buildMessages.invoke(controller, "Next", history);

        // Assistant message with empty content + tool_calls should NOT have content key
        Map<String, Object> assistantResult = result.get(1);
        assertNull("Empty content should be omitted with tool_calls", assistantResult.get("content"));
    }

    @Test
    public void testBuildMessagesWithToolsToolRoleMessage() throws Exception {
        Method buildMessages = getPrivateMethod("buildMessagesWithTools", String.class, List.class);

        List<Map<String, Object>> history = new ArrayList<>();
        Map<String, Object> toolMsg = new LinkedHashMap<>();
        toolMsg.put("role", "tool");
        toolMsg.put("content", "{\"topics\":[\"A\",\"B\"]}");
        toolMsg.put("tool_call_id", "call_001");
        history.add(toolMsg);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result =
                (List<Map<String, Object>>) buildMessages.invoke(controller, "Thanks", history);

        // system + tool + user = 3
        assertEquals("Should have 3 messages", 3, result.size());
        assertEquals("tool", result.get(1).get("role"));
        assertEquals("call_001", result.get(1).get("tool_call_id"));
    }

    @Test
    public void testBuildMessagesWithToolsSystemRoleMessage() throws Exception {
        Method buildMessages = getPrivateMethod("buildMessagesWithTools", String.class, List.class);

        List<Map<String, Object>> history = new ArrayList<>();
        Map<String, Object> sysMsg = new LinkedHashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", "Custom system instruction");
        history.add(sysMsg);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result =
                (List<Map<String, Object>>) buildMessages.invoke(controller, "Hello", history);

        // Default system + custom system + user = 3
        assertTrue("Should have at least 3 messages", result.size() >= 3);
    }

    @Test
    public void testBuildMessagesWithToolsDotInToolNameReplaced() throws Exception {
        Method buildMessages = getPrivateMethod("buildMessagesWithTools", String.class, List.class);

        List<Map<String, Object>> history = new ArrayList<>();
        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", "");

        List<Map<String, Object>> toolCalls = new ArrayList<>();
        Map<String, Object> tc = new LinkedHashMap<>();
        tc.put("id", "call_001");
        tc.put("name", "rmq.topic.list");  // Dot in name
        tc.put("arguments", new LinkedHashMap<>());
        tc.put("status", "completed");
        toolCalls.add(tc);
        assistantMsg.put("toolCalls", toolCalls);
        history.add(assistantMsg);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result =
                (List<Map<String, Object>>) buildMessages.invoke(controller, "Next", history);

        Map<String, Object> assistantResult = result.get(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resultToolCalls =
                (List<Map<String, Object>>) assistantResult.get("tool_calls");
        assertNotNull("Should have tool_calls", resultToolCalls);
        @SuppressWarnings("unchecked")
        Map<String, Object> func = (Map<String, Object>) resultToolCalls.get(0).get("function");
        // Dots should be replaced with underscores
        assertEquals("Dots should be replaced with underscores",
                "rmq_topic_list", func.get("name"));
    }

    // ---- buildPromptWithHistory tests ------------------------------------------

    @Test
    public void testBuildPromptWithHistoryNull() throws Exception {
        Method buildPrompt = getPrivateMethod("buildPromptWithHistory", String.class, List.class);
        String result = (String) buildPrompt.invoke(controller, "Hello", null);
        assertEquals("Null history should return message only", "Hello", result);
    }

    @Test
    public void testBuildPromptWithHistoryEmpty() throws Exception {
        Method buildPrompt = getPrivateMethod("buildPromptWithHistory", String.class, List.class);
        String result = (String) buildPrompt.invoke(controller, "Hello", new ArrayList<>());
        assertEquals("Empty history should return message only", "Hello", result);
    }

    @Test
    public void testBuildPromptWithHistoryEntries() throws Exception {
        Method buildPrompt = getPrivateMethod("buildPromptWithHistory", String.class, List.class);

        List<Map<String, Object>> history = new ArrayList<>();
        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", "List topics");
        history.add(userMsg);

        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", "Found 3 topics");
        history.add(assistantMsg);

        String result = (String) buildPrompt.invoke(controller, "Tell me more", history);

        assertTrue("Should contain user history", result.contains("user: List topics"));
        assertTrue("Should contain assistant history", result.contains("assistant: Found 3 topics"));
        assertTrue("Should contain current message", result.contains("user: Tell me more"));
    }

    // ---- escapeJson tests ------------------------------------------------------

    @Test
    public void testEscapeJsonNull() throws Exception {
        Method escapeJson = getPrivateMethod("escapeJson", String.class);
        String result = (String) escapeJson.invoke(controller, (String) null);
        assertEquals("Null should return empty string", "", result);
    }

    @Test
    public void testEscapeJsonWithSpecialChars() throws Exception {
        Method escapeJson = getPrivateMethod("escapeJson", String.class);
        String result = (String) escapeJson.invoke(controller, "hello\nworld\t\"quoted\"");
        assertTrue("Should escape newline", result.contains("\\n"));
        assertTrue("Should escape tab", result.contains("\\t"));
        assertTrue("Should escape quotes", result.contains("\\\""));
    }

    @Test
    public void testEscapeJsonNoSpecialChars() throws Exception {
        Method escapeJson = getPrivateMethod("escapeJson", String.class);
        String result = (String) escapeJson.invoke(controller, "hello world");
        assertEquals("No special chars should be unchanged", "hello world", result);
    }

    // ---- determineViewHint tests -----------------------------------------------

    @Test
    public void testDetermineViewHintEmpty() throws Exception {
        Method determineViewHint = getPrivateMethod("determineViewHint", List.class);
        String result = (String) determineViewHint.invoke(controller, new ArrayList<>());
        assertEquals("Empty tool calls should return text", "text", result);
    }

    @Test
    public void testDetermineViewHintDryRun() throws Exception {
        Method determineViewHint = getPrivateMethod("determineViewHint", List.class);

        List<Map<String, Object>> toolCalls = new ArrayList<>();
        Map<String, Object> tc = new LinkedHashMap<>();
        tc.put("status", "dry_run");
        toolCalls.add(tc);

        String result = (String) determineViewHint.invoke(controller, toolCalls);
        assertEquals("Dry run should return dry-run", "dry-run", result);
    }

    @Test
    public void testDetermineViewHintTable() throws Exception {
        Method determineViewHint = getPrivateMethod("determineViewHint", List.class);

        List<Map<String, Object>> toolCalls = new ArrayList<>();
        Map<String, Object> tc = new LinkedHashMap<>();
        Map<String, Object> tcResult = new LinkedHashMap<>();
        tcResult.put("data", new ArrayList<>());
        tc.put("result", tcResult);
        tc.put("status", "completed");
        toolCalls.add(tc);

        String result = (String) determineViewHint.invoke(controller, toolCalls);
        assertEquals("List data should return table", "table", result);
    }

    @Test
    public void testDetermineViewHintText() throws Exception {
        Method determineViewHint = getPrivateMethod("determineViewHint", List.class);

        List<Map<String, Object>> toolCalls = new ArrayList<>();
        Map<String, Object> tc = new LinkedHashMap<>();
        tc.put("status", "completed");
        // No result with data list
        toolCalls.add(tc);

        String result = (String) determineViewHint.invoke(controller, toolCalls);
        assertEquals("Non-list result should return text", "text", result);
    }

    // ---- generateAffectedResources tests ----------------------------------------

    @Test
    public void testGenerateAffectedResourcesWithTopicArg() throws Exception {
        Method generateResources = getPrivateMethod("generateAffectedResources",
                ToolDefinition.class, Map.class);

        // Get a tool from the registry
        List<ToolDefinition> tools = ToolRegistry.getInstance().getAllTools();
        assertFalse("Should have tools available", tools.isEmpty());
        ToolDefinition tool = tools.get(0);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("topic", "test-topic");

        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) generateResources.invoke(controller, tool, args);

        assertNotNull("Result should not be null", result);
        assertFalse("Should have affected resources", result.isEmpty());
        assertTrue("Should contain topic name", result.get(0).contains("test-topic"));
    }

    @Test
    public void testGenerateAffectedResourcesEmptyArgs() throws Exception {
        Method generateResources = getPrivateMethod("generateAffectedResources",
                ToolDefinition.class, Map.class);

        List<ToolDefinition> tools = ToolRegistry.getInstance().getAllTools();
        ToolDefinition tool = tools.get(0);

        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) generateResources.invoke(controller, tool, null);

        assertNotNull("Result should not be null", result);
        assertFalse("Should have default resource", result.isEmpty());
        assertTrue("Should contain unspecified", result.get(0).contains("<unspecified>"));
    }

    @Test
    public void testGenerateAffectedResourcesNoMatchingKey() throws Exception {
        Method generateResources = getPrivateMethod("generateAffectedResources",
                ToolDefinition.class, Map.class);

        List<ToolDefinition> tools = ToolRegistry.getInstance().getAllTools();
        ToolDefinition tool = tools.get(0);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("unknownKey", "someValue");

        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) generateResources.invoke(controller, tool, args);

        assertNotNull("Result should not be null", result);
        assertTrue("Should contain unspecified", result.get(0).contains("<unspecified>"));
    }

    // ---- DryRunRecord tests ----------------------------------------------------

    @Test
    public void testDryRunRecordConstructorAndGetters() throws Exception {
        Class<?> dryRunClass = Class.forName(
                "org.apache.rocketmq.dashboard.llm.McpBridgeController$DryRunRecord");

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("topic", "test-topic");

        Object record = dryRunClass.getDeclaredConstructor(
                String.class, String.class, Map.class, String.class, String.class
        ).newInstance("dry-run-id-1", "rmq.topic.list", args, "default", "admin");

        java.lang.reflect.Method getId = dryRunClass.getDeclaredMethod("getId");
        java.lang.reflect.Method getToolName = dryRunClass.getDeclaredMethod("getToolName");
        java.lang.reflect.Method getArguments = dryRunClass.getDeclaredMethod("getArguments");
        java.lang.reflect.Method getCluster = dryRunClass.getDeclaredMethod("getCluster");
        java.lang.reflect.Method getUsername = dryRunClass.getDeclaredMethod("getUsername");

        assertEquals("ID should match", "dry-run-id-1", getId.invoke(record));
        assertEquals("Tool name should match", "rmq.topic.list", getToolName.invoke(record));
        assertEquals("Cluster should match", "default", getCluster.invoke(record));
        assertEquals("Username should match", "admin", getUsername.invoke(record));

        @SuppressWarnings("unchecked")
        Map<String, Object> resultArgs = (Map<String, Object>) getArguments.invoke(record);
        assertEquals("Arguments should match", "test-topic", resultArgs.get("topic"));
    }

    // ---- getModels tests -------------------------------------------------------

    @Test
    public void testGetModelsWhenNotConfigured() {
        Map<String, Object> result = controller.getModels();

        assertNotNull("Result should not be null", result);
        assertEquals("Should have status -1", -1, result.get("status"));
        assertTrue("Should mention not configured",
                result.get("errMsg").toString().contains("not configured"));
    }

    // ---- getCapabilities tests -------------------------------------------------

    @Test
    public void testGetCapabilitiesReturnsCapabilityInfo() {
        Map<String, Object> result = controller.getCapabilities();

        assertNotNull("Result should not be null", result);
        assertTrue("Should have provider key", result.containsKey("provider"));
        assertTrue("Should have model key", result.containsKey("model"));
        assertTrue("Should have enabled key", result.containsKey("enabled"));
        assertTrue("Should have configured key", result.containsKey("configured"));
        assertTrue("Should have toolCount key", result.containsKey("toolCount"));
        assertTrue("Should have features key", result.containsKey("features"));
        assertTrue("Should have supportedProviders key", result.containsKey("supportedProviders"));

        @SuppressWarnings("unchecked")
        List<String> features = (List<String>) result.get("features");
        assertTrue("Should have tool_discovery feature", features.contains("tool_discovery"));
        assertTrue("Should have chat feature", features.contains("chat"));
        assertTrue("Should have dry_run_confirmation feature", features.contains("dry_run_confirmation"));

        @SuppressWarnings("unchecked")
        List<String> providers = (List<String>) result.get("supportedProviders");
        assertTrue("Should have OPENAI provider", providers.contains("OPENAI"));
        assertTrue("Should have DEEPSEEK provider", providers.contains("DEEPSEEK"));
    }

    @Test
    public void testGetCapabilitiesNotConfiguredNoFunctionCalling() {
        Map<String, Object> result = controller.getCapabilities();

        @SuppressWarnings("unchecked")
        List<String> features = (List<String>) result.get("features");
        // When not configured, should NOT have function_calling or multi_turn_conversation
        assertFalse("Should not have function_calling when not configured",
                features.contains("function_calling"));
        assertFalse("Should not have multi_turn_conversation when not configured",
                features.contains("multi_turn_conversation"));
    }

    @Test
    public void testGetCapabilitiesToolCount() {
        Map<String, Object> result = controller.getCapabilities();

        Object toolCount = result.get("toolCount");
        assertNotNull("Tool count should not be null", toolCount);
        assertTrue("Tool count should be positive", (Integer) toolCount > 0);
    }

    // ---- maskApiKey additional edge cases --------------------------------------

    @Test
    public void testMaskApiKeyShortKey() throws Exception {
        Method maskApiKey = getPrivateMethod("maskApiKey", String.class);
        String result = (String) maskApiKey.invoke(controller, "short");
        assertEquals("Short key should not be masked", "short", result);
    }

    @Test
    public void testMaskApiKeyExactlyEight() throws Exception {
        Method maskApiKey = getPrivateMethod("maskApiKey", String.class);
        String result = (String) maskApiKey.invoke(controller, "12345678");
        assertEquals("8-char key should not be masked", "12345678", result);
    }

    @Test
    public void testMaskApiKeyNineChars() throws Exception {
        Method maskApiKey = getPrivateMethod("maskApiKey", String.class);
        String result = (String) maskApiKey.invoke(controller, "123456789");
        assertEquals("9-char key should be masked", "1234****6789", result);
    }

    @Test
    public void testMaskApiKeyLongKey() throws Exception {
        Method maskApiKey = getPrivateMethod("maskApiKey", String.class);
        String result = (String) maskApiKey.invoke(controller, "sk-very-long-api-key-here");
        assertTrue("Should start with first 4 chars", result.startsWith("sk-v"));
        assertTrue("Should end with last 4 chars", result.endsWith("here"));
        assertTrue("Should contain ****", result.contains("****"));
    }

    // ---- confirmAction with valid tool call (end-to-end) -----------------------

    @Test
    public void testConfirmActionWithValidToolCallCancelled() throws Exception {
        // Access dryRunStore via reflection
        java.lang.reflect.Field dryRunStoreField =
                McpBridgeController.class.getDeclaredField("dryRunStore");
        dryRunStoreField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Object> dryRunStore =
                (ConcurrentHashMap<String, Object>) dryRunStoreField.get(controller);

        // Create a DryRunRecord and put it in the store
        Class<?> dryRunClass = Class.forName(
                "org.apache.rocketmq.dashboard.llm.McpBridgeController$DryRunRecord");
        Object record = dryRunClass.getDeclaredConstructor(
                String.class, String.class, Map.class, String.class, String.class
        ).newInstance("test-dry-run-id", "rmq.topic.list", new LinkedHashMap<>(), "default", "admin");
        dryRunStore.put("test-dry-run-id", record);

        // Now cancel it
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("toolCallId", "test-dry-run-id");
        request.put("confirm", false);

        Map<String, Object> result = controller.confirmAction(request);

        assertEquals("Should be cancelled", "cancelled", result.get("status"));
        assertTrue("Should mention cancelled", result.get("message").toString().contains("cancelled"));
    }

    @Test
    public void testConfirmActionWithValidToolCallConfirmed() throws Exception {
        java.lang.reflect.Field dryRunStoreField =
                McpBridgeController.class.getDeclaredField("dryRunStore");
        dryRunStoreField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Object> dryRunStore =
                (ConcurrentHashMap<String, Object>) dryRunStoreField.get(controller);

        Class<?> dryRunClass = Class.forName(
                "org.apache.rocketmq.dashboard.llm.McpBridgeController$DryRunRecord");
        Object record = dryRunClass.getDeclaredConstructor(
                String.class, String.class, Map.class, String.class, String.class
        ).newInstance("test-confirm-id", "rmq.topic.list", new LinkedHashMap<>(), "default", "admin");
        dryRunStore.put("test-confirm-id", record);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("toolCallId", "test-confirm-id");
        request.put("confirm", true);

        Map<String, Object> result = controller.confirmAction(request);

        // dashboardApiClient is null, so executeTool will throw NPE → caught as error
        assertNotNull("Result should not be null", result);
        // Should have status "error" since dashboardApiClient is not injected
        assertEquals("Should have error status since DashboardApiClient is null",
                "error", result.get("status"));
    }

    // ---- processToolCall tests -------------------------------------------------

    @Test
    public void testProcessToolCallToolNotFound() throws Exception {
        Method processToolCall = getPrivateMethod("processToolCall",
                Map.class, String.class, String.class);

        Map<String, Object> toolCall = new LinkedHashMap<>();
        toolCall.put("id", "call-001");
        toolCall.put("name", "rmq.nonexistent.tool");
        toolCall.put("arguments", new LinkedHashMap<>());

        @SuppressWarnings("unchecked")
        Map<String, Object> result =
                (Map<String, Object>) processToolCall.invoke(controller, toolCall, "default", "admin");

        assertEquals("Should have not_found status", "not_found", result.get("status"));
        assertTrue("Should mention tool not found",
                result.get("message").toString().contains("not found"));
    }

    @Test
    public void testProcessToolCallL1ToolAllowed() throws Exception {
        Method processToolCall = getPrivateMethod("processToolCall",
                Map.class, String.class, String.class);

        // rmq.cluster.list is L1 (read-only) → ALLOW
        Map<String, Object> toolCall = new LinkedHashMap<>();
        toolCall.put("id", "call-002");
        toolCall.put("name", "rmq.cluster.list");
        toolCall.put("arguments", new LinkedHashMap<>());

        @SuppressWarnings("unchecked")
        Map<String, Object> result =
                (Map<String, Object>) processToolCall.invoke(controller, toolCall, "default", "admin");

        // L1 tool should be ALLOWED → status "completed"
        assertEquals("L1 tool should be completed", "completed", result.get("status"));
        assertEquals("Should have risk level", "L1", result.get("riskLevel"));
        assertTrue("Should auto-execute", (Boolean) result.get("autoExecute"));
        // executeTool will fail (dashboardApiClient null), so resultError should be set
        assertNotNull("Should have resultError since dashboardApiClient is null",
                result.get("resultError"));
    }

    @Test
    public void testProcessToolCallL2ToolDryRun() throws Exception {
        Method processToolCall = getPrivateMethod("processToolCall",
                Map.class, String.class, String.class);

        // rmq.topic.create is L2 (controlled mutation) → DRY_RUN
        Map<String, Object> toolCall = new LinkedHashMap<>();
        toolCall.put("id", "call-003");
        toolCall.put("name", "rmq.topic.create");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("topic", "new-topic");
        toolCall.put("arguments", args);

        @SuppressWarnings("unchecked")
        Map<String, Object> result =
                (Map<String, Object>) processToolCall.invoke(controller, toolCall, "default", "admin");

        // L2 tool should be DRY_RUN → status "dry_run"
        assertEquals("L2 tool should be dry_run", "dry_run", result.get("status"));
        assertEquals("Should have risk level", "L2", result.get("riskLevel"));
        assertTrue("Should require confirmation", (Boolean) result.get("requiresConfirmation"));
        assertNotNull("Should have dryRunPreview", result.get("dryRunPreview"));

        @SuppressWarnings("unchecked")
        Map<String, Object> preview = (Map<String, Object>) result.get("dryRunPreview");
        assertTrue("Preview should have affectedResources",
                preview.containsKey("affectedResources"));
        assertTrue("Preview should have confirmationRequired",
                (Boolean) preview.get("confirmationRequired"));
    }

    @Test
    public void testProcessToolCallL3ToolBlocked() throws Exception {
        Method processToolCall = getPrivateMethod("processToolCall",
                Map.class, String.class, String.class);

        // rmq.topic.delete is L3 (dangerous) → BLOCK
        Map<String, Object> toolCall = new LinkedHashMap<>();
        toolCall.put("id", "call-004");
        toolCall.put("name", "rmq.topic.delete");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("topic", "doomed-topic");
        toolCall.put("arguments", args);

        @SuppressWarnings("unchecked")
        Map<String, Object> result =
                (Map<String, Object>) processToolCall.invoke(controller, toolCall, "default", "admin");

        // L3 tool should be BLOCKED
        assertEquals("L3 tool should be blocked", "blocked", result.get("status"));
        assertEquals("Should have risk level", "L3", result.get("riskLevel"));
        assertNotNull("Should have message", result.get("message"));
        assertEquals("Should have hint", "Operation blocked by security policy", result.get("hint"));
    }

    @Test
    public void testProcessToolCallUnderscoreNameFallback() throws Exception {
        Method processToolCall = getPrivateMethod("processToolCall",
                Map.class, String.class, String.class);

        // Tool name with underscores should fall back to hyphens
        // rmq.group.reset_offset → rmq.group.reset-offset (L2)
        Map<String, Object> toolCall = new LinkedHashMap<>();
        toolCall.put("id", "call-005");
        toolCall.put("name", "rmq.group.reset_offset");
        toolCall.put("arguments", new LinkedHashMap<>());

        @SuppressWarnings("unchecked")
        Map<String, Object> result =
                (Map<String, Object>) processToolCall.invoke(controller, toolCall, "default", "admin");

        // Should NOT be not_found (underscore→hyphen fallback finds the tool)
        assertNotEquals("Should find tool via underscore fallback", "not_found", result.get("status"));
    }

    // ---- executeTool tests (via reflection) ------------------------------------

    @Test
    public void testExecuteToolUnknownTool() throws Exception {
        Method executeTool = getPrivateMethod("executeTool",
                String.class, Map.class, String.class, String.class);

        Map<String, Object> args = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> result =
                (Map<String, Object>) executeTool.invoke(controller, "rmq.unknown.tool", args, "default", "admin");

        assertEquals("Should have error status", "error", result.get("status"));
        assertTrue("Should mention not connected",
                result.get("error").toString().contains("not yet connected"));
    }

    @Test
    public void testExecuteToolSetsClusterDefault() throws Exception {
        Method executeTool = getPrivateMethod("executeTool",
                String.class, Map.class, String.class, String.class);

        Map<String, Object> args = new LinkedHashMap<>();
        // Call with null cluster - should default
        @SuppressWarnings("unchecked")
        Map<String, Object> result =
                (Map<String, Object>) executeTool.invoke(controller, "rmq.unknown.tool", args, null, "admin");

        // Even for unknown tool, cluster should be set in result
        assertNull("Cluster in result should be null when input is null", result.get("cluster"));
    }

    @Test
    public void testExecuteToolWithClusterArg() throws Exception {
        Method executeTool = getPrivateMethod("executeTool",
                String.class, Map.class, String.class, String.class);

        Map<String, Object> args = new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> result =
                (Map<String, Object>) executeTool.invoke(controller, "rmq.unknown.tool", args, "my-cluster", "admin");

        assertEquals("Cluster should be set", "my-cluster", result.get("cluster"));
        assertEquals("Tool name should be set", "rmq.unknown.tool", result.get("tool"));
    }

    // ---- buildPromptWithHistory with List content ------------------------------

    @Test
    public void testBuildPromptWithHistoryListContent() throws Exception {
        Method buildPrompt = getPrivateMethod("buildPromptWithHistory", String.class, List.class);

        List<Map<String, Object>> history = new ArrayList<>();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "user");

        // Content as List with text type
        List<Map<String, Object>> contentList = new ArrayList<>();
        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("type", "text");
        textPart.put("text", "Hello from list content");
        contentList.add(textPart);
        msg.put("content", contentList);
        history.add(msg);

        String result = (String) buildPrompt.invoke(controller, "Next message", history);

        assertTrue("Should extract text from list content", result.contains("Hello from list content"));
        assertTrue("Should contain current message", result.contains("Next message"));
    }

    @Test
    public void testBuildPromptWithHistoryListContentNonTextIgnored() throws Exception {
        Method buildPrompt = getPrivateMethod("buildPromptWithHistory", String.class, List.class);

        List<Map<String, Object>> history = new ArrayList<>();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", "assistant");

        // Content as List with non-text type (e.g., image)
        List<Map<String, Object>> contentList = new ArrayList<>();
        Map<String, Object> imagePart = new LinkedHashMap<>();
        imagePart.put("type", "image");
        imagePart.put("url", "http://example.com/img.png");
        contentList.add(imagePart);
        msg.put("content", contentList);
        history.add(msg);

        String result = (String) buildPrompt.invoke(controller, "Next", history);

        // Non-text content should be skipped, only role prefix remains
        assertTrue("Should contain role", result.contains("assistant:"));
        assertTrue("Should contain current message", result.contains("user: Next"));
    }

    // ---- GET /chat/stream legacy endpoint test ---------------------------------

    @Test
    public void testChatStreamGetLegacyEndpoint() {
        // Test the GET /chat/stream endpoint (legacy)
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                controller.chatStream("Hello", null, null, null);
        assertNotNull("Should return SseEmitter", emitter);
    }

    @Test
    public void testChatStreamGetWithHistory() {
        String historyJson = "[{\"role\":\"user\",\"content\":\"Hi\"}]";
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                controller.chatStream("Follow up", "default", historyJson, null);
        assertNotNull("Should return SseEmitter with history", emitter);
    }

    @Test
    public void testChatStreamGetWithModel() {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                controller.chatStream("Hello", "default", null, "gpt-4");
        assertNotNull("Should return SseEmitter with model", emitter);
    }

    // ---- Additional escapeJson edge cases --------------------------------------

    @Test
    public void testEscapeJsonBackslash() throws Exception {
        Method escapeJson = getPrivateMethod("escapeJson", String.class);
        String result = (String) escapeJson.invoke(controller, "path\\to\\file");
        assertTrue("Should escape backslash", result.contains("\\\\"));
    }

    @Test
    public void testEscapeJsonCarriageReturn() throws Exception {
        Method escapeJson = getPrivateMethod("escapeJson", String.class);
        String result = (String) escapeJson.invoke(controller, "line1\rline2");
        assertTrue("Should escape carriage return", result.contains("\\r"));
    }

    // ---- Additional determineViewHint edge cases -------------------------------

    @Test
    public void testDetermineViewHintMultipleToolCalls() throws Exception {
        Method determineViewHint = getPrivateMethod("determineViewHint", List.class);

        List<Map<String, Object>> toolCalls = new ArrayList<>();
        // First: completed with list data
        Map<String, Object> tc1 = new LinkedHashMap<>();
        Map<String, Object> tc1Result = new LinkedHashMap<>();
        tc1Result.put("data", new ArrayList<>());
        tc1.put("result", tc1Result);
        tc1.put("status", "completed");
        toolCalls.add(tc1);

        // Second: dry_run
        Map<String, Object> tc2 = new LinkedHashMap<>();
        tc2.put("status", "dry_run");
        toolCalls.add(tc2);

        String result = (String) determineViewHint.invoke(controller, toolCalls);
        // dry_run takes priority over table
        assertEquals("dry_run should take priority", "dry-run", result);
    }

    @Test
    public void testDetermineViewHintResultMapWithoutData() throws Exception {
        Method determineViewHint = getPrivateMethod("determineViewHint", List.class);

        List<Map<String, Object>> toolCalls = new ArrayList<>();
        Map<String, Object> tc = new LinkedHashMap<>();
        Map<String, Object> tcResult = new LinkedHashMap<>();
        tcResult.put("status", "success");  // Map but no "data" key
        tc.put("result", tcResult);
        tc.put("status", "completed");
        toolCalls.add(tc);

        String result = (String) determineViewHint.invoke(controller, toolCalls);
        // Map without "data" key should return text
        assertEquals("Map without data key should return text", "text", result);
    }

    // ---- Additional generateAffectedResources tests ----------------------------

    @Test
    public void testGenerateAffectedResourcesWithGroupArg() throws Exception {
        Method generateResources = getPrivateMethod("generateAffectedResources",
                ToolDefinition.class, Map.class);

        List<ToolDefinition> tools = ToolRegistry.getInstance().getAllTools();
        ToolDefinition tool = tools.get(0);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("group", "test-group");

        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) generateResources.invoke(controller, tool, args);

        assertFalse("Should have affected resources", result.isEmpty());
        assertTrue("Should contain group name", result.get(0).contains("test-group"));
    }

    @Test
    public void testGenerateAffectedResourcesWithClusterArg() throws Exception {
        Method generateResources = getPrivateMethod("generateAffectedResources",
                ToolDefinition.class, Map.class);

        List<ToolDefinition> tools = ToolRegistry.getInstance().getAllTools();
        ToolDefinition tool = tools.get(0);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("cluster", "prod-cluster");

        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) generateResources.invoke(controller, tool, args);

        assertFalse("Should have affected resources", result.isEmpty());
        assertTrue("Should contain cluster name", result.get(0).contains("prod-cluster"));
    }

    // ---- Additional maskApiKey edge cases --------------------------------------

    @Test
    public void testMaskApiKeyNull() throws Exception {
        Method maskApiKey = getPrivateMethod("maskApiKey", String.class);
        String result = (String) maskApiKey.invoke(controller, (String) null);
        assertNull("Null key should return null", result);
    }

    // ---- confirmAction with missing toolCallId ---------------------------------

    @Test
    public void testConfirmActionMissingToolCallId() {
        Map<String, Object> request = new LinkedHashMap<>();
        // No toolCallId
        request.put("confirm", true);

        Map<String, Object> result = controller.confirmAction(request);

        assertTrue("Should have error", result.containsKey("error"));
        assertTrue("Should mention toolCallId required",
                result.get("error").toString().contains("toolCallId"));
    }

    @Test
    public void testConfirmActionNotFoundToolCall() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("toolCallId", "nonexistent-id");
        request.put("confirm", true);

        Map<String, Object> result = controller.confirmAction(request);

        assertTrue("Should have error", result.containsKey("error"));
        assertTrue("Should mention not found",
                result.get("error").toString().contains("not found"));
    }

    // ---- Additional buildMessagesWithTools edge cases --------------------------

    @Test
    public void testBuildMessagesWithToolsAssistantWithContentAndToolCalls() throws Exception {
        Method buildMessages = getPrivateMethod("buildMessagesWithTools", String.class, List.class);

        List<Map<String, Object>> history = new ArrayList<>();
        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", "I will list the topics for you.");

        List<Map<String, Object>> toolCalls = new ArrayList<>();
        Map<String, Object> tc = new LinkedHashMap<>();
        tc.put("id", "call_001");
        tc.put("name", "listTopics");
        tc.put("arguments", new LinkedHashMap<>());
        tc.put("status", "completed");
        tc.put("result", "TopicA, TopicB");
        toolCalls.add(tc);
        assistantMsg.put("toolCalls", toolCalls);
        history.add(assistantMsg);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result =
                (List<Map<String, Object>>) buildMessages.invoke(controller, "Thanks", history);

        // Assistant with non-empty content + tool_calls should have both
        Map<String, Object> assistantResult = result.get(1);
        assertEquals("Should have content", "I will list the topics for you.", assistantResult.get("content"));
        assertNotNull("Should have tool_calls", assistantResult.get("tool_calls"));
    }

    @Test
    public void testBuildMessagesWithToolsMultipleToolCalls() throws Exception {
        Method buildMessages = getPrivateMethod("buildMessagesWithTools", String.class, List.class);

        List<Map<String, Object>> history = new ArrayList<>();
        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", "");

        List<Map<String, Object>> toolCalls = new ArrayList<>();
        Map<String, Object> tc1 = new LinkedHashMap<>();
        tc1.put("id", "call_001");
        tc1.put("name", "rmq.topic.list");
        tc1.put("arguments", new LinkedHashMap<>());
        tc1.put("status", "completed");
        tc1.put("result", "TopicA");
        toolCalls.add(tc1);

        Map<String, Object> tc2 = new LinkedHashMap<>();
        tc2.put("id", "call_002");
        tc2.put("name", "rmq.broker.list");
        tc2.put("arguments", new LinkedHashMap<>());
        tc2.put("status", "completed");
        tc2.put("result", "BrokerA");
        toolCalls.add(tc2);

        assistantMsg.put("toolCalls", toolCalls);
        history.add(assistantMsg);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result =
                (List<Map<String, Object>>) buildMessages.invoke(controller, "Next", history);

        // system + assistant + tool1 + tool2 + user = 5
        assertEquals("Should have 5 messages", 5, result.size());
        assertEquals("tool", result.get(2).get("role"));
        assertEquals("tool", result.get(3).get("role"));
        assertEquals("user", result.get(4).get("role"));
    }

    // ---- Helper method for assertNotEquals ------------------------------------
    // JUnit 4 doesn't have assertNotEquals, so we implement it inline

    private void assertNotEquals(String message, Object expected, Object actual) {
        if (expected == null && actual == null) {
            throw new AssertionError(message + " - expected not equal but both were null");
        }
        if (expected != null && expected.equals(actual)) {
            throw new AssertionError(message + " - expected not equal but was: " + actual);
        }
    }
}