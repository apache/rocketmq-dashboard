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
package org.apache.rocketmq.dashboard.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.rocketmq.dashboard.mcp.resources.ResourceProvider;
import org.apache.rocketmq.dashboard.mcp.tools.McpToolRegistry;
import org.apache.rocketmq.dashboard.mcp.tools.SecurityGate;
import org.apache.rocketmq.dashboard.mcp.transport.StdioTransport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for the MCP Server module.
 */
public class McpServerTest {

    private ObjectMapper objectMapper;
    private McpProtocolHandler protocolHandler;
    private SecurityGate securityGate;
    private McpToolRegistry toolRegistry;
    private ResourceProvider resourceProvider;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper();
        securityGate = new SecurityGate();
        toolRegistry = new McpToolRegistry(securityGate);
        resourceProvider = new ResourceProvider();
        protocolHandler = new McpProtocolHandler(toolRegistry, resourceProvider);
    }

    @After
    public void tearDown() {
        // no-op
    }

    // ---- Test 1: tools/list returns all 30+ tools ---------------------------------

    @Test
    public void testToolsList() throws Exception {
        String request = buildRequest("1", "tools/list", null);
        String response = protocolHandler.handleMessage(request);

        JsonNode root = objectMapper.readTree(response);
        assertNotNull("Response should not be null", root);
        assertTrue("Response should have result", root.has("result"));
        assertFalse("Response should not have error", root.has("error"));

        JsonNode result = root.get("result");
        assertTrue("Result should be an array", result.isArray());
        assertTrue("Should have at least 25 tools, got: " + result.size(),
                result.size() >= 25);

        // Verify first tool has expected structure
        JsonNode firstTool = result.get(0);
        assertTrue("Tool should have name", firstTool.has("name"));
        assertTrue("Tool should have description", firstTool.has("description"));
        assertTrue("Tool should have inputSchema", firstTool.has("inputSchema"));

        JsonNode inputSchema = firstTool.get("inputSchema");
        assertEquals("type should be object", "object", inputSchema.get("type").asText());
        assertTrue("inputSchema should have properties", inputSchema.has("properties"));
    }

    // ---- Test 2: L1 tool call (rmq.topic.list) returns data -----------------------

    @Test
    public void testL1ToolCall() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "rmq.topic.list");

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("cluster", "test-cluster");
        params.put("arguments", arguments);

        String request = buildRequest("2", "tools/call", params);
        String response = protocolHandler.handleMessage(request);

        JsonNode root = objectMapper.readTree(response);
        assertNotNull("Response should not be null", root);
        assertTrue("Response should have result", root.has("result"));

        JsonNode result = root.get("result");
        assertEquals("Status should be success", "success", result.get("status").asText());
        assertEquals("Tool name should match", "rmq.topic.list", result.get("tool").asText());
        assertEquals("Risk level should be L1", "L1", result.get("riskLevel").asText());
        assertTrue("Should have data", result.has("data"));
    }

    // ---- Test 3: L2 tool call (rmq.topic.create) returns dry-run ------------------

    @Test
    public void testL2ToolCall() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "rmq.topic.create");

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("cluster", "test-cluster");
        arguments.put("topic", "new-test-topic");
        params.put("arguments", arguments);

        String request = buildRequest("3", "tools/call", params);
        String response = protocolHandler.handleMessage(request);

        JsonNode root = objectMapper.readTree(response);
        assertNotNull("Response should not be null", root);
        assertTrue("Response should have result", root.has("result"));

        JsonNode result = root.get("result");
        assertEquals("Status should be dry_run", "dry_run", result.get("status").asText());
        assertEquals("Tool name should match", "rmq.topic.create", result.get("tool").asText());
        assertEquals("Risk level should be L2", "L2", result.get("riskLevel").asText());
        assertTrue("Should have dryRunData", result.has("dryRunData"));

        JsonNode dryRunData = result.get("dryRunData");
        assertFalse("willExecute should be false", dryRunData.get("willExecute").asBoolean());
    }

    // ---- Test 4: L3 tool call (rmq.topic.delete) returns blocked ------------------

    @Test
    public void testL3ToolCall() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "rmq.topic.delete");

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("cluster", "test-cluster");
        arguments.put("topic", "old-topic");
        params.put("arguments", arguments);

        String request = buildRequest("4", "tools/call", params);
        String response = protocolHandler.handleMessage(request);

        JsonNode root = objectMapper.readTree(response);
        assertNotNull("Response should not be null", root);
        assertTrue("Response should have result", root.has("result"));

        JsonNode result = root.get("result");
        assertEquals("Status should be blocked", "blocked", result.get("status").asText());
        assertEquals("Tool name should match", "rmq.topic.delete", result.get("tool").asText());
        assertTrue("Should have message about dangerous ops",
                result.get("message").asText().contains("blocked"));
    }

    // ---- Test 5: capabilities tool returns cluster info ----------------------------

    @Test
    public void testCapabilitiesTool() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "rmq.capabilities.detect");

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("cluster", "test-cluster");
        params.put("arguments", arguments);

        String request = buildRequest("5", "tools/call", params);
        String response = protocolHandler.handleMessage(request);

        JsonNode root = objectMapper.readTree(response);
        assertNotNull("Response should not be null", root);
        assertTrue("Response should have result", root.has("result"));

        JsonNode result = root.get("result");
        assertEquals("Status should be success", "success", result.get("status").asText());
        assertEquals("Tool name should match", "rmq.capabilities.detect",
                result.get("tool").asText());
        assertTrue("Should have data", result.has("data"));
    }

    // ---- Test 6: stdio transport JSON-RPC exchange --------------------------------

    @Test
    public void testStdioTransport() throws Exception {
        // Simulate stdin/stdout by capturing System.out and providing System.in
        String inputMessage = buildRequest("6", "initialize", null);

        InputStream originalIn = System.in;
        PrintStream originalOut = System.out;

        try {
            ByteArrayInputStream testIn = new ByteArrayInputStream(
                    inputMessage.getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream capturedOut = new ByteArrayOutputStream();
            PrintStream testOut = new PrintStream(capturedOut, true, StandardCharsets.UTF_8.name());

            System.setIn(testIn);
            System.setOut(testOut);

            StdioTransport transport = new StdioTransport();
            McpProtocolHandler handler = new McpProtocolHandler(toolRegistry, resourceProvider);

            // Start transport on daemon thread
            Thread transportThread = new Thread(() -> {
                transport.start(handler);
            }, "test-stdio-transport");
            transportThread.setDaemon(true);
            transportThread.start();

            // Give it time to read the input
            Thread.sleep(500);

            // Stop transport
            transport.stop();
            transportThread.join(2000);

            // Verify output
            String output = capturedOut.toString(StandardCharsets.UTF_8.name()).trim();
            assertFalse("Output should not be empty", output.isEmpty());

            String jsonLine = null;
            for (String line : output.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("{")) {
                    jsonLine = trimmed;
                }
            }
            assertNotNull("Should find a JSON response line in output", jsonLine);

            // Parse the response
            JsonNode root = objectMapper.readTree(jsonLine);
            assertNotNull("Response should be valid JSON", root);
            assertTrue("Response should have result", root.has("result"));
            assertFalse("Response should not have error", root.has("error"));

            JsonNode result = root.get("result");
            assertTrue("Should have protocolVersion", result.has("protocolVersion"));
            assertTrue("Should have serverInfo", result.has("serverInfo"));
            assertTrue("Should have capabilities", result.has("capabilities"));

        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }
    }

    // ---- Test 7: initialize handshake returns protocol version, server info, capabilities

    @Test
    public void testInitialize() throws Exception {
        String request = buildRequest("7", "initialize", null);
        String response = protocolHandler.handleMessage(request);

        JsonNode root = objectMapper.readTree(response);
        assertNotNull("Response should not be null", root);
        assertTrue("Response should have result", root.has("result"));
        assertFalse("Response should not have error", root.has("error"));

        JsonNode result = root.get("result");
        assertEquals("Protocol version should be 2024-11-05",
                "2024-11-05", result.get("protocolVersion").asText());

        assertTrue("Should have serverInfo", result.has("serverInfo"));
        JsonNode serverInfo = result.get("serverInfo");
        assertEquals("Server name should match",
                "rocketmq-dashboard-mcp", serverInfo.get("name").asText());
        assertTrue("Should have version", serverInfo.has("version"));

        assertTrue("Should have capabilities", result.has("capabilities"));
        JsonNode capabilities = result.get("capabilities");
        assertTrue("Should have tools capability", capabilities.has("tools"));
        assertTrue("Should have resources capability", capabilities.has("resources"));
    }

    // ---- Additional tests ----------------------------------------------------------

    @Test
    public void testResourcesList() throws Exception {
        String request = buildRequest("8", "resources/list", null);
        String response = protocolHandler.handleMessage(request);

        JsonNode root = objectMapper.readTree(response);
        assertTrue("Response should have result", root.has("result"));

        JsonNode result = root.get("result");
        assertTrue("Result should be array", result.isArray());
        assertEquals("Should have 3 resources", 3, result.size());
    }

    @Test
    public void testResourcesRead() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("uri", "rmq://topics");

        String request = buildRequest("9", "resources/read", params);
        String response = protocolHandler.handleMessage(request);

        JsonNode root = objectMapper.readTree(response);
        assertTrue("Response should have result", root.has("result"));

        JsonNode result = root.get("result");
        assertEquals("URI should match", "rmq://topics", result.get("uri").asText());
        assertTrue("Should have data", result.has("data"));
    }

    @Test
    public void testUnknownMethod() throws Exception {
        String request = buildRequest("10", "unknown/method", null);
        String response = protocolHandler.handleMessage(request);

        JsonNode root = objectMapper.readTree(response);
        assertTrue("Response should have error", root.has("error"));

        JsonNode error = root.get("error");
        assertEquals("Error code should be -32601", -32601, error.get("code").asInt());
    }

    @Test
    public void testL3WithDangerousOpsEnabled() throws Exception {
        // Create handler with L3 enabled
        SecurityGate permissiveGate = new SecurityGate(true);
        McpToolRegistry permissiveRegistry = new McpToolRegistry(permissiveGate);
        McpProtocolHandler permissiveHandler = new McpProtocolHandler(
                permissiveRegistry, resourceProvider);

        Map<String, Object> params = new HashMap<>();
        params.put("name", "rmq.topic.delete");

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("cluster", "test-cluster");
        arguments.put("topic", "old-topic");
        params.put("arguments", arguments);

        String request = buildRequest("11", "tools/call", params);
        String response = permissiveHandler.handleMessage(request);

        JsonNode root = objectMapper.readTree(response);
        assertTrue("Response should have result", root.has("result"));

        JsonNode result = root.get("result");
        // With L3 enabled, it should be dry_run, not blocked
        assertEquals("Status should be dry_run when L3 enabled",
                "dry_run", result.get("status").asText());
    }

    @Test
    public void testToolNotFound() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "rmq.nonexistent.tool");
        params.put("arguments", new HashMap<>());

        String request = buildRequest("12", "tools/call", params);
        String response = protocolHandler.handleMessage(request);

        JsonNode root = objectMapper.readTree(response);
        assertTrue("Response should have result", root.has("result"));

        JsonNode result = root.get("result");
        assertEquals("Status should be error", "error", result.get("status").asText());
        assertTrue("Message should contain 'not found'",
                result.get("message").asText().contains("not found"));
    }

    @Test
    public void testSecurityGateL1Enabled() {
        // L1 should be allowed by default
        SecurityGate gate = new SecurityGate();
        assertTrue("L1 should be allowed by default", gate.isAllowL1());
        assertTrue("L2 should be allowed by default", gate.isAllowL2());
        assertFalse("L3 should be blocked by default", gate.isAllowL3());
    }

    @Test
    public void testSecurityGateL3ExplicitlyEnabled() {
        SecurityGate gate = new SecurityGate(true);
        assertTrue("L3 should be allowed when explicitly enabled", gate.isAllowL3());
    }

    // ---- Helpers -------------------------------------------------------------------

    private String buildRequest(String id, String method, Map<String, Object> params) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("id", id);
            request.put("method", method);
            if (params != null) {
                request.put("params", params);
            }
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build request", e);
        }
    }
}
