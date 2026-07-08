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
package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.dashboard.model.MetricsHealthResult;
import org.apache.rocketmq.dashboard.service.MetricsEnhancedService;
import org.apache.rocketmq.dashboard.service.MetricsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.TreeSet;
import java.util.NoSuchElementException;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/**
 * Enhanced metrics service implementation with dashboard templates, pre-built queries,
 * alert rules, and Grafana JSON export.
 * <p>
 * Composes PromQL from predefined templates targeting RocketMQ 5.x OpenTelemetry
 * PROM exporter metric families. All data is read through {@link MetricsProvider}.
 * </p>
 */
@Service
public class MetricsEnhancedServiceImpl implements MetricsEnhancedService {

    private static final Logger log = LoggerFactory.getLogger(MetricsEnhancedServiceImpl.class);

    @Resource
    private MetricsProvider metricsProvider;

    // ======================== Health Check ========================

    @Override
    public MetricsHealthResult checkDataSourceHealth() {
        try {
            return metricsProvider.healthCheck();
        } catch (Exception e) {
            MetricsHealthResult fallback = new MetricsHealthResult();
            fallback.setConnected(false);
            fallback.setStatusMessage("Health check unavailable: " + e.getMessage());
            fallback.setMissingMetricFamilies(new ArrayList<>());
            fallback.setAvailableMetricFamilies(new ArrayList<>());
            return fallback;
        }
    }

    // ======================== Dashboard Panels ========================

    /** Panel ID -> panel definition map, lazily built once. */
    private volatile Map<String, Map<String, Object>> panelsCache;
    private volatile boolean panelsInitialized;

    private void ensurePanelsLoaded() {
        if (panelsInitialized) return;
        synchronized (this) {
            if (panelsInitialized) return;
            buildAllPanels();
            panelsInitialized = true;
        }
    }

