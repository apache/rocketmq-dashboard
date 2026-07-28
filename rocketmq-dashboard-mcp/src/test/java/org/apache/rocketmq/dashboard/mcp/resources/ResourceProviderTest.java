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
package org.apache.rocketmq.dashboard.mcp.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.rocketmq.dashboard.cli.executor.ToolExecutor;
import org.apache.rocketmq.dashboard.cli.schema.ToolDefinition;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ResourceProviderTest {

    private ResourceProvider resourceProvider;
    private ObjectMapper objectMapper;
    private List<String> executedTools;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper();
        executedTools = new ArrayList<>();
        // Stub executor: canned data keyed by tool, no cluster connection needed
        resourceProvider = new ResourceProvider(new ToolExecutor() {
            @Override
            public Object execute(ToolDefinition tool, Map<String, Object> arguments) {
                executedTools.add(tool.getName());
                List<Map<String, Object>> list = new ArrayList<>();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", tool.getResource() + "-live-1");
                list.add(item);
                return list;
            }
        });
    }

    // ---- Resources list tests --------------------------------------------------

    @Test
    public void testResourcesListNotEmpty() throws Exception {
        String result = resourceProvider.handleResourcesList();
        assertNotNull("Resources list result should not be null", result);

        JsonNode resources = objectMapper.readTree(result);
        assertTrue("Resources list should be an array", resources.isArray());
        assertTrue("Should have at least 1 resource", resources.size() >= 1);
    }

    @Test
    public void testResourcesListContainsTopics() throws Exception {
        String result = resourceProvider.handleResourcesList();
        assertTrue("Should contain rmq://topics", result.contains("rmq://topics"));
    }

    @Test
    public void testResourcesListContainsGroups() throws Exception {
        String result = resourceProvider.handleResourcesList();
        assertTrue("Should contain rmq://groups", result.contains("rmq://groups"));
    }

    @Test
    public void testResourcesListContainsClients() throws Exception {
        String result = resourceProvider.handleResourcesList();
        assertTrue("Should contain rmq://clients", result.contains("rmq://clients"));
    }

    @Test
    public void testResourcesListHasExactlyThreeResources() throws Exception {
        String result = resourceProvider.handleResourcesList();
        JsonNode resources = objectMapper.readTree(result);
        assertEquals("Should have exactly 3 resources", 3, resources.size());
    }

    @Test
    public void testResourcesListHasRequiredFields() throws Exception {
        String result = resourceProvider.handleResourcesList();
        JsonNode resources = objectMapper.readTree(result);
        JsonNode first = resources.get(0);
        assertTrue("Resource should have uri", first.has("uri"));
        assertTrue("Resource should have name", first.has("name"));
        assertTrue("Resource should have description", first.has("description"));
        assertTrue("Resource should have mimeType", first.has("mimeType"));
        assertEquals("mimeType should be application/json",
                "application/json", first.get("mimeType").asText());
    }

    // ---- Resources read tests (live execution through ToolExecutor) -------------

    @Test
    public void testResourcesReadTopicExecutesTopicListTool() throws Exception {
        String result = resourceProvider.handleResourcesRead("rmq://topics");
        assertNotNull("Topic resource read should not be null", result);

        JsonNode root = objectMapper.readTree(result);
        assertEquals("URI should match", "rmq://topics", root.get("uri").asText());
        assertEquals("mimeType should match", "application/json", root.get("mimeType").asText());
        assertTrue("Should be flagged as live data", root.get("live").asBoolean());
        assertTrue("Should have data", root.has("data"));
        assertTrue("Data should be an array", root.get("data").isArray());
        assertEquals("Data should come from the stub executor",
                "topic-live-1", root.get("data").get(0).get("name").asText());
        assertEquals("Should execute rmq.topic.list", List.of("rmq.topic.list"), executedTools);
    }

    @Test
    public void testResourcesReadGroupExecutesGroupListTool() throws Exception {
        String result = resourceProvider.handleResourcesRead("rmq://groups");
        JsonNode root = objectMapper.readTree(result);
        assertEquals("URI should match", "rmq://groups", root.get("uri").asText());
        assertTrue("Should be flagged as live data", root.get("live").asBoolean());
        assertEquals("Data should come from the stub executor",
                "group-live-1", root.get("data").get(0).get("name").asText());
        assertEquals("Should execute rmq.group.list", List.of("rmq.group.list"), executedTools);
    }

    @Test
    public void testResourcesReadClientExecutesClientListTool() throws Exception {
        String result = resourceProvider.handleResourcesRead("rmq://clients");
        JsonNode root = objectMapper.readTree(result);
        assertEquals("URI should match", "rmq://clients", root.get("uri").asText());
        assertTrue("Should be flagged as live data", root.get("live").asBoolean());
        assertEquals("Data should come from the stub executor",
                "client-live-1", root.get("data").get(0).get("name").asText());
        assertEquals("Should execute rmq.client.list", List.of("rmq.client.list"), executedTools);
    }

    @Test
    public void testResourcesReadNullDataFallsBackToEmptyList() throws Exception {
        ResourceProvider nullDataProvider = new ResourceProvider(new ToolExecutor() {
            @Override
            public Object execute(ToolDefinition tool, Map<String, Object> arguments) {
                return null;
            }
        });
        String result = nullDataProvider.handleResourcesRead("rmq://topics");
        JsonNode root = objectMapper.readTree(result);
        assertTrue("Data should be an array", root.get("data").isArray());
        assertEquals("Data should be empty", 0, root.get("data").size());
    }

    @Test
    public void testResourcesReadExecutionFailureReturnsError() throws Exception {
        // Execution failures must surface as explicit errors, never mock data
        ResourceProvider failingProvider = new ResourceProvider(new ToolExecutor() {
            @Override
            public Object execute(ToolDefinition tool, Map<String, Object> arguments)
                    throws Exception {
                throw new IllegalStateException("no cluster reachable");
            }
        });
        String result = failingProvider.handleResourcesRead("rmq://topics");
        JsonNode root = objectMapper.readTree(result);
        assertTrue("Should contain error", root.has("error"));
        assertEquals("Should be an execution error",
                "EXECUTION_ERROR", root.get("errorType").asText());
        assertTrue("Error should carry the cause message",
                root.get("error").asText().contains("no cluster reachable"));
    }

    @Test
    public void testResourcesReadUnknownUri() throws Exception {
        String result = resourceProvider.handleResourcesRead("rmq://unknown-resource");
        assertNotNull("Unknown resource read should not be null", result);
        assertTrue("Should contain error for unknown URI",
                result.contains("error") || result.contains("Unknown"));
        assertTrue("Unknown URI must not trigger any execution", executedTools.isEmpty());
    }

    @Test
    public void testResourcesReadNullUri() throws Exception {
        String result = resourceProvider.handleResourcesRead(null);
        assertNotNull("Null URI should return error, not null", result);
        assertTrue("Should contain error for null URI",
                result.contains("error") || result.contains("required"));
    }

    @Test
    public void testResourcesReadEmptyUri() throws Exception {
        String result = resourceProvider.handleResourcesRead("");
        assertNotNull("Empty URI should not be null", result);
        assertTrue("Should contain error for empty/unknown URI",
                result.contains("error") || result.contains("Unknown"));
    }
}
