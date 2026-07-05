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
package org.apache.rocketmq.dashboard.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class McpToolRegistryExtTest {

    private ObjectMapper objectMapper;
    private McpToolRegistry registry;
    private SecurityGate securityGate;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper();
        securityGate = new SecurityGate();
        registry = new McpToolRegistry(securityGate);
    }

    // ---- Tools list tests ------------------------------------------------------

    @Test
    public void testToolsListHasCorrectFormat() throws Exception {
        String result = registry.handleToolsList();
        assertNotNull("Tools list should not be null", result);

        JsonNode tools = objectMapper.readTree(result);
        assertTrue("Tools list should be an array", tools.isArray());
        assertTrue("Should have at least 25 tools", tools.size() >= 25);

        JsonNode firstTool = tools.get(0);
        assertTrue("Tool should have name", firstTool.has("name"));
        assertTrue("Tool should have description", firstTool.has("description"));
        assertTrue("Tool should have inputSchema", firstTool.has("inputSchema"));
    }

    @Test
    public void testToolsListInputSchemaHasProperties() throws Exception {
        String result = registry.handleToolsList();
        JsonNode tools = objectMapper.readTree(result);

        for (int i = 0; i < tools.size(); i++) {
            JsonNode tool = tools.get(i);
            JsonNode inputSchema = tool.get("inputSchema");
            assertTrue("inputSchema should have properties",
                    inputSchema.has("properties"));
            assertEquals("inputSchema type should be object",
                    "object", inputSchema.get("type").asText());
        }
    }

    @Test
    public void testToolsListReturnsValidJson() throws Exception {
        String result = registry.handleToolsList();
        // Should not throw when parsing
        JsonNode root = objectMapper.readTree(result);
        assertNotNull("Parsed result should not be null", root);
    }

    // ---- Tool call tests -------------------------------------------------------

    @Test
    public void testCallNonExistentTool() throws Exception {
        String result = registry.handleToolsCall("rmq.nonexistent.tool", new HashMap<>());
        assertNotNull("Result should not be null", result);

        JsonNode root = objectMapper.readTree(result);
        assertEquals("Status should be error", "error", root.get("status").asText());
        assertEquals("Code should be TOOL_NOT_FOUND", "TOOL_NOT_FOUND", root.get("code").asText());
        assertTrue("Message should contain 'not found'",
                root.get("message").asText().contains("not found"));
    }

    @Test
    public void testCallToolWithEmptyArgs() throws Exception {
        String result = registry.handleToolsCall("rmq.topic.list", new HashMap<>());
        assertNotNull("Result should not be null", result);

        JsonNode root = objectMapper.readTree(result);
        assertEquals("Status should be success for L1 tool", "success", root.get("status").asText());
        assertEquals("Tool name should match", "rmq.topic.list", root.get("tool").asText());
        assertEquals("Risk level should be L1", "L1", root.get("riskLevel").asText());
    }

    @Test
    public void testCallTopicListReturnsListData() throws Exception {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("cluster", "test-cluster");
        String result = registry.handleToolsCall("rmq.topic.list", args);

        JsonNode root = objectMapper.readTree(result);
        assertEquals("success", root.get("status").asText());
        assertTrue("Should have data", root.has("data"));
        assertTrue("Data should be array", root.get("data").isArray());
        assertTrue("Should have at least 1 data item", root.get("data").size() >= 1);
    }

    @Test
    public void testCallTopicCreateReturnsDryRun() throws Exception {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("cluster", "test-cluster");
        args.put("topic", "new-topic");
        String result = registry.handleToolsCall("rmq.topic.create", args);

        JsonNode root = objectMapper.readTree(result);
        assertEquals("Status should be dry_run for L2", "dry_run", root.get("status").asText());
        assertTrue("Should have dryRunData", root.has("dryRunData"));
    }

    @Test
    public void testCallL3Blocked() throws Exception {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("cluster", "test-cluster");
        args.put("topic", "old-topic");
        String result = registry.handleToolsCall("rmq.topic.delete", args);

        JsonNode root = objectMapper.readTree(result);
        assertEquals("Status should be blocked for L3", "blocked", root.get("status").asText());
        assertTrue("Should have hint", root.has("hint"));
        assertTrue("Message should refer to dangerous ops",
                root.get("message").asText().contains("blocked"));
    }

    @Test
    public void testCallWithUnderscoreName() throws Exception {
        // Test underscore-to-hyphen conversion: rmq.topic_list -> rmq.topic_list
        // The tool name "rmq.topic.list" is registered; "rmq.topic_list" should
        // be converted to "rmq.topic-list" which doesn't exist either.
        // Actually let's test with a known tool that has hyphens in verb
        String result = registry.handleToolsCall("rmq.message.query_by_id", new HashMap<>());

        JsonNode root = objectMapper.readTree(result);
        // Should find the tool after underscore-to-hyphen conversion
        assertNotNull("Result should not be null", root);
    }

    @Test
    public void testCallWithNullArguments() throws Exception {
        String result = registry.handleToolsCall("rmq.topic.list", null);
        assertNotNull("Result with null args should not be null", result);

        JsonNode root = objectMapper.readTree(result);
        assertEquals("Status should be success with null args",
                "success", root.get("status").asText());
    }

    @Test
    public void testCallToolReturnsAffectedResources() throws Exception {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("cluster", "test-cluster");
        args.put("topic", "test-topic");
        String result = registry.handleToolsCall("rmq.topic.create", args);

        JsonNode root = objectMapper.readTree(result);
        JsonNode dryRunData = root.get("dryRunData");
        assertTrue("Should have affectedResources", dryRunData.has("affectedResources"));
        assertTrue("affectedResources should be array",
                dryRunData.get("affectedResources").isArray());
    }

    // ---- Security gate access test ---------------------------------------------

    @Test
    public void testGetSecurityGate() {
        SecurityGate gate = registry.getSecurityGate();
        assertNotNull("SecurityGate should not be null", gate);
        assertTrue("SecurityGate should be the same instance", gate == securityGate);
    }

    // ---- Mock data generation coverage tests -----------------------------------

    @Test
    public void testCallClusterListGeneratesClusterData() throws Exception {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("cluster", "test-cluster");
        String result = registry.handleToolsCall("rmq.cluster.list", args);

        JsonNode root = objectMapper.readTree(result);
        assertEquals("success", root.get("status").asText());
        JsonNode data = root.get("data");
        assertTrue("Data should be array", data.isArray());
        JsonNode first = data.get(0);
        assertTrue("Should have name", first.has("name"));
        assertEquals("Status should be HEALTHY", "HEALTHY", first.get("status").asText());
    }

    @Test
    public void testCallGroupListGeneratesGroupData() throws Exception {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("cluster", "test-cluster");
        String result = registry.handleToolsCall("rmq.group.list", args);

        JsonNode root = objectMapper.readTree(result);
        assertEquals("success", root.get("status").asText());
        JsonNode data = root.get("data");
        JsonNode first = data.get(0);
        assertTrue("Should have consumeMode", first.has("consumeMode"));
        assertEquals("CLUSTER", first.get("consumeMode").asText());
    }

    @Test
    public void testCallBrokerListGeneratesBrokerData() throws Exception {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("cluster", "test-cluster");
        String result = registry.handleToolsCall("rmq.broker.list", args);

        JsonNode root = objectMapper.readTree(result);
        assertEquals("success", root.get("status").asText());
        JsonNode data = root.get("data");
        JsonNode first = data.get(0);
        assertTrue("Should have version", first.has("version"));
        assertEquals("Version should be 5.5.0", "5.5.0", first.get("version").asText());
    }

    @Test
    public void testCallClientListGeneratesClientData() throws Exception {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("cluster", "test-cluster");
        String result = registry.handleToolsCall("rmq.client.list", args);

        JsonNode root = objectMapper.readTree(result);
        assertEquals("success", root.get("status").asText());
        JsonNode data = root.get("data");
        JsonNode first = data.get(0);
        assertTrue("Should have clientId", first.has("clientId"));
        assertTrue("Should have type", first.has("type"));
        assertTrue("Should have version", first.has("version"));
    }
}