    @SuppressWarnings("unchecked")
    private void buildAllPanels() {
        panelsCache = new LinkedHashMap<>();

        // 1. cluster-overview
        putPanel("cluster-overview", Map.of(
                "title", "Cluster Overview",
                "description", "Overall RocketMQ cluster health — total messages sent, received, and broker status.",
                "category", "Overview",
                "promql", "sum(rocketmq_broker_bornTotal)",
                "graphPanel", Map.of("type", "graph", "yAxis", Map.of("format", "short")),
                "legendFormat", "{{broker}} - born",
                "threshold", null,
                "alertCondition", null
        ));

        // 2. broker-stats
        putPanel("broker-stats", Map.of(
                "title", "Broker Statistics (Sent / Born TPS)",
                "description", "Real-time send and birth TPS per broker instance.",
                "category", "Broker",
                "promql", "rate(rocketmq_broker_sendTPS[5m])\nrate(rocketmq_broker_bornTPS[5m])",
                "graphPanel", Map.of("type", "graph", "yAxis", Map.of("format", "reqps")),
                "legendFormat", "{{instance}} - {{metric}}",
                "threshold", null,
                "alertCondition", null
        ));

        // 3. topic-throughput
        putPanel("topic-throughput", Map.of(
                "title", "Topic Throughput",
                "description", "Put message rate per topic over the last 5 minutes.",
                "category", "Topic",
                "promql", "topk(10, rate(rocketmq_topic_putNumsTotal[5m]))",
                "graphPanel", Map.of("type", "graph", "yAxis", Map.of("format", "ops")),
                "legendFormat", "{{topic}}",
                "threshold", null,
                "alertCondition", null
        ));

        // 4. consumer-lag
        putPanel("consumer-lag", Map.of(
                "title", "Consumer Group Lag",
                "description", "Maximum pull latency indicating back-pressure in consumer groups.",
                "category", "Consumer",
                "promql", "max by(group)(rocketmq_group_cqPullLatency_max)",
                "graphPanel", Map.of("type", "graph", "yAxis", Map.of("format", "ms")),
                "legendFormat", "{{group}}",
                "threshold", 60000,
                "alertCondition", "rocketmq_group_cqPullLatency_max > 60000"
        ));

        // 5. client-online
        putPanel("client-online", Map.of(
                "title", "Online Clients",
                "description", "Number of currently connected producers/consumers per protocol type.",
                "category", "Client",
                "promql", "count by(protocol)(rocketmq_client_connected)",
                "graphPanel", Map.of("type", "graph", "yAxis", Map.of("format", "short")),
                "legendFormat", "{{protocol}}",
                "threshold", null,
                "alertCondition", null
        ));

        // 6. dlq-count
        putPanel("dlq-count", Map.of(
                "title", "Dead-Letter Queue Messages",
                "description", "Cumulative count of messages sent to DLQ (failed processing).",
                "category", "Error Handling",
                "promql", "rocketmq_dlq_total",
                "graphPanel", Map.of("type", "stat", "valueMap", true),
                "legendFormat", "DLQ count",
                "threshold", 1000,
                "alertCondition", "rocketmq_dlq_total > 1000"
        ));

        // 7. pop-consume
        putPanel("pop-consume", Map.of(
                "title", "POP Consumption Progress",
                "description", "ACK count for POP-mode consumption, tracking progress against backlog.",
                "category", "Consumer",
                "promql", "rate(rocketmq_pop_ack_count_total[5m])",
                "graphPanel", Map.of("type", "graph", "yAxis", Map.of("format", "ops")),
                "legendFormat", "{{group}}",
                "threshold", null,
                "alertCondition", null
        ));

        // 8. proxy-qps
        putPanel("proxy-qps", Map.of(
                "title", "Proxy QPS",
                "description", "Request throughput on each proxy node.",
                "category", "Proxy",
                "promql", "rate(rocketmq_proxy_requestTotal[5m])",
                "graphPanel", Map.of("type", "graph", "yAxis", Map.of("format", "reqps")),
                "legendFormat", "{{instance}}",
                "threshold", null,
                "alertCondition", null
        ));

        // 9. broker-disk-io
        putPanel("broker-disk-io", Map.of(
                "title", "Broker Disk I/O Utilization",
                "description", "Disk usage percentage per broker store path.",
                "category", "Broker",
                "promql", "rocketmq_broker_storePathUseRatio",
                "graphPanel", Map.of("type", "gauge", "max", 100, "min", 0),
                "legendFormat", "{{storePath}}",
                "threshold", 85,
                "alertCondition", "rocketmq_broker_storePathUseRatio > 0.85"
        ));

        // 10. remoting-latency
        putPanel("remoting-latency", Map.of(
                "title", "Remoting Latency Distribution",
                "description", "P50 / P99 / P999 remoting operation latency.",
                "category", "Remoting",
                "promql", "histogram_quantile(0.99, rate(rocketmq_remoting_latency_bucket[5m]))",
                "graphPanel", Map.of("type", "graph", "yAxis", Map.of("format", "ms")),
                "legendFormat", "P99 - {{operation}}",
                "threshold", 500,
                "alertCondition", "histogram_quantile(0.99, rate(rocketmq_remoting_latency_bucket[5m])) > 0.5"
        ));

        // 11. consumer-group-rate
        putPanel("consumer-group-rate", Map.of(
                "title", "Consumer Group Consumption Rate",
                "description", "Messages consumed per group per second.",
                "category", "Consumer",
                "promql", "rate(rocketmq_group_consumeTotal[5m])",
                "graphPanel", Map.of("type", "graph", "yAxis", Map.of("format", "ops")),
                "legendFormat", "{{group}}",
                "threshold", null,
                "alertCondition", null
        ));

        // 12. producer-send-latency
        putPanel("producer-send-latency", Map.of(
                "title", "Producer Send Latency",
                "description", "Send latency histogram for producer clients.",
                "category", "Client",
                "promql", "histogram_quantile(0.95, rate(rocketmq_client_send_latency_bucket[5m]))",
                "graphPanel", Map.of("type", "graph", "yAxis", Map.of("format", "ms")),
                "legendFormat", "P95",
                "threshold", 200,
                "alertCondition", "histogram_quantile(0.95, rate(rocketmq_client_send_latency_bucket[5m])) > 0.2"
        ));

        // 13. broker-memory
        putPanel("broker-memory", Map.of(
                "title", "Broker Memory Usage",
                "description", "JVM heap memory ratio for broker processes.",
                "category", "Broker",
                "promql", "java_lang_memory_HeapMemoryUsage_used / java_lang_memory_HeapMemoryUsage_max",
                "graphPanel", Map.of("type", "gauge", "max", 100, "min", 0),
                "legendFormat", "{{instance}}",
                "threshold", 80,
                "alertCondition", "java_lang_memory_HeapMemoryUsage_used / java_lang_memory_HeapMemoryUsage_max > 0.80"
        ));

        // 14. accumulation-depth-trend
        putPanel("accumulation-depth-trend", Map.of(
                "title", "Accumulation Depth Trend",
                "description", "Consumer group accumulation depth over time, tracking lag trends for backlog monitoring.",
                "category", "Consumer",
                "promql", "max by(group)(rocketmq_group_diff)",
                "graphPanel", Map.of("type", "graph", "yAxis", Map.of("format", "short")),
                "legendFormat", "{{group}}",
                "threshold", 100000,
                "alertCondition", "max by(group)(rocketmq_group_diff) > 100000"
        ));

        // 15. transaction-msg-metrics
        putPanel("transaction-msg-metrics", Map.of(
                "title", "Transaction Message Metrics",
                "description", "Transaction message commit and rollback rates, tracking transaction processing health.",
                "category", "Topic",
                "promql", "rate(rocketmq_transaction_commit_total[5m])",
                "graphPanel", Map.of("type", "graph", "yAxis", Map.of("format", "ops")),
                "legendFormat", "{{instance}}",
                "threshold", null,
                "alertCondition", null
        ));

        // 16. storage-write-latency
        putPanel("storage-write-latency", Map.of(
                "title", "Storage Write Latency",
                "description", "Message storage write latency per broker (P99), indicating disk I/O performance.",
                "category", "Broker",
                "promql", "histogram_quantile(0.99, rate(rocketmq_broker_putLatency_bucket[5m]))",
                "graphPanel", Map.of("type", "graph", "yAxis", Map.of("format", "ms")),
                "legendFormat", "{{instance}}",
                "threshold", 100,
                "alertCondition", "histogram_quantile(0.99, rate(rocketmq_broker_putLatency_bucket[5m])) > 0.1"
        ));

        // 17. broker-network-throughput
        putPanel("broker-network-throughput", Map.of(
                "title", "Broker Network Throughput",
                "description", "Network bytes sent and received throughput per broker instance.",
                "category", "Broker",
                "promql", "rate(rocketmq_broker_sendTPS[5m])",
                "graphPanel", Map.of("type", "graph", "yAxis", Map.of("format", "reqps")),
                "legendFormat", "{{instance}}",
                "threshold", null,
                "alertCondition", null
        ));

        // 18. replica-sync-latency
        putPanel("replica-sync-latency", Map.of(
                "title", "Replica Sync Latency",
                "description", "Master-slave replica synchronization lag in bytes, indicating replication health.",
                "category", "Broker",
                "promql", "rocketmq_broker_slaveFallBehindSize",
                "graphPanel", Map.of("type", "graph", "yAxis", Map.of("format", "bytes")),
                "legendFormat", "{{broker}}",
                "threshold", 1073741824,
                "alertCondition", "rocketmq_broker_slaveFallBehindSize > 1073741824"
        ));

        // 19. hot-topic-top10
        putPanel("hot-topic-top10", Map.of(
                "title", "Hot Topic Top 10",
                "description", "Top 10 topics by message put rate, identifying high-traffic topics.",
                "category", "Topic",
                "promql", "topk(10, rate(rocketmq_topic_putNumsTotal[5m]))",
                "graphPanel", Map.of("type", "graph", "yAxis", Map.of("format", "ops")),
                "legendFormat", "{{topic}}",
                "threshold", null,
                "alertCondition", null
        ));

        // 20. consumer-concurrency
        putPanel("consumer-concurrency", Map.of(
                "title", "Consumer Concurrency",
                "description", "Active consumer thread pool size vs configured maximum per consumer group, helping identify scaling needs.",
                "category", "Consumer",
                "promql", "rocketmq_group_consumeThreadPoolSize",
                "graphPanel", Map.of("type", "graph", "yAxis", Map.of("format", "short")),
                "legendFormat", "{{group}}",
                "threshold", null,
                "alertCondition", null
        ));

        // 21. broker-jvm-gc-stats
        putPanel("broker-jvm-gc-stats", Map.of(
                "title", "Broker JVM GC Statistics",
                "description", "JVM garbage collection pause time and frequency per broker, helping tune JVM parameters.",
                "category", "Broker",
                "promql", "rate(java_lang_GcTime_milliseconds[5m])",
                "graphPanel", Map.of("type", "graph", "yAxis", Map.of("format", "ms")),
                "legendFormat", "{{instance}}",
                "threshold", null,
                "alertCondition", null
        ));
    }

