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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides MCP resource endpoints for listing and reading RocketMQ resources.
 * Resources expose cluster topology data as structured URIs.
 */
public class ResourceProvider {

    private static final Logger log = LoggerFactory.getLogger(ResourceProvider.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Return the MCP resources/list response as a JSON array.
     * Exposes three resource URIs: topics, groups, clients.
     *
     * @return JSON string for the resources/list response
     */
    public String handleResourcesList() {
        List<Map<String, Object>> resources = new ArrayList<>();

        Map<String, Object> topics = new LinkedHashMap<>();
        topics.put("uri", "rmq://topics");
        topics.put("name", "RocketMQ Topics");
        topics.put("description", "List of all topics in the connected RocketMQ cluster");
        topics.put("mimeType", "application/json");
        resources.add(topics);

        Map<String, Object> groups = new LinkedHashMap<>();
        groups.put("uri", "rmq://groups");
        groups.put("name", "RocketMQ Consumer Groups");
        groups.put("description", "List of all consumer groups in the connected RocketMQ cluster");
        groups.put("mimeType", "application/json");
        resources.add(groups);

        Map<String, Object> clients = new LinkedHashMap<>();
        clients.put("uri", "rmq://clients");
        clients.put("name", "RocketMQ Clients");
        clients.put("description", "List of all connected clients in the RocketMQ cluster");
        clients.put("mimeType", "application/json");
        resources.add(clients);

        try {
            return objectMapper.writeValueAsString(resources);
        } catch (JsonProcessingException e) {
            log.error("Error serializing resources list: {}", e.getMessage(), e);
            return "[]";
        }
    }

    /**
     * Read a specific resource by URI. Returns mock snapshot data.
     *
     * @param uri the resource URI (e.g. "rmq://topics")
     * @return JSON string with the resource content
     */
    public String handleResourcesRead(String uri) {
        if (uri == null) {
            return buildError("Resource URI is required");
        }

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("uri", uri);

        switch (uri) {
            case "rmq://topics":
                content.put("mimeType", "application/json");
                content.put("data", generateTopicsData());
                break;
            case "rmq://groups":
                content.put("mimeType", "application/json");
                content.put("data", generateGroupsData());
                break;
            case "rmq://clients":
                content.put("mimeType", "application/json");
                content.put("data", generateClientsData());
                break;
            default:
                return buildError("Unknown resource URI: " + uri);
        }

        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            log.error("Error serializing resource content: {}", e.getMessage(), e);
            return "{}";
        }
    }

    private String buildError(String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("error", message);
        try {
            return objectMapper.writeValueAsString(error);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"" + message + "\"}";
        }
    }

    // ---- Mock data generators ------------------------------------------------

    private List<Map<String, Object>> generateTopicsData() {
        List<Map<String, Object>> topics = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> topic = new LinkedHashMap<>();
            topic.put("name", "Topic-" + i);
            topic.put("type", i == 3 ? "FIFO" : "NORMAL");
            topic.put("status", "ACTIVE");
            topic.put("queueNums", 8);
            topic.put("perm", 6);
            topic.put("writeQueueNums", 8);
            topic.put("readQueueNums", 8);
            topics.add(topic);
        }
        return topics;
    }

    private List<Map<String, Object>> generateGroupsData() {
        List<Map<String, Object>> groups = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("name", "ConsumerGroup-" + i);
            group.put("status", "ACTIVE");
            group.put("consumeMode", i == 4 ? "BROADCAST" : "CLUSTER");
            group.put("consumeEnable", true);
            group.put("retryMaxTimes", 16);
            groups.add(group);
        }
        return groups;
    }

    private List<Map<String, Object>> generateClientsData() {
        List<Map<String, Object>> clients = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            Map<String, Object> client = new LinkedHashMap<>();
            client.put("clientId", "192.168.1." + (10 + i) + "@consumer-" + i);
            client.put("type", i <= 3 ? "CONSUMER" : "PRODUCER");
            client.put("version", "5.5.0");
            client.put("language", "JAVA");
            client.put("status", "CONNECTED");
            client.put("lastHeartbeat", System.currentTimeMillis() - 60_000);
            clients.add(client);
        }
        return clients;
    }
}
