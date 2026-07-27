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
package org.apache.rocketmq.dashboard.service.metrics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.dashboard.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prometheus / OpenMetrics query client for RocketMQ Dashboard.
 *
 * <p>Connects to a Prometheus-compatible server (Prometheus, VictoriaMetrics,
 * Thanos, Mimir, Cortex) and queries RocketMQ metrics using PromQL.
 * Parses the JSON response from the Prometheus HTTP API into structured maps.</p>
 *
 * <h3>Supported RocketMQ Exporter Metrics</h3>
 * <ul>
 *   <li>{@code rocketmq_broker_tps} / {@code rocketmq_broker_qps}</li>
 *   <li>{@code rocketmq_broker_sendTPS} / {@code rocketmq_broker_bornTPS}</li>
 *   <li>{@code rocketmq_consumer_offset} / {@code rocketmq_group_diff}</li>
 *   <li>{@code rocketmq_topic_putNumsTotal} / {@code rocketmq_topic_putSizeTotal}</li>
 *   <li>{@code rocketmq_broker_storePathUseRatio}</li>
 *   <li>{@code rocketmq_group_cqPullLatency_max}</li>
 *   <li>{@code rocketmq_remoting_latency_bucket}</li>
 *   <li>{@code rocketmq_proxy_requestTotal}</li>
 *   <li>{@code rocketmq_client_connected}</li>
 * </ul>
 *
 * <h3>Response Parsing</h3>
 * <p>Parses the standard Prometheus JSON response format:
 * <pre>
 * {
 *   "status": "success",
 *   "data": {
 *     "resultType": "vector" | "matrix" | "scalar" | "string",
 *     "result": [...]
 *   }
 * }
 * </pre>
 * </p>
 */
@Component
public class PrometheusMetricsQueryClient {

    private static final Logger log = LoggerFactory.getLogger(PrometheusMetricsQueryClient.class);

    private static final int CONNECT_TIMEOUT_S = 10;
    private static final int READ_TIMEOUT_S = 30;

    // ==================== Standard RocketMQ PromQL Templates ====================

    /** Cluster-level PromQL queries. */
    private static final Map<String, String> CLUSTER_QUERIES;
    /** Broker-level PromQL queries. */
    private static final Map<String, String> BROKER_QUERIES;
    /** Topic-level PromQL queries. */
    private static final Map<String, String> TOPIC_QUERIES;
    /** Consumer group PromQL queries. */
    private static final Map<String, String> CONSUMER_QUERIES;
    /** System-level PromQL queries. */
    private static final Map<String, String> SYSTEM_QUERIES;