    private void putPanel(String id, Map<String, Object> panel) {
        Map<String, Object> enriched = new HashMap<>(panel);
        enriched.put("id", id);
        // Ensure graphPanel has all needed keys
        if (!enriched.containsKey("graphPanel")) {
            enriched.put("graphPanel", Map.of("type", "graph"));
        }
        panelsCache.put(id, enriched);
    }

    @Override
    public Map<String, Object> getDashboardPanel(String panelId) {
        ensurePanelsLoaded();
        Map<String, Object> panel = panelsCache.get(panelId);
        if (panel == null) {
            throw new NoSuchElementException("Dashboard panel not found: " + panelId
                    + ". Available panels: " + panelsCache.keySet());
        }
        return Collections.unmodifiableMap(panel);
    }

    @Override
    public List<Map<String, Object>> listDashboards() {
        ensurePanelsLoaded();
        return panelsCache.values().stream()
                .map(p -> Map.<String, Object>of(
                        "id", p.get("id"),
                        "title", p.get("title"),
                        "category", p.get("category")
                ))
                .collect(Collectors.toList());
    }

    // ======================== Grafana Export ========================

    @Override
    public String exportGrafanaJson(String panelId) {
        Map<String, Object> panel = getDashboardPanel(panelId);
        String title = (String) panel.get("title");
        String promql = (String) panel.get("promql");

        String timeFrom = "now-1h";
        String datasource = "\"${datasource}\"";

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        // Dashboard meta
        json.append("  \"annotations\": {\n");
        json.append("    \"list\": [\n");
        json.append("      {\n").append("        \"builtIn\": 1,\n");
        json.append("        \"datasource\": {\n").append("          \"type\": \"grafana\",\n");
        json.append("          \"uid\": \"-- Grafana --\"\n").append("        },\n");
        json.append("        \"enable\": true,\n").append("        \"hide\": true,\n");
        json.append("        \"iconColor\": \"rgba(0, 211, 255, 1)\",\n");
        json.append("        \"name\": \"Annotations & Alerts\",\n").append("        \"type\": \"dashboard\"\n");
        json.append("      }\n");
        json.append("    ]\n");
        json.append("  },\n");

        // Targets (query panels)
        json.append("  \"editable\": true,\n");
        json.append("  \"fiscalYearStartMonth\": 0,\n");
        json.append("  \"graphTooltip\": 0,\n");
        json.append("  \"id\": null,\n");
        json.append("  \"links\": [],\n");
        json.append("  \"panels\": [\n");
        json.append("    {\n");
        json.append("      \"datasource\": ").append(datasource).append(",\n");
        json.append("      \"description\": \"").append(escapeJson((String) panel.getOrDefault("description", ""))).append("\",\n");
        json.append("      \"fieldConfig\": {\n");
        json.append("        \"defaults\": {\n");
        json.append("          \"color\": {\n").append("            \"mode\": \"palette-classic\"\n");
        json.append("          },\n");
        json.append("          \"custom\": {\n");
        json.append("            \"axisCenteredZero\": false,\n");
        json.append("            \"axisColorMode\": \"text\",\n");
        json.append("            \"axisLabel\": \"\",\n");
        json.append("            \"axisPlacement\": \"auto\",\n");
        json.append("            \"barAlignment\": 0,\n");
        json.append("            \"drawStyle\": \"line\",\n");
        json.append("            \"fillOpacity\": 10,\n");
        json.append("            \"gradientMode\": \"none\",\n");
        json.append("            \"hideFrom\": {\"legend\": false, \"tooltip\": false, \"viz\": false},\n");
        json.append("            \"lineInterpolation\": \"linear\",\n");
        json.append("            \"lineWidth\": 1,\n");
        json.append("            \"pointSize\": 5,\n");
        json.append("            \"scaleDistribution\": {\"type\": \"linear\"},\n");
        json.append("            \"showPoints\": \"auto\",\n");
        json.append("            \"spanNulls\": false,\n");
        json.append("            \"styleOverrides\": {},\n");
        json.append("            \"thresholdStyle\": {\"mode\": \"off\"}\n");
        json.append("          },\n");
        json.append("          \"thresholdsStyle\": {\n");
        json.append("            \"mode\": \"off\"\n");
        json.append("          }\n");
        json.append("        },\n");
        json.append("        \"overrides\": []\n");
        json.append("      },\n");

        // Title and description
        json.append("      \"gridPos\": {\n");
        json.append("        \"h\": 8,\n").append("        \"w\": 12,\n");
        json.append("        \"x\": 0,\n").append("        \"y\": 0\n");
        json.append("      },\n");
        json.append("      \"id\": 1,\n");

        // PromQL target
        json.append("      \"targets\": [\n");
        json.append("        {\n");
        json.append("          \"datasource\": ").append(datasource).append(",\n");
        json.append("          \"expr\": \"").append(escapeJson(promql)).append("\",\n");
        json.append("          \"refId\": \"A\",\n");
        json.append("          \"legendFormat\": \"").append(escapeJson((String) panel.getOrDefault("legendFormat", ""))).append("\"\n");
        json.append("        }\n");
        json.append("      ],\n");

        // Threshold config (if defined)
        Object threshold = panel.get("threshold");
        if (threshold != null) {
            json.append("      \"thresholdsStep\": 100,\n");
        }

        // Time range
        json.append("      \"timeFrom\": null,\n");
        json.append("      \"timeShift\": null,\n");
        json.append("      \"title\": \"").append(escapeJson(title)).append("\",\n");
        json.append("      \"tooltip\": {\n");
        json.append("        \"mode\": \"single\",\n").append("        \"sort\": 0\n");
        json.append("      },\n");
        json.append("      \"type\": \"graph\",\n");
        json.append("      \"legend\": {\n");
        json.append("        \"calcs\": [\"mean\", \"max\", \"lastNotNull\"],\n");
        json.append("        \"displayMode\": \"table\",\n");
        json.append("        \"placement\": \"bottom\",\n");
        json.append("        \"showLegend\": true\n");
        json.append("      }\n");
        json.append("    }\n");
        json.append("  ],\n");

        // Schema version
        json.append("  \"schemaVersion\": 39,\n");
        json.append("  \"tags\": [\"rocketmq\", \"metrics\", \"auto-generated\"],\n");
        json.append("  \"templating\": {\n");
        json.append("    \"list\": [\n");
        json.append("      {\n");
        json.append("        \"current\": {\n");
        json.append("          \"selected\": false,\n").append("          \"text\": \"Prometheus\",\n");
        json.append("          \"value\": \"Prometheus\"\n").append("        },\n");
        json.append("        \"hide\": 0,\n");
        json.append("        \"includeAll\": false,\n");
        json.append("        \"label\": \"Datasource\",\n");
        json.append("        \"multi\": false,\n");
        json.append("        \"name\": \"datasource\",\n");
        json.append("        \"options\": [],\n");
        json.append("        \"query\": \"prometheus\",\n");
        json.append("        \"refresh\": 1,\n");
        json.append("        \"regex\": \"\",\n");
        json.append("        \"skipUrlSync\": false,\n");
        json.append("        \"type\": \"datasource\"\n");
        json.append("      }\n");
        json.append("    ]\n");
        json.append("  },\n");

        // Time picker
        json.append("  \"time\": {\n");
        json.append("    \"from\": \"").append(timeFrom).append("\",\n");
        json.append("    \"to\": \"now\"\n");
        json.append("  },\n");
        json.append("  \"timepicker\": {},\n");
        json.append("  \"timezone\": \"browser\",\n");
        json.append("  \"title\": \"").append(escapeJson(title)).append("\",\n");
        json.append("  \"uid\": \"").append(UUID.randomUUID().toString().substring(0, 12)).append("\",\n");
        json.append("  \"version\": 1,\n");
        json.append("  \"weekStart\": \"\"\n");
        json.append("}");

        return json.toString();
    }

