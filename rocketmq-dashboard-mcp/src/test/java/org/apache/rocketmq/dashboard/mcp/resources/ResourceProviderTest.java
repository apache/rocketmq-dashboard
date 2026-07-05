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
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ResourceProviderTest {

    private ResourceProvider resourceProvider;
    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        resourceProvider = new ResourceProvider();
        objectMapper = new ObjectMapper();
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

    // ---- Resources read tests --------------------------------------------------

    @Test
    public void testResourcesReadTopic() throws Exception {
        String result = resourceProvider.handleResourcesRead("rmq://topics");
        assertNotNull("Topic resource read should not be null", result);

        JsonNode root = objectMapper.readTree(result);
        assertEquals("URI should match", "rmq://topics", root.get("uri").asText());
        assertEquals("mimeType should match", "application/json", root.get("mimeType").asText());
        assertTrue("Should have data", root.has("data"));
        assertTrue("Data should be an array", root.get("data").isArray());
        assertTrue("Should have at least 1 topic", root.get("data").size() >= 1);

        JsonNode firstTopic = root.get("data").get(0);
        assertTrue("Topic should have name", firstTopic.has("name"));
        assertTrue("Topic should have type", firstTopic.has("type"));
        assertTrue("Topic should have status", firstTopic.has("status"));
        assertEquals("Status should be ACTIVE", "ACTIVE", firstTopic.get("status").asText());
    }

    @Test
    public void testResourcesReadGroup() throws Exception {
        String result = resourceProvider.handleResourcesRead("rmq://groups");
        assertNotNull("Group resource read should not be null", result);

        JsonNode root = objectMapper.readTree(result);
        assertEquals("URI should match", "rmq://groups", root.get("uri").asText());
        assertTrue("Should have data", root.has("data"));

        JsonNode data = root.get("data");
        assertTrue("Group data should be array", data.isArray());
        assertTrue("Should have at least 1 group", data.size() >= 1);

        JsonNode firstGroup = data.get(0);
        assertTrue("Group should have name", firstGroup.has("name"));
        assertTrue("Group should have consumeMode", firstGroup.has("consumeMode"));
    }

    @Test
    public void testResourcesReadClient() throws Exception {
        String result = resourceProvider.handleResourcesRead("rmq://clients");
        assertNotNull("Client resource read should not be null", result);

        JsonNode root = objectMapper.readTree(result);
        assertEquals("URI should match", "rmq://clients", root.get("uri").asText());
        assertTrue("Should have data", root.has("data"));

        JsonNode data = root.get("data");
        assertTrue("Client data should be array", data.isArray());
        assertTrue("Should have at least 1 client", data.size() >= 1);

        JsonNode firstClient = data.get(0);
        assertTrue("Client should have clientId", firstClient.has("clientId"));
        assertTrue("Client should have type", firstClient.has("type"));
        assertTrue("Client should have version", firstClient.has("version"));
    }

    @Test
    public void testResourcesReadUnknownUri() throws Exception {
        String result = resourceProvider.handleResourcesRead("rmq://unknown-resource");
        assertNotNull("Unknown resource read should not be null", result);
        assertTrue("Should contain error for unknown URI",
                result.contains("error") || result.contains("Unknown"));
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

    @Test
    public void testTopicsDataHasFiveTopics() throws Exception {
        String result = resourceProvider.handleResourcesRead("rmq://topics");
        JsonNode root = objectMapper.readTree(result);
        assertEquals("Should have exactly 5 topics", 5, root.get("data").size());
    }

    @Test
    public void testGroupsDataHasFourGroups() throws Exception {
        String result = resourceProvider.handleResourcesRead("rmq://groups");
        JsonNode root = objectMapper.readTree(result);
        assertEquals("Should have exactly 4 groups", 4, root.get("data").size());
    }

    @Test
    public void testClientsDataHasSixClients() throws Exception {
        String result = resourceProvider.handleResourcesRead("rmq://clients");
        JsonNode root = objectMapper.readTree(result);
        assertEquals("Should have exactly 6 clients", 6, root.get("data").size());
    }

    @Test
    public void testTopicDataContainsExpectedFields() throws Exception {
        String result = resourceProvider.handleResourcesRead("rmq://topics");
        JsonNode root = objectMapper.readTree(result);

        JsonNode firstTopic = root.get("data").get(0);
        assertTrue("Should have queueNums", firstTopic.has("queueNums"));
        assertEquals("queueNums should be 8", 8, firstTopic.get("queueNums").asInt());
        assertTrue("Should have perm", firstTopic.has("perm"));
        assertEquals("perm should be 6", 6, firstTopic.get("perm").asInt());
        assertTrue("Should have writeQueueNums", firstTopic.has("writeQueueNums"));
        assertTrue("Should have readQueueNums", firstTopic.has("readQueueNums"));
    }
}