    static {
        // Cluster metrics
        Map<String, String> cq = new LinkedHashMap<>();
        cq.put("totalMessagesSent", "sum(rocketmq_broker_sendTPS)");
        cq.put("totalMessagesBorn", "sum(rocketmq_broker_bornTPS)");
        cq.put("clusterTPS", "sum(rate(rocketmq_broker_sendTPS[5m]))");
        cq.put("clusterQPS", "sum(rate(rocketmq_broker_qps[5m]))");
        cq.put("brokerCount", "count(rocketmq_broker_sendTPS)");
        cq.put("topicCount", "count(count by(topic)(rocketmq_topic_putNumsTotal))");
        cq.put("consumerGroupCount", "count(count by(group)(rocketmq_group_diff))");
        cq.put("totalBrokerDiskUsage", "avg(rocketmq_broker_storePathUseRatio)");
        CLUSTER_QUERIES = Collections.unmodifiableMap(cq);

        // Broker metrics
        Map<String, String> bq = new LinkedHashMap<>();
        bq.put("sendTPS", "rocketmq_broker_sendTPS{broker=\"%s\"}");
        bq.put("bornTPS", "rocketmq_broker_bornTPS{broker=\"%s\"}");
        bq.put("sendTotal", "rocketmq_broker_sendTotal{broker=\"%s\"}");
        bq.put("bornTotal", "rocketmq_broker_bornTotal{broker=\"%s\"}");
        bq.put("diskUsageRatio", "rocketmq_broker_storePathUseRatio{broker=\"%s\"}");
        bq.put("writeThreadPoolSize", "rocketmq_broker_sendThreadPoolNums{broker=\"%s\"}");
        bq.put("remotingLatencyP99", "histogram_quantile(0.99, rate(rocketmq_remoting_latency_bucket{broker=\"%s\"}[5m]))");
        bq.put("jvmHeapUsed", "java_lang_memory_HeapMemoryUsage_used{broker=\"%s\"}");
        bq.put("jvmHeapMax", "java_lang_memory_HeapMemoryUsage_max{broker=\"%s\"}");
        bq.put("gcTime", "rate(java_lang_GcTime_milliseconds{broker=\"%s\"}[5m])");
        bq.put("slaveFallBehind", "rocketmq_broker_slaveFallBehindSize{broker=\"%s\"}");
        BROKER_QUERIES = Collections.unmodifiableMap(bq);

        // Topic metrics
        Map<String, String> tq = new LinkedHashMap<>();
        tq.put("putNumsRate", "rate(rocketmq_topic_putNumsTotal{topic=\"%s\"}[5m])");
        tq.put("putSizeRate", "rate(rocketmq_topic_putSizeTotal{topic=\"%s\"}[5m])");
        tq.put("putErrorMsgs", "rate(rocketmq_topic_putErrorMsgsTotal{topic=\"%s\"}[5m])");
        tq.put("putTotal", "rocketmq_topic_putNumsTotal{topic=\"%s\"}");
        tq.put("consumeRate", "sum by(group)(rate(rocketmq_group_consumeTotal{topic=\"%s\"}[5m]))");
        tq.put("accumulation", "sum by(group)(rocketmq_group_diff{topic=\"%s\"})");
        TOPIC_QUERIES = Collections.unmodifiableMap(tq);

        // Consumer group metrics
        Map<String, String> cgq = new LinkedHashMap<>();
        cgq.put("consumeOffset", "rocketmq_consumer_offset{group=\"%s\"}");
        cgq.put("consumeLag", "rocketmq_group_diff{group=\"%s\"}");
        cgq.put("pullLatencyMax", "rocketmq_group_cqPullLatency_max{group=\"%s\"}");
        cgq.put("consumeTPS", "rate(rocketmq_group_consumeTotal{group=\"%s\"}[5m])");
        cgq.put("consumeErrorMsgs", "rate(rocketmq_group_consumeErrorMsgsTotal{group=\"%s\"}[5m])");
        cgq.put("consumeTotal", "rocketmq_group_consumeTotal{group=\"%s\"}");
        cgq.put("rebalanceCount", "rocketmq_group_rebalanceTotal{group=\"%s\"}");
        CONSUMER_QUERIES = Collections.unmodifiableMap(cgq);

        // System metrics
        Map<String, String> sq = new LinkedHashMap<>();
        sq.put("up", "up{job=~\"rocketmq.*\"}");
        sq.put("processCpuSeconds", "rate(process_cpu_seconds_total{job=~\"rocketmq.*\"}[5m])");
        sq.put("jvmMemoryUsed", "java_lang_memory_HeapMemoryUsage_used");
        sq.put("jvmMemoryMax", "java_lang_memory_HeapMemoryUsage_max");
        sq.put("jvmGcPause", "rate(java_lang_GcTime_milliseconds[5m])");
        sq.put("processOpenFds", "process_open_fds{job=~\"rocketmq.*\"}");
        sq.put("processMaxFds", "process_max_fds{job=~\"rocketmq.*\"}");
        SYSTEM_QUERIES = Collections.unmodifiableMap(sq);
    }

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /** Configured Prometheus base URLs keyed by datasource ID. */
    private final Map<String, String> datasourceUrls = new ConcurrentHashMap<>();

    /** Default datasource URL (used when no ID is specified). */
    private volatile String defaultDatasourceUrl;

    public PrometheusMetricsQueryClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_S))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // ==================== Configuration ====================

    /**
     * Register or update a Prometheus datasource URL.
     *
     * @param datasourceId unique identifier for the datasource
     * @param url          base URL (e.g., "http://prometheus:9090")
     * @param isDefault    whether this should be the default datasource
     */
    public void registerDatasource(String datasourceId, String url, boolean isDefault) {
        String normalized = normalizeUrl(url);
        datasourceUrls.put(datasourceId, normalized);
        if (isDefault || defaultDatasourceUrl == null) {
            defaultDatasourceUrl = normalized;
        }
        log.info("Registered Prometheus datasource: id={}, url={}, default={}", datasourceId, normalized, isDefault);
    }

    /**
     * Remove a datasource.
     */
    public void removeDatasource(String datasourceId) {
        datasourceUrls.remove(datasourceId);
        log.info("Removed Prometheus datasource: id={}", datasourceId);
    }

    /**
     * Set the default datasource URL directly (for simple configurations).
     */
    public void setDefaultDatasourceUrl(String url) {
        this.defaultDatasourceUrl = normalizeUrl(url);
    }

    // ==================== Raw Query Methods ====================

    /**
     * Execute a PromQL instant query and return the raw parsed response.
     *
     * @param promQL       the PromQL expression
     * @param datasourceId optional datasource ID (null for default)
     * @return parsed Prometheus response as a Map
     */
    public Map<String, Object> queryInstant(String promQL, String datasourceId) {
        String baseUrl = resolveUrl(datasourceId);
        String url = baseUrl + "/api/v1/query?query=" + encode(promQL);
        return executeQuery(url, "instant query: " + truncate(promQL, 80));
    }

    /**
     * Execute a PromQL instant query with a specific evaluation time.
     *
     * @param promQL       the PromQL expression
     * @param time         evaluation timestamp (Unix timestamp or RFC3339)
     * @param datasourceId optional datasource ID
     * @return parsed Prometheus response
     */
    public Map<String, Object> queryInstant(String promQL, String time, String datasourceId) {
        String baseUrl = resolveUrl(datasourceId);
        StringBuilder urlBuilder = new StringBuilder(baseUrl)
                .append("/api/v1/query?query=").append(encode(promQL));
        if (time != null && !time.isEmpty()) {
            urlBuilder.append("&time=").append(encode(time));
        }
        return executeQuery(urlBuilder.toString(), "instant query: " + truncate(promQL, 80));
    }

    /**
     * Execute a PromQL range query and return the raw parsed response.
     *
     * @param promQL       the PromQL expression
     * @param start        start timestamp
     * @param end          end timestamp
     * @param step         query resolution step (e.g., "15s", "1m")
     * @param datasourceId optional datasource ID
     * @return parsed Prometheus response in matrix format
     */
    public Map<String, Object> queryRange(String promQL, String start, String end, String step, String datasourceId) {
        String baseUrl = resolveUrl(datasourceId);
        String url = baseUrl + "/api/v1/query_range?"
                + "query=" + encode(promQL)
                + "&start=" + encode(start)
                + "&end=" + encode(end)
                + "&step=" + encode(step);
        return executeQuery(url, "range query: " + truncate(promQL, 80));
    }

    // ==================== OpenMetrics Response Parsing ====================

    /**
     * Parse a Prometheus JSON API response into a list of metric samples.
     * Each sample is a Map with "metric" (labels) and "value" fields.
     *
     * @param response the raw Prometheus JSON response
     * @return list of parsed metric samples
     */
    public List<Map<String, Object>> parseVectorResult(Map<String, Object> response) {
        return parseResultByType(response, "vector");
    }

    /**
     * Parse a matrix (range vector) result from a range query.
     *
     * @param response the raw Prometheus JSON response
     * @return list of parsed series with multiple values
     */
    public List<Map<String, Object>> parseMatrixResult(Map<String, Object> response) {
        return parseResultByType(response, "matrix");
    }

    /**
     * Parse the data section of a Prometheus response regardless of result type.
     *
     * @param response the raw Prometheus JSON response
     * @return the "result" list from the data section
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> parseResultByType(Map<String, Object> response, String expectedType) {
        if (response == null) {
            return Collections.emptyList();
        }

        String status = (String) response.get("status");
        if (!"success".equals(status)) {
            String error = (String) response.getOrDefault("error", "Unknown error");
            log.warn("Prometheus query returned status '{}': {}", status, error);
            return Collections.emptyList();
        }

        Map<String, Object> data = (Map<String, Object>) response.get("data");
        if (data == null) {
            return Collections.emptyList();
        }

        String resultType = (String) data.get("resultType");
        if (expectedType != null && !expectedType.equals(resultType)) {
            log.debug("Expected resultType '{}' but got '{}'", expectedType, resultType);
        }

        Object resultObj = data.get("result");
        if (resultObj instanceof List) {
            return (List<Map<String, Object>>) resultObj;
        }
        return Collections.emptyList();
    }

    /**
     * Extract scalar values from a vector result into a simple name->value map.
     *
     * @param vectorResult parsed vector result from {@link #parseVectorResult}
     * @return map of metric name (from labels) to numeric value
     */
    public Map<String, Double> extractScalarValues(List<Map<String, Object>> vectorResult) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (vectorResult == null) {
            return result;
        }

        for (Map<String, Object> sample : vectorResult) {
            @SuppressWarnings("unchecked")
            Map<String, String> metric = (Map<String, String>) sample.get("metric");
            Object valueObj = sample.get("value");

            String name = buildMetricDisplayName(metric);
            double value = 0.0;

            if (valueObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> valuePair = (List<Object>) valueObj;
                if (valuePair.size() >= 2) {
                    try {
                        value = Double.parseDouble(valuePair.get(1).toString());
                    } catch (NumberFormatException e) {
                        log.debug("Cannot parse value: {}", valuePair.get(1));
                    }
                }
            }

            result.put(name, value);
        }
        return result;
    }

    /**
     * Parse OpenMetrics text format (text/plain; version=0.0.4) into structured data.
     *
     * @param openMetricsText raw OpenMetrics/Prometheus exposition text
     * @return list of parsed metric entries
     */
    public List<Map<String, Object>> parseOpenMetricsText(String openMetricsText) {
        List<Map<String, Object>> metrics = new ArrayList<>();
        if (openMetricsText == null || openMetricsText.isEmpty()) {
            return metrics;
        }

        String[] lines = openMetricsText.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue; // Skip comments and HELP/TYPE lines
            }

            Map<String, Object> entry = parseMetricLine(line);
            if (entry != null) {
                metrics.add(entry);
            }
        }
        return metrics;
    }

    // ==================== Convenience Methods ====================

    /**
     * Get cluster-level metrics from Prometheus.
     * Executes all cluster PromQL queries and aggregates results.
     *
     * @param datasourceId optional datasource ID
     * @return map of metric name to value
     */
    public Map<String, Object> clusterMetrics(String datasourceId) {
        return executeNamedQueries(CLUSTER_QUERIES, null, datasourceId);
    }

    /**
     * Get broker-specific metrics from Prometheus.
     *
     * @param brokerName   the broker name to query
     * @param datasourceId optional datasource ID
     * @return map of metric name to value
     */
    public Map<String, Object> brokerMetrics(String brokerName, String datasourceId) {
        return executeNamedQueries(BROKER_QUERIES, new Object[]{brokerName}, datasourceId);
    }

    /**
     * Get topic-specific metrics from Prometheus.
     *
     * @param topicName    the topic name to query
     * @param datasourceId optional datasource ID
     * @return map of metric name to value
     */
    public Map<String, Object> topicMetrics(String topicName, String datasourceId) {
        return executeNamedQueries(TOPIC_QUERIES, new Object[]{topicName}, datasourceId);
    }

    /**
     * Get consumer group metrics from Prometheus.
     *
     * @param groupName    the consumer group name
     * @param datasourceId optional datasource ID
     * @return map of metric name to value
     */
    public Map<String, Object> consumerGroupMetrics(String groupName, String datasourceId) {
        return executeNamedQueries(CONSUMER_QUERIES, new Object[]{groupName}, datasourceId);
    }

    /**
     * Get system-level metrics from Prometheus.
     *
     * @param datasourceId optional datasource ID
     * @return map of metric name to value
     */
    public Map<String, Object> systemMetrics(String datasourceId) {
        return executeNamedQueries(SYSTEM_QUERIES, null, datasourceId);
    }

    /**
     * Check if a Prometheus datasource is reachable.
     *
     * @param datasourceId optional datasource ID
     * @return true if the datasource responds successfully
     */
    public boolean isDatasourceAvailable(String datasourceId) {
        try {
            Map<String, Object> result = queryInstant("up", datasourceId);
            return "success".equals(result.get("status"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the standard PromQL query templates for each category.
     *
     * @return map of category name to query templates
     */
    public Map<String, Map<String, String>> getQueryTemplates() {
        Map<String, Map<String, String>> templates = new LinkedHashMap<>();
        templates.put("cluster", CLUSTER_QUERIES);
        templates.put("broker", BROKER_QUERIES);
        templates.put("topic", TOPIC_QUERIES);
        templates.put("consumer", CONSUMER_QUERIES);
        templates.put("system", SYSTEM_QUERIES);
        return templates;
    }

    // ==================== Internal Helpers ====================

    /**
     * Execute a set of named PromQL queries and aggregate results.
     */
    private Map<String, Object> executeNamedQueries(Map<String, String> queries, Object[] formatArgs, String datasourceId) {
        Map<String, Object> aggregated = new LinkedHashMap<>();
        aggregated.put("timestamp", System.currentTimeMillis());
        aggregated.put("source", "prometheus");

        Map<String, Object> metrics = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : queries.entrySet()) {
            String metricName = entry.getKey();
            String promql = formatArgs != null
                    ? String.format(entry.getValue(), formatArgs)
                    : entry.getValue();

            try {
                Map<String, Object> response = queryInstant(promql, datasourceId);
                List<Map<String, Object>> vectorResult = parseVectorResult(response);
                Map<String, Double> values = extractScalarValues(vectorResult);

                if (values.size() == 1) {
                    metrics.put(metricName, values.values().iterator().next());
                } else if (values.size() > 1) {
                    metrics.put(metricName, values);
                } else {
                    metrics.put(metricName, null);
                }
            } catch (Exception e) {
                log.debug("Failed to query metric '{}': {}", metricName, e.getMessage());
                metrics.put(metricName, null);
            }
        }

        aggregated.put("metrics", metrics);
        return aggregated;
    }

    /**
     * Execute an HTTP GET to the Prometheus API and parse the JSON response.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> executeQuery(String url, String description) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(READ_TIMEOUT_S))
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();

            if (statusCode != 200) {
                String body = truncate(response.body(), 500);
                throw new ServiceException(statusCode,
                        String.format("Prometheus API returned HTTP %d for %s. Body: %s", statusCode, description, body));
            }

            return objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() { });
        } catch (ServiceException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException(502, "Failed to query Prometheus for " + description + ": " + e.getMessage());
        }
    }

    private String resolveUrl(String datasourceId) {
        if (datasourceId != null && !datasourceId.isEmpty()) {
            String url = datasourceUrls.get(datasourceId);
            if (url != null) {
                return url;
            }
        }
        if (defaultDatasourceUrl != null) {
            return defaultDatasourceUrl;
        }
        throw new ServiceException(500, "No Prometheus datasource configured. "
                + "Set datasource URL via registerDatasource() or configure 'rocketmq.config.metrics.datasource.url'.");
    }

    private static String normalizeUrl(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Build a human-readable display name from metric labels.
     */
    private String buildMetricDisplayName(Map<String, String> metric) {
        if (metric == null || metric.isEmpty()) {
            return "unknown";
        }
        StringBuilder sb = new StringBuilder();
        String name = metric.get("__name__");
        if (name != null) {
            sb.append(name);
        }
        // Append distinguishing labels
        for (Map.Entry<String, String> entry : metric.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("__")) continue;
            if ("broker".equals(key) || "topic".equals(key) || "group".equals(key)
                    || "instance".equals(key) || "consumerGroup".equals(key)) {
                if (sb.length() > 0) sb.append("{");
                sb.append(key).append("=\"").append(entry.getValue()).append("\"");
                if (sb.indexOf("{") >= 0) sb.append("}");
            }
        }
        return sb.length() > 0 ? sb.toString() : "unknown";
    }

    /**
     * Parse a single OpenMetrics exposition line into a structured map.
     * Format: metric_name{label1="val1",label2="val2"} value [timestamp]
     */
    private Map<String, Object> parseMetricLine(String line) {
        try {
            Map<String, Object> entry = new LinkedHashMap<>();
            Map<String, String> labels = new LinkedHashMap<>();

            int braceStart = line.indexOf('{');
            int braceEnd = line.indexOf('}');
            String metricName;
            String valuePart;

            if (braceStart >= 0 && braceEnd > braceStart) {
                metricName = line.substring(0, braceStart);
                String labelsStr = line.substring(braceStart + 1, braceEnd);
                parseLabels(labelsStr, labels);
                valuePart = line.substring(braceEnd + 1).trim();
            } else {
                int spaceIdx = line.indexOf(' ');
                if (spaceIdx < 0) return null;
                metricName = line.substring(0, spaceIdx);
                valuePart = line.substring(spaceIdx).trim();
            }

            entry.put("name", metricName);
            entry.put("labels", labels);

            // Parse value (and optional timestamp)
            String[] valueParts = valuePart.split("\\s+");
            if (valueParts.length >= 1) {
                String valStr = valueParts[0];
                switch (valStr) {
                    case "+Inf":
                        entry.put("value", Double.POSITIVE_INFINITY);
                        break;
                    case "-Inf":
                        entry.put("value", Double.NEGATIVE_INFINITY);
                        break;
                    case "NaN":
                        entry.put("value", Double.NaN);
                        break;
                    default:
                        entry.put("value", Double.parseDouble(valStr));
                        break;
                }
            }
            if (valueParts.length >= 2) {
                entry.put("timestamp", Long.parseLong(valueParts[1]));
            }

            return entry;
        } catch (Exception e) {
            log.trace("Failed to parse OpenMetrics line: {}", line, e);
            return null;
        }
    }

    /**
     * Parse label key="value" pairs from a Prometheus label string.
     */
    private void parseLabels(String labelsStr, Map<String, String> labels) {
        if (labelsStr == null || labelsStr.isEmpty()) return;

        int i = 0;
        while (i < labelsStr.length()) {
            int eqIdx = labelsStr.indexOf('=', i);
            if (eqIdx < 0) break;

            String key = labelsStr.substring(i, eqIdx).trim();
            i = eqIdx + 1;

            // Skip opening quote
            if (i < labelsStr.length() && labelsStr.charAt(i) == '"') {
                i++;
            }

            StringBuilder value = new StringBuilder();
            while (i < labelsStr.length()) {
                char c = labelsStr.charAt(i);
                if (c == '\\' && i + 1 < labelsStr.length()) {
                    value.append(labelsStr.charAt(i + 1));
                    i += 2;
                } else if (c == '"') {
                    i++;
                    break;
                } else {
                    value.append(c);
                    i++;
                }
            }

            labels.put(key, value.toString());

            // Skip comma separator
            if (i < labelsStr.length() && labelsStr.charAt(i) == ',') {
                i++;
            }
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