    @Override
    public Map<String, Object> exportGrafanaJson(List<String> panelIds) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> allPanels = listDashboards();

        List<String> ids = (panelIds != null && !panelIds.isEmpty())
            ? panelIds
            : allPanels.stream().map(p -> (String) p.get("id")).collect(Collectors.toList());

        for (String panelId : ids) {
            try {
                String grafanaJson = exportGrafanaJson(panelId);
                result.put(panelId, grafanaJson);
            } catch (Exception e) {
                log.warn("Failed to export Grafana JSON for panel {}: {}", panelId, e.getMessage());
                result.put(panelId, "{\"error\": \"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
        return result;
    }

    // ======================== Prebuilt Queries ========================

    @Override
    public Map<String, Object> getPrebuiltQueries() {
        ensurePanelsLoaded();

        Map<String, Object> result = new LinkedHashMap<>();
        Set<String> categories = panelsCache.values().stream()
                .map(p -> (String) p.get("category"))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));

        for (String category : categories) {
            List<Map<String, Object>> queries = panelsCache.values().stream()
                    .filter(p -> category.equals(p.get("category")))
                    .map(p -> {
                        Map<String, Object> q = new LinkedHashMap<>();
                        q.put("id", p.get("id"));
                        q.put("title", p.get("title"));
                        q.put("promql", p.get("promql"));
                        q.put("description", p.get("description"));
                        return q;
                    })
                    .collect(Collectors.toList());
            result.put(category, queries);
        }
        return result;
    }

