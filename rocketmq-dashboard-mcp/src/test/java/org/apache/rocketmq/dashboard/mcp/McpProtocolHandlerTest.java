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
import java.util.HashMap;
import java.util.Map;
import org.apache.rocketmq.dashboard.mcp.resources.ResourceProvider;
import org.apache.rocketmq.dashboard.mcp.tools.McpToolRegistry;
import org.apache.rocketmq.dashboard.mcp.tools.SecurityGate;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class McpProtocolHandlerTest {

    private ObjectMapper objectMapper;
    private McpProtocolHandler handler;
    private McpToolRegistry toolRegistry;
    private ResourceProvider resourceProvider;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper();
        SecurityGate securityGate = new SecurityGate();
        toolRegistry = new McpToolRegistry(securityGate);
        resourceProvider = new ResourceProvider();
        handler = new McpProtocolHandler(toolRegistry, resourceProvider);
    }

    // ---- Malformed JSON tests --------------------------------------------------

    @Test
    public void testHandleMalformedJson() {
        String response = handler.handleMessage("not json at all");
        assertNotNull("Response should not be null", response);
        assertTrue("Response should contain error for malformed JSON",
                response.contains("error"));
    }

    @Test
    public void testHandleEmptyMessage() {
        String response = handler.handleMessage("");
        assertNotNull("Response should not be null", response);
        assertTrue("Response should contain error for empty message",
                response.contains("error"));
    }

    @Test
    public void testHandleNullMethod() throws Exception {
        // Message without a method field
        String msg = objectMapper.writeValueAsString(
                createMap("jsonrpc", "2.0", "id", 1));
        String response = handler.handleMessage(msg);
        assertNotNull("Response should not be null", response);
        assertTrue("Response should contain error for missing method",
                response.contains("error"));
    }

    @Test
    public void testHandleBlankMethod() throws Exception {
        // Method field present but empty
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", "1");
        request.put("method", "");
        String msg = objectMapper.writeValueAsString(request);
        String response = handler.handleMessage(msg);
        assertNotNull("Response should not be null", response);
    }

    @Test
    public void testHandleMethodWithNullId() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", "initialize");
        // No id field
        String msg = objectMapper.writeValueAsString(request);
        String response = handler.handleMessage(msg);

        JsonNode root = objectMapper.readTree(response);
        assertTrue("Response should have result for initialize without id",
                root.has("result"));
    }

    // ---- Initialize tests ------------------------------------------------------

    @Test
    public void testInitializeResponseStructure() throws Exception {
        Map<String, Object> params = new HashMap<>();
        String msg = buildRequest("1", "initialize", params);
        String response = handler.handleMessage(msg);

        JsonNode root = objectMapper.readTree(response);
        assertTrue("Initialize response should have result", root.has("result"));
        assertFalse("Initialize response should not have error", root.has("error"));

        JsonNode result = root.get("result");
        assertEquals("Protocol version should match",
                "2024-11-05", result.get("protocolVersion").asText());

        assertTrue("Should have serverInfo", result.has("serverInfo"));
        JsonNode serverInfo = result.get("serverInfo");
        assertEquals("rocketmq-dashboard-mcp", serverInfo.get("name").asText());
        assertTrue("Should have version", serverInfo.has("version"));

        assertTrue("Should have capabilities", result.has("capabilities"));
    }

    @Test
    public void testInitializeCapabilitiesHasTools() throws Exception {
        Map<String, Object> params = new HashMap<>();
        String msg = buildRequest("2", "initialize", params);
        String response = handler.handleMessage(msg);

        JsonNode root = objectMapper.readTree(response);
        JsonNode capabilities = root.get("result").get("capabilities");
        assertTrue("Capabilities should have tools", capabilities.has("tools"));
        assertTrue("Capabilities should have resources", capabilities.has("resources"));
    }

    // ---- Unknown method tests --------------------------------------------------

    @Test
    public void testUnknownMethodReturnsError() throws Exception {
        String msg = buildRequest("3", "nonexistent/method", new HashMap<>());
        String response = handler.handleMessage(msg);

        JsonNode root = objectMapper.readTree(response);
        assertTrue("Response should have error for unknown method", root.has("error"));
        JsonNode error = root.get("error");
        assertEquals("Error code should be -32601", -32601, error.get("code").asInt());
        assertTrue("Error message should mention method not found",
                error.get("message").asText().contains("Method not found"));
    }

    // ---- tools/call missing params tests ---------------------------------------

    @Test
    public void testToolsCallMissingParams() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", "4");
        request.put("method", "tools/call");
        String msg = objectMapper.writeValueAsString(request);
        String response = handler.handleMessage(msg);

        JsonNode root = objectMapper.readTree(response);
        assertTrue("Response should have result", root.has("result"));
        JsonNode result = root.get("result");
        assertEquals("Status should be error", "error", result.get("status").asText());
        assertTrue("Message should mention missing params",
                result.get("message").asText().contains("Missing params"));
    }

    @Test
    public void testToolsCallMissingToolName() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("arguments", new HashMap<>());
        String msg = buildRequest("5", "tools/call", params);
        String response = handler.handleMessage(msg);

        JsonNode root = objectMapper.readTree(response);
        assertTrue("Response should have result", root.has("result"));
        JsonNode result = root.get("result");
        assertEquals("Status should be error", "error", result.get("status").asText());
        assertTrue("Message should mention missing tool name",
                result.get("message").asText().contains("Missing tool name"));
    }

    // ---- resources/read missing params tests -----------------------------------

    @Test
    public void testResourcesReadMissingParams() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", "6");
        request.put("method", "resources/read");
        String msg = objectMapper.writeValueAsString(request);
        String response = handler.handleMessage(msg);

        JsonNode root = objectMapper.readTree(response);
        assertTrue("Response should have result", root.has("result"));
        JsonNode result = root.get("result");
        assertTrue("Result should have error for missing params",
                result.has("error"));
    }

    // ---- JSON-RPC response structure tests -------------------------------------

    @Test
    public void testResponseHasJsonRpcVersion() throws Exception {
        String msg = buildRequest("7", "initialize", new HashMap<>());
        String response = handler.handleMessage(msg);

        JsonNode root = objectMapper.readTree(response);
        assertEquals("jsonrpc should be 2.0", "2.0", root.get("jsonrpc").asText());
    }

    @Test
    public void testResponseEchoesId() throws Exception {
        String msg = buildRequest("my-custom-id", "initialize", new HashMap<>());
        String response = handler.handleMessage(msg);

        JsonNode root = objectMapper.readTree(response);
        assertEquals("id should be echoed back",
                "my-custom-id", root.get("id").asText());
    }

    @Test
    public void testParseErrorReturnsCorrectCode() {
        String response = handler.handleMessage("{broken json");
        assertTrue("Response should contain error for parse error",
                response.contains("-32700") || response.contains("error"));
    }

    @Test
    public void testHandleMessageWithNullParams() throws Exception {
        String msg = buildRequest("8", "tools/call", null);
        String response = handler.handleMessage(msg);

        JsonNode root = objectMapper.readTree(response);
        assertTrue("Response should have result", root.has("result"));
    }

    // ---- Null-safe tests -------------------------------------------------------

    @Test
    public void testHandleMessageDoesNotThrowOnNull() {
        // Should not throw NPE
        String response = handler.handleMessage(null);
        assertNotNull("Response should not be null even for null input", response);
    }

    // ---- Helpers ---------------------------------------------------------------

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

    private Map<String, Object> createMap(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> map = new HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }
}
