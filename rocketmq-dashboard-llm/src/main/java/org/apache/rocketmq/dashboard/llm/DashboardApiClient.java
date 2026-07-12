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
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * HTTP client that calls the main RocketMQ Dashboard REST API
 * to execute tool commands against a live RocketMQ cluster.
 */
@Service
public class DashboardApiClient {

    private static final Logger log = LoggerFactory.getLogger(DashboardApiClient.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** Default dashboard API base URL (same host as main app). */
    private String dashboardBaseUrl = "http://localhost:8082";

    private HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public void setDashboardBaseUrl(String url) {
        this.dashboardBaseUrl = url != null ? url.replaceAll("/$", "") : "http://localhost:8082";
    }

    // -----------------------------------------------------------------------
    // Public API called by executeTool
    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public Map<String, Object> clusterList(Map<String, Object> args) {
        // Return simplified cluster info: cluster names and broker counts
        Map<String, Object> raw = get("/cluster/list.query");
        Map<String, Object> data = (Map<String, Object>) raw.get("data");
        if (data == null) return raw;

        Map<String, Object> clusterInfo = (Map<String, Object>) data.get("clusterInfo");
        List<Map<String, Object>> clusters = new ArrayList<>();
        if (clusterInfo != null) {
            Map<String, Object> clusterAddrTable =
                    (Map<String, Object>) clusterInfo.get("clusterAddrTable");
            Map<String, Object> brokerAddrTable =
                    (Map<String, Object>) clusterInfo.get("brokerAddrTable");
            if (clusterAddrTable != null) {
                for (Map.Entry<String, Object> entry : clusterAddrTable.entrySet()) {
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("name", entry.getKey());
                    List<String> brokerNames = (List<String>) entry.getValue();
                    c.put("brokerNames", brokerNames != null ? brokerNames : List.of());
                    c.put("brokerCount", brokerNames != null ? brokerNames.size() : 0);
                    if (brokerAddrTable != null && brokerNames != null) {
                        for (String bn : brokerNames) {
                            Map<String, Object> ba = (Map<String, Object>) brokerAddrTable.get(bn);
                            if (ba != null) {
                                c.put("selectedBroker", ba);
                                break;
                            }
                        }
                    }
                    clusters.add(c);
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", 0);
        result.put("data", clusters);
        return result;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> clusterDescribe(Map<String, Object> args) {
        Map<String, Object> raw = get("/cluster/list.query");
        Map<String, Object> data = (Map<String, Object>) raw.get("data");
        if (data == null) return raw;

        Map<String, Object> clusterInfo = (Map<String, Object>) data.get("clusterInfo");
        if (clusterInfo != null) {
            Map<String, Object> desc = new LinkedHashMap<>();
            Map<String, Object> clusterAddrTable =
                    (Map<String, Object>) clusterInfo.get("clusterAddrTable");
            Map<String, Object> brokerAddrTable =
                    (Map<String, Object>) clusterInfo.get("brokerAddrTable");
            desc.put("clusters", clusterAddrTable != null ? clusterAddrTable : new LinkedHashMap<>());
            // Extract simplified broker info
            List<Map<String, Object>> brokers = new ArrayList<>();
            if (brokerAddrTable != null) {
                for (Map.Entry<String, Object> entry : brokerAddrTable.entrySet()) {
                    Map<String, Object> ba = (Map<String, Object>) entry.getValue();
                    Map<String, Object> b = new LinkedHashMap<>();
                    b.put("name", entry.getKey());
                    b.put("cluster", ba != null ? ba.get("cluster") : "");
                    b.put("addrs", ba != null ? ba.get("brokerAddrs") : "");
                    brokers.add(b);
                }
            }
            desc.put("brokers", brokers);
            // Include broker server stats (trimmed)
            Map<String, Object> brokerServer = (Map<String, Object>) data.get("brokerServer");
            if (brokerServer != null) {
                // Keep only top-level broker names, strip detailed metrics
                Map<String, Object> summary = new LinkedHashMap<>();
                for (String key : brokerServer.keySet()) {
                    summary.put(key, "online");
                }
                desc.put("brokerStatus", summary);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", 0);
            result.put("data", desc);
            return result;
        }
        return raw;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> topicList(Map<String, Object> args) {
        return get("/topic/list.query");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> topicDescribe(Map<String, Object> args) {
        String topic = (String) args.get("topic");
        if (topic == null) {
            return error("Missing required parameter: topic");
        }
        return get("/topic/route.query?topic=" + encode(topic));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> groupList(Map<String, Object> args) {
        return get("/consumer/groupList.query");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> groupDescribe(Map<String, Object> args) {
        String group = (String) args.get("group");
        if (group == null) {
            return error("Missing required parameter: group");
        }
        return get("/consumer/group.query?consumerGroup=" + encode(group));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> brokerList(Map<String, Object> args) {
        // Extract brokers from cluster list response's clusterInfo.brokerAddrTable
        Map<String, Object> raw = get("/cluster/list.query");
        Map<String, Object> data = (Map<String, Object>) raw.get("data");
        List<Map<String, Object>> brokers = new ArrayList<>();

        if (data != null) {
            Map<String, Object> clusterInfo = (Map<String, Object>) data.get("clusterInfo");
            if (clusterInfo != null) {
                Map<String, Object> brokerAddrTable =
                        (Map<String, Object>) clusterInfo.get("brokerAddrTable");
                Map<String, Object> clusterAddrTable =
                        (Map<String, Object>) clusterInfo.get("clusterAddrTable");

                if (brokerAddrTable != null) {
                    for (Map.Entry<String, Object> entry : brokerAddrTable.entrySet()) {
                        Map<String, Object> ba = (Map<String, Object>) entry.getValue();
                        Map<String, Object> b = new LinkedHashMap<>();
                        b.put("name", entry.getKey());
                        b.put("cluster", ba != null ? ba.get("cluster") : "");
                        b.put("addrs", ba != null ? ba.get("brokerAddrs") : "");
                        b.put("version", "V5_5_0");

                        // Find which cluster this broker belongs to
                        if (clusterAddrTable != null) {
                            for (Map.Entry<String, Object> ce : clusterAddrTable.entrySet()) {
                                List<String> names = (List<String>) ce.getValue();
                                if (names != null && names.contains(entry.getKey())) {
                                    b.put("cluster", ce.getKey());
                                    break;
                                }
                            }
                        }
                        brokers.add(b);
                    }
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", 0);
        result.put("data", brokers);
        return result;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> brokerDescribe(Map<String, Object> args) {
        String brokerName = (String) args.get("brokerName");
        Map<String, Object> raw = get("/cluster/list.query");
        Map<String, Object> data = (Map<String, Object>) raw.get("data");
        if (data != null) {
            Map<String, Object> clusterInfo = (Map<String, Object>) data.get("clusterInfo");
            if (clusterInfo != null) {
                Map<String, Object> brokerAddrTable =
                        (Map<String, Object>) clusterInfo.get("brokerAddrTable");
                if (brokerAddrTable != null && brokerAddrTable.containsKey(brokerName)) {
                    Map<String, Object> ba = (Map<String, Object>) brokerAddrTable.get(brokerName);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("status", 0);
                    result.put("data", ba);
                    return result;
                }
            }
        }
        return error("Broker not found: " + brokerName);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> messageQueryById(Map<String, Object> args) {
        String msgId = (String) args.get("msgId");
        String topic = (String) args.get("topic");
        if (msgId == null || topic == null) {
            return error("Missing required parameters: msgId and topic");
        }
        return get("/message/viewMessage.query?msgId=" + encode(msgId)
                + "&topic=" + encode(topic));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> messageQueryByTime(Map<String, Object> args) {
        String topic = (String) args.get("topic");
        Object beginTime = args.get("beginTime");
        Object endTime = args.get("endTime");
        if (topic == null || beginTime == null || endTime == null) {
            return error("Missing required parameters: topic, beginTime, endTime");
        }
        return get("/message/queryMessageByTopic.query?topic=" + encode(topic)
                + "&begin=" + beginTime + "&end=" + endTime);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> namespaceList(Map<String, Object> args) {
        return get("/namespace/list.query");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> clientList(Map<String, Object> args) {
        return get("/client/list.query");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> aclList(Map<String, Object> args) {
        return get("/acl/list.query");
    }

    // -----------------------------------------------------------------------
    // Internal HTTP helpers
    // -----------------------------------------------------------------------

    private Map<String, Object> get(String path) {
        try {
            String url = dashboardBaseUrl + path;
            log.debug("Dashboard API GET: {}", url);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return objectMapper.readValue(resp.body(), Map.class);
            } else {
                log.warn("Dashboard API returned {} for {}", resp.statusCode(), path);
                return error("Dashboard API error: HTTP " + resp.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            log.error("Dashboard API call failed for {}: {}", path, e.getMessage());
            return error("Failed to reach RocketMQ Dashboard: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractDataList(Map<String, Object> response) {
        if (response == null) {
            return List.of();
        }
        // Dashboard API returns {"status":0,"data":[...]} or {"status":0,"data":{"xxx":[...]}}
        Object data = response.get("data");
        if (data instanceof List) {
            return (List<Map<String, Object>>) data;
        }
        if (data instanceof Map) {
            // Data might be wrapped, try to find a list inside
            Map<String, Object> dataMap = (Map<String, Object>) data;
            for (Object value : dataMap.values()) {
                if (value instanceof List) {
                    return (List<Map<String, Object>>) value;
                }
            }
        }
        return List.of();
    }

    private Map<String, Object> error(String msg) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("status", -1);
        err.put("errMsg", msg);
        return err;
    }

    private String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