    // ======================== Alert Rules YAML ========================

    @Override
    public String getAlertRulesYaml() {
        return ALERT_RULES_YAML;
    }

    /**
     * Built-in alert rule definitions compliant with Prometheus/VictoriaMetrics Alertmanager format.
     * Contains 20 rules covering broker, topic, consumer, client, proxy, and infrastructure concerns.
     */
    private static final String ALERT_RULES_YAML =
            "# ==============================================================\n"
            + "# RocketMQ 5.x Monitoring — Recommended Alert Rules\n"
            + "# Generated by MetricsEnhancedService (RIP-1 METRICS-01)\n"
            + "# Compatible with Prometheus / VictoriaMetrics / Thanos alerting\n"
            + "# ==============================================================\n"
            + "\n"
            + "groups:\n"
            + "  - name: rocketmq-broker.rules\n"
            + "    rules:\n"
            + "      # Rule 1: Broker is down\n"
            + "      - alert: RocketMQ_Broker_Down\n"
            + "        expr: up{job=~\"rocketmq.*broker.*\"} == 0\n"
            + "        for: 2m\n"
            + "        labels:\n"
            + "          severity: critical\n"
            + "          team: broker\n"
            + "        annotations:\n"
            + "          summary: \"Broker instance {{ $labels.instance }} is down\"\n"
            + "          description: \"Broker has been unreachable for more than 2 minutes.\"\n"
            + "\n"
            + "      # Rule 2: Broker disk usage high\n"
            + "      - alert: RocketMQ_Broker_DiskUsage_High\n"
            + "        expr: rocketmq_broker_storePathUseRatio > 0.85\n"
            + "        for: 5m\n"
            + "        labels:\n"
            + "          severity: warning\n"
            + "          team: broker\n"
            + "        annotations:\n"
            + "          summary: \"Broker disk usage at {{ $value | humanizePercentage }}\"\n"
            + "          description: \"Store path on {{ $labels.instance }} is above 85% capacity.\"\n"
            + "\n"
            + "      # Rule 3: Broker memory pressure\n"
            + "      - alert: RocketMQ_Broker_Memory_High\n"
            + "        expr: java_lang_memory_HeapMemoryUsage_used / java_lang_memory_HeapMemoryUsage_max > 0.85\n"
            + "        for: 5m\n"
            + "        labels:\n"
            + "          severity: warning\n"
            + "          team: broker\n"
            + "        annotations:\n"
            + "          summary: \"Broker JVM heap above 85% on {{ $labels.instance }}\"\n"
            + "          description: \"GC pressure may increase. Consider scaling brokers.\"\n"
            + "\n"
            + "      # Rule 4: Producer send TPS abnormal drop\n"
            + "      - alert: RocketMQ_Broker_Send_TPS_Low\n"
            + "        expr: sum(rate(rocketmq_broker_sendTPS[5m])) by (broker) < 10\n"
            + "        for: 10m\n"
            + "        labels:\n"
            + "          severity: warning\n"
            + "          team: broker\n"
            + "        annotations:\n"
            + "          summary: \"Broker send TPS dropped below 10/s on {{ $labels.broker }}\"\n"
            + "\n"
            + "  - name: rocketmq-topic.rules\n"
            + "    rules:\n"
            + "      # Rule 5: Topic message accumulation\n"
            + "      - alert: RocketMQ_Topic_LargeAccumulate\n"
            + "        expr: rocketmq_topic_putNumsTotal - on(topic) group_right() sum(rocketmq_group_consumeTotal) by (topic) > 100000\n"
            + "        for: 5m\n"
            + "        labels:\n"
            + "          severity: critical\n"
            + "          team: topic\n"
            + "        annotations:\n"
            + "          summary: \"Topic {{ $labels.topic }} has accumulated > 100K uncommitted messages\"\n"
            + "\n"
            + "      # Rule 6: Topic put failure rate\n"
            + "      - alert: RocketMQ_Topic_PutFailure_High\n"
            + "        expr: rate(rocketmq_topic_putErrorMsgsTotal[5m]) > 0.01 * rate(rocketmq_topic_putNumsTotal[5m])\n"
            + "        for: 5m\n"
            + "        labels:\n"
            + "          severity: warning\n"
            + "          team: topic\n"
            + "        annotations:\n"
            + "          summary: \"Topic {{ $labels.topic }} put error rate above 1%\"\n"
            + "\n"
            + "  - name: rocketmq-consumer.rules\n"
            + "    rules:\n"
            + "      # Rule 7: Consumer group lag\n"
            + "      - alert: RocketMQ_Consumer_Group_Lag\n"
            + "        expr: rocketmq_group_cqPullLatency_max > 60000\n"
            + "        for: 5m\n"
            + "        labels:\n"
            + "          severity: critical\n"
            + "          team: consumer\n"
            + "        annotations:\n"
            + "          summary: \"Consumer group {{ $labels.group }} lag exceeds 60 seconds\"\n"
            + "          description: \"Consumption cannot keep up with production rate.\"\n"
            + "\n"
            + "      # Rule 8: Consumer consume failure spike\n"
            + "      - alert: RocketMQ_Consumer_ConsumeFailed_Spike\n"
            + "        expr: rate(rocketmq_group_consumeErrorMsgsTotal[5m]) > 5\n"
            + "        for: 5m\n"
            + "        labels:\n"
            + "          severity: warning\n"
            + "          team: consumer\n"
            + "        annotations:\n"
            + "          summary: \"Group {{ $labels.group }} failing > 5 msgs/sec\"\n"
            + "\n"
            + "      # Rule 9: Consumer offset stuck\n"
            + "      - alert: RocketMQ_Consumer_Offset_Stuck\n"
            + "        expr: delta(rocketmq_group_consumeTotal[10m]) == 0 and absent(rocketmq_group_cqPullLatency_max < 1)\n"
            + "        for: 15m\n"
            + "        labels:\n"
            + "          severity: critical\n"
            + "          team: consumer\n"
            + "        annotations:\n"
            + "          summary: \"Consumer group {{ $labels.group }} offset has not advanced in 15 minutes\"\n"
            + "\n"
            + "  - name: rocketmq-client.rules\n"
            + "    rules:\n"
            + "      # Rule 10: Client disconnection spike\n"
            + "      - alert: RocketMQ_Client_Disconnect\n"
            + "        expr: increase(rocketmq_client_disconnected_total[5m]) > 10\n"
            + "        for: 5m\n"
            + "        labels:\n"
            + "          severity: warning\n"
            + "          team: client\n"
            + "        annotations:\n"
            + "          summary: \"{{ $value | printf \\\"%.0f\\\" }} client disconnections in 5 min\"\n"
            + "\n"
            + "      # Rule 11: Producer send latency high\n"
            + "      - alert: RocketMQ_Produce_SendLatency_High\n"
            + "        expr: histogram_quantile(0.95, rate(rocketmq_client_send_latency_bucket[5m])) > 0.2\n"
            + "        for: 5m\n"
            + "        labels:\n"
            + "          severity: warning\n"
            + "          team: client\n"
            + "        annotations:\n"
            + "          summary: \"Producer P95 send latency > 200ms\"\n"
            + "\n"
            + "  - name: rocketmq-proxy.rules\n"
            + "    rules:\n"
            + "      # Rule 12: Proxy request timeout\n"
            + "      - alert: RocketMQ_Proxy_Timeout_High\n"
            + "        expr: rate(rocketmq_proxy_requestTimeoutTotal[5m]) > 1\n"
            + "        for: 5m\n"
            + "        labels:\n"
            + "          severity: warning\n"
            + "          team: proxy\n"
            + "        annotations:\n"
            + "          summary: \"Proxy returning > 1 req/s timeouts\"\n"
            + "\n"
            + "      # Rule 13: Proxy overload (too many concurrent requests)\n"
            + "      - alert: RocketMQ_Proxy_Overloaded\n"
            + "        expr: rocketmq_proxy_concurrentRequests > 1000\n"
            + "        for: 3m\n"
            + "        labels:\n"
            + "          severity: critical\n"
            + "          team: proxy\n"
            + "        annotations:\n"
            + "          summary: \"Proxy concurrently handling > 1000 requests\"\n"
            + "\n"
            + "  - name: rocketmq-errors.rules\n"
            + "    rules:\n"
            + "      # Rule 14: ACL auth failure spike\n"
            + "      - alert: RocketMQ_Acl_AuthFailure\n"
            + "        expr: rate(rocketmq_acl_authFailureTotal[5m]) > 0.05 * rate(rocketmq_acl_authTotal[5m])\n"
            + "        for: 3m\n"
            + "        labels:\n"
            + "          severity: warning\n"
            + "          team: security\n"
            + "        annotations:\n"
            + "          summary: \"ACL auth failure rate > 5% — possible unauthorized access attempt\"\n"
            + "\n"
            + "      # Rule 15: DLQ message spike\n"
            + "      - alert: RocketMQ_DLQ_Spike\n"
            + "        expr: increase(rocketmq_dlq_total[15m]) > 1000\n"
            + "        for: 5m\n"
            + "        labels:\n"
            + "          severity: warning\n"
            + "          team: reliability\n"
            + "        annotations:\n"
            + "          summary: \"{{ $value | printf \\\"%.0f\\\" }} new DLQ messages in 15 min\"\n"
            + "\n"
            + "      # Rule 16: POP consumption failure\n"
            + "      - alert: RocketMQ_Pop_Consume_Failed\n"
            + "        expr: rate(rocketmq_pop_nackTotal[5m]) > 5\n"
            + "        for: 5m\n"
            + "        labels:\n"
            + "          severity: warning\n"
            + "          team: consumer\n"
            + "        annotations:\n"
            + "          summary: \"POP nack rate above 5/s — consumers may be dropping messages\"\n"
            + "\n"
            + "  - name: rocketmq-broker-extended.rules\n"
            + "    rules:\n"
            + "      # Rule 17: Transaction check failure rate > 10%\n"
            + "      - alert: Transaction_Check_Failure\n"
            + "        expr: rate(rocketmq_transaction_check_failure_total[5m]) > 0.1 * rate(rocketmq_transaction_check_total[5m])\n"
            + "        for: 5m\n"
            + "        labels:\n"
            + "          severity: warning\n"
            + "          team: broker\n"
            + "        annotations:\n"
            + "          summary: \"Transaction check failure rate > 10% on {{ $labels.instance }}\"\n"
            + "          description: \"Transaction message check operations are failing above the 10% threshold.\"\n"
            + "\n"
            + "      # Rule 18: Message accumulation growth rate > 1000 msg/min\n"
            + "      - alert: Message_Accumulation_Rate\n"
            + "        expr: rate(rocketmq_topic_accumulation_total[1m]) * 60 > 1000\n"
            + "        for: 5m\n"
            + "        labels:\n"
            + "          severity: warning\n"
            + "          team: topic\n"
            + "        annotations:\n"
            + "          summary: \"Message accumulation growing > 1000 msg/min on {{ $labels.topic }}\"\n"
            + "          description: \"Topic {{ $labels.topic }} message accumulation rate exceeds 1000 messages per minute.\"\n"
            + "\n"
            + "      # Rule 19: Broker JVM GC time exceeds 5%\n"
            + "      - alert: Broker_JVM_GC_High\n"
            + "        expr: rate(rocketmq_broker_jvm_gc_seconds_sum[5m]) / rate(rocketmq_broker_jvm_gc_seconds_count[5m]) > 0.05\n"
            + "        for: 5m\n"
            + "        labels:\n"
            + "          severity: warning\n"
            + "          team: broker\n"
            + "        annotations:\n"
            + "          summary: \"Broker JVM GC time exceeds 5% on {{ $labels.instance }}\"\n"
            + "          description: \"GC pause time accounts for more than 5% of runtime.\"\n"
            + "\n"
            + "      # Rule 20: Proxy connection reject count > 0\n"
            + "      - alert: Proxy_Connection_Reject\n"
            + "        expr: rocketmq_proxy_connection_reject_total > 0\n"
            + "        for: 1m\n"
            + "        labels:\n"
            + "          severity: warning\n"
            + "          team: proxy\n"
            + "        annotations:\n"
            + "          summary: \"Proxy connection rejection detected on {{ $labels.instance }}\"\n"
            + "          description: \"Proxy is rejecting incoming connections. Check connection limits and broker health.\"\n"
            + "";

    // ======================== Internal Helpers ========================

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\t", "\\t");
    }
}
