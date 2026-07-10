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
package org.apache.rocketmq.dashboard.controller;

import org.apache.rocketmq.dashboard.adapter.PrometheusMetricsAdapter;
import org.apache.rocketmq.dashboard.aspect.admin.annotation.OriginalControllerReturnValue;
import org.apache.rocketmq.dashboard.model.request.MetricsDataSourceRequest;
import org.apache.rocketmq.dashboard.service.MetricsEnhancedService;
import org.apache.rocketmq.dashboard.service.MetricsService;
import org.apache.rocketmq.dashboard.support.JsonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Metrics REST API Controller.
 *
 * <p>Provides Prometheus-compatible metrics export and PromQL proxy query
 * capabilities for RocketMQ monitoring. Supports both direct metrics export
 * and proxy queries to external Prometheus instances.</p>
 *
 * <h3>API Overview</h3>
 * <table border="1">
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>GET</td><td>/metrics</td><td>Export all metrics (Prometheus format)</td></tr>
 *   <tr><td>GET</td><td>/metrics/cluster</td><td>Cluster metrics (Prometheus format)</td></tr>
 *   <tr><td>GET</td><td>/metrics/broker/{name}</td><td>Broker metrics (Prometheus format)</td></tr>
 *   <tr><td>GET</td><td>/metrics/topic/{name}</td><td>Topic metrics (Prometheus format)</td></tr>
 *   <tr><td>GET</td><td>/metrics/consumer/{name}</td><td>Consumer group metrics (Prometheus format)</td></tr>
 *   <tr><td>GET</td><td>/metrics/client</td><td>Client metrics (Prometheus format)</td></tr>
 *   <tr><td>GET</td><td>/metrics/system</td><td>System metrics (Prometheus format)</td></tr>
 *   <tr><td>GET</td><td>/metrics/custom/{type}</td><td>Custom metrics (Prometheus format)</td></tr>
 *   <tr><td>GET</td><td>/metrics/summary</td><td>Metrics summary (JSON)</td></tr>
 *   <tr><td>GET</td><td>/api/metrics/query</td><td>PromQL proxy query</td></tr>
 *   <tr><td>GET</td><td>/api/metrics/range_query</td><td>PromQL range query</td></tr>
 *   <tr><td>GET</td><td>/api/metrics/datasources</td><td>List data sources</td></tr>
 *   <tr><td>POST</td><td>/api/metrics/datasources</td><td>Create data source</td></tr>
 *   <tr><td>PUT</td><td>/api/metrics/datasources/{id}</td><td>Update data source</td></tr>
 *   <tr><td>DELETE</td><td>/api/metrics/datasources/{id}</td><td>Delete data source</td></tr>
 *   <tr><td>GET</td><td>/api/metrics/datasources/{id}/test</td><td>Test data source connection</td></tr>
 *   <tr><td>GET</td><td>/api/metrics/dashboards</td><td>List dashboard panels (e2e discovery)</td></tr>
 *   <tr><td>GET</td><td>/api/metrics/dashboards/{id}</td><td>Get specific dashboard panel</td></tr>
 *   <tr><td>GET</td><td>/api/metrics/alerts</td><td>Get alert rules (JSON-wrapped YAML)</td></tr>
 *   <tr><td>GET</td><td>/api/metrics/alerts.yaml</td><td>Export alert rules as raw Prometheus Alertmanager YAML</td></tr>
 *   <tr><td>POST</td><td>/api/metrics/export/grafana</td><td>Export Grafana dashboard JSON (POST)</td></tr>
 *   <tr><td>GET</td><td>/api/metrics/export/grafana</td><td>One-click export Grafana dashboard JSON (GET)</td></tr>
 *   <tr><td>GET</td><td>/api/metrics/queries</td><td>Get pre-built PromQL query templates</td></tr>
 * </table>
 */
@RestController
@RequestMapping("")
public class MetricsController {

    private static final Logger log = LoggerFactory.getLogger(MetricsController.class);

    @Resource
    private MetricsService metricsService;

    @Resource
    private PrometheusMetricsAdapter prometheusAdapter;

    @Resource
    private MetricsEnhancedService metricsEnhancedService;

    // ==================== Prometheus Format Endpoints ====================

    /**
     * GET /metrics - Export all metrics in Prometheus format.
     */
    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public String exportAllMetrics() {
        try {
            Map<String, Object> clusterMetrics = metricsService.getClusterMetrics();
            return prometheusAdapter.generateFullMetricsExport(clusterMetrics);
        } catch (Exception e) {
            return "# Error collecting metrics: " + e.getMessage();
        }
    }

    /**
     * GET /metrics/cluster - Export cluster metrics in Prometheus format.
     */
    @GetMapping(value = "/metrics/cluster", produces = MediaType.TEXT_PLAIN_VALUE)
    public String exportClusterMetrics() {
        try {
            Map<String, Object> metrics = metricsService.getClusterMetrics();
            return prometheusAdapter.toPrometheusFormat(metrics);
        } catch (Exception e) {
            return "# Error: " + e.getMessage();
        }
    }

    /**
     * GET /metrics/broker/{brokerName} - Export broker metrics in Prometheus format.
     */
    @GetMapping(value = "/metrics/broker/{brokerName}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String exportBrokerMetrics(@PathVariable String brokerName) {
        try {
            Map<String, Object> metrics = metricsService.getBrokerMetrics(brokerName);
            return prometheusAdapter.toPrometheusFormat(metrics);
        } catch (Exception e) {
            return "# Error: " + e.getMessage();
        }
    }

    /**
     * GET /metrics/topic/{topicName} - Export topic metrics in Prometheus format.
     */
    @GetMapping(value = "/metrics/topic/{topicName}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String exportTopicMetrics(@PathVariable String topicName) {
        try {
            Map<String, Object> metrics = metricsService.getTopicMetrics(topicName);
            return prometheusAdapter.toPrometheusFormat(metrics);
        } catch (Exception e) {
            return "# Error: " + e.getMessage();
        }
    }

    /**
     * GET /metrics/consumer/{groupName} - Export consumer group metrics in Prometheus format.
     */
    @GetMapping(value = "/metrics/consumer/{groupName}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String exportConsumerGroupMetrics(@PathVariable String groupName) {
        try {
            Map<String, Object> metrics = metricsService.getConsumerGroupMetrics(groupName);
            return prometheusAdapter.toPrometheusFormat(metrics);
        } catch (Exception e) {
            return "# Error: " + e.getMessage();
        }
    }

    /**
     * GET /metrics/client - Export client metrics in Prometheus format.
     */
    @GetMapping(value = "/metrics/client", produces = MediaType.TEXT_PLAIN_VALUE)
    public String exportClientMetrics() {
        try {
            Map<String, Object> metrics = metricsService.getClientMetrics();
            return prometheusAdapter.toPrometheusFormat(metrics);
        } catch (Exception e) {
            return "# Error: " + e.getMessage();
        }
    }

    /**
     * GET /metrics/system - Export system metrics in Prometheus format.
     */
    @GetMapping(value = "/metrics/system", produces = MediaType.TEXT_PLAIN_VALUE)
    public String exportSystemMetrics() {
        try {
            Map<String, Object> metrics = metricsService.getSystemMetrics();
            return prometheusAdapter.toPrometheusFormat(metrics);
        } catch (Exception e) {
            return "# Error: " + e.getMessage();
        }
    }

    /**
     * GET /metrics/custom/{metricType} - Export custom metrics in Prometheus format.
     */
    @GetMapping(value = "/metrics/custom/{metricType}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String exportCustomMetrics(@PathVariable String metricType) {
        try {
            Map<String, Object> metrics = metricsService.getCustomMetrics(metricType);
            return prometheusAdapter.toPrometheusFormat(metrics);
        } catch (Exception e) {
            return "# Error: " + e.getMessage();
        }
    }

    /**
     * GET /metrics/summary - Get metrics summary in JSON format.
     */
    @GetMapping(value = "/metrics/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getMetricsSummary() {
        return metricsService.getMetricsSummary();
    }

    // ==================== PromQL Proxy Query Endpoints ====================

    /**
     * GET /api/metrics/query - PromQL instant query proxy.
     *
     * <p>Proxies a PromQL instant query to the configured Prometheus data source.</p>
     *
     * @param query    PromQL query expression
     * @param time     evaluation timestamp (RFC3339 or Unix timestamp, optional)
     * @param datasourceId data source ID (optional, uses default if not specified)
     * @return JsonResult containing query results
     */
    @GetMapping("/api/metrics/query")
    public Object promqlQuery(
        @RequestParam String query,
        @RequestParam(required = false) String time,
        @RequestParam(required = false) String datasourceId) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return new JsonResult<>(1, "Query parameter is required");
            }

            Map<String, Object> queryParams = new LinkedHashMap<>();
            queryParams.put("query", query.trim());
            if (time != null && !time.trim().isEmpty()) {
                queryParams.put("time", time.trim());
            }
            if (datasourceId != null && !datasourceId.trim().isEmpty()) {
                queryParams.put("datasourceId", datasourceId.trim());
            }

            Map<String, Object> result = metricsService.executePromqlQuery(queryParams);
            return new JsonResult<>(result);
        } catch (UnsupportedOperationException e) {
            log.warn("PromQL query not supported: {}", e.getMessage());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("supported", false);
            result.put("message", "PromQL proxy requires a configured Prometheus data source. " +
                "Please add a data source via /api/metrics/datasources first.");
            return new JsonResult<>(2, result, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to execute PromQL query: {}", query, e);
            return new JsonResult<>(1, "Failed to execute query: " + e.getMessage());
        }
    }

    /**
     * GET /api/metrics/range_query - PromQL range query proxy.
     *
     * <p>Proxies a PromQL range query to the configured Prometheus data source.</p>
     *
     * @param query    PromQL query expression
     * @param start    start timestamp (RFC3339 or Unix timestamp)
     * @param end      end timestamp (RFC3339 or Unix timestamp)
     * @param step     query resolution step width (duration format or float seconds)
     * @param datasourceId data source ID (optional, uses default if not specified)
     * @return JsonResult containing range query results
     */
    @GetMapping("/api/metrics/range_query")
    public Object promqlRangeQuery(
        @RequestParam String query,
        @RequestParam String start,
        @RequestParam String end,
        @RequestParam String step,
        @RequestParam(required = false) String datasourceId) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return new JsonResult<>(1, "Query parameter is required");
            }
            if (start == null || start.trim().isEmpty()) {
                return new JsonResult<>(1, "Start parameter is required");
            }
            if (end == null || end.trim().isEmpty()) {
                return new JsonResult<>(1, "End parameter is required");
            }
            if (step == null || step.trim().isEmpty()) {
                return new JsonResult<>(1, "Step parameter is required");
            }

            Map<String, Object> queryParams = new LinkedHashMap<>();
            queryParams.put("query", query.trim());
            queryParams.put("start", start.trim());
            queryParams.put("end", end.trim());
            queryParams.put("step", step.trim());
            if (datasourceId != null && !datasourceId.trim().isEmpty()) {
                queryParams.put("datasourceId", datasourceId.trim());
            }

            Map<String, Object> result = metricsService.executePromqlRangeQuery(queryParams);
            return new JsonResult<>(result);
        } catch (UnsupportedOperationException e) {
            log.warn("PromQL range query not supported: {}", e.getMessage());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("supported", false);
            result.put("message", "PromQL range query requires a configured Prometheus data source. " +
                "Please add a data source via /api/metrics/datasources first.");
            return new JsonResult<>(2, result, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to execute PromQL range query: {}", query, e);
            return new JsonResult<>(1, "Failed to execute range query: " + e.getMessage());
        }
    }

    // ==================== Data Source CRUD Endpoints ====================

    /**
     * GET /api/metrics/datasources - List all configured data sources.
     *
     * @return JsonResult containing data source list
     */
    @GetMapping("/api/metrics/datasources")
    public Object listDataSources() {
        try {
            List<Map<String, Object>> datasources = metricsService.listDataSources();
            return new JsonResult<>(datasources);
        } catch (Exception e) {
            log.error("Failed to list data sources", e);
            return new JsonResult<>(1, "Failed to list data sources: " + e.getMessage());
        }
    }

    /**
     * POST /api/metrics/datasources - Create a new data source.
     *
     * @param request data source configuration including providerType
     * @return JsonResult with creation result
     */
    @PostMapping("/api/metrics/datasources")
    public Object createDataSource(@RequestBody MetricsDataSourceRequest request) {
        try {
            if (request == null) {
                return new JsonResult<>(1, "Request body cannot be null");
            }
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return new JsonResult<>(1, "Data source name is required");
            }
            if (request.getUrl() == null || request.getUrl().trim().isEmpty()) {
                return new JsonResult<>(1, "Data source URL is required");
            }

            Map<String, Object> created = metricsService.createDataSource(request);
            return new JsonResult<>(created);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid data source creation request: {}", e.getMessage());
            return new JsonResult<>(1, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to create data source", e);
            return new JsonResult<>(1, "Failed to create data source: " + e.getMessage());
        }
    }

    /**
     * PUT /api/metrics/datasources/{id} - Update an existing data source.
     *
     * @param id      data source ID
     * @param request updated data source configuration
     * @return JsonResult with update result
     */
    @PutMapping("/api/metrics/datasources/{id}")
    public Object updateDataSource(
        @PathVariable String id,
        @RequestBody MetricsDataSourceRequest request) {
        try {
            if (id == null || id.trim().isEmpty()) {
                return new JsonResult<>(1, "Data source ID cannot be empty");
            }
            if (request == null) {
                return new JsonResult<>(1, "Request body cannot be null");
            }

            Map<String, Object> updated = metricsService.updateDataSource(id, request);
            return new JsonResult<>(updated);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid data source update request: {}", e.getMessage());
            return new JsonResult<>(1, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to update data source: {}", id, e);
            return new JsonResult<>(1, "Failed to update data source: " + e.getMessage());
        }
    }

    /**
     * DELETE /api/metrics/datasources/{id} - Delete a data source.
     *
     * @param id data source ID
     * @return JsonResult confirming deletion
     */
    @DeleteMapping("/api/metrics/datasources/{id}")
    public Object deleteDataSource(@PathVariable String id) {
        try {
            if (id == null || id.trim().isEmpty()) {
                return new JsonResult<>(1, "Data source ID cannot be empty");
            }

            boolean success = metricsService.deleteDataSource(id);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", success);
            result.put("datasourceId", id);
            result.put("message", success
                ? "Data source deleted successfully"
                : "Data source not found or deletion failed");
            return new JsonResult<>(result);
        } catch (Exception e) {
            log.error("Failed to delete data source: {}", id, e);
            return new JsonResult<>(1, "Failed to delete data source: " + e.getMessage());
        }
    }

    /**
     * GET /api/metrics/datasources/{id}/test - Test data source connection.
     *
     * <p>Tests connectivity to the data source backend. Supported provider types
     * include PROMETHEUS, VICTORIAMETRICS, THANOS, MIMIR, CORTEX, ARMS, and CUSTOM.</p>
     *
     * @param id data source ID
     * @return JsonResult with connection test result including provider type info
     */
    @GetMapping("/api/metrics/datasources/{id}/test")
    public Object testDataSource(@PathVariable String id) {
        try {
            if (id == null || id.trim().isEmpty()) {
                return new JsonResult<>(1, "Data source ID cannot be empty");
            }

            Map<String, Object> testResult = metricsService.testDataSource(id);
            testResult.putIfAbsent("supportedProviderTypes",
                "PROMETHEUS, VICTORIAMETRICS, THANOS, MIMIR, CORTEX, ARMS, CUSTOM");
            return new JsonResult<>(testResult);
        } catch (Exception e) {
            log.error("Failed to test data source: {}", id, e);
            return new JsonResult<>(1, "Failed to test data source: " + e.getMessage());
        }
    }

    // ==================== Dashboard & Alert Endpoints (RIP-1 METRICS-01) ====================

    /**
     * GET /api/metrics/dashboards - List all pre-built dashboard panels.
     * RIP-1 METRICS-01: Standardized dashboards for all core components.
     *
     * @return JsonResult containing list of dashboard panel metadata
     */
    @GetMapping("/api/metrics/dashboards")
    public Object listDashboards() {
        try {
            List<Map<String, Object>> dashboards = metricsEnhancedService.listDashboards();
            return new JsonResult<>(dashboards);
        } catch (Exception e) {
            log.error("Failed to list dashboards", e);
            return new JsonResult<>(1, "Failed to list dashboards: " + e.getMessage());
        }
    }

    /**
     * GET /api/metrics/panels - List all pre-built dashboard panels.
     * @return JsonResult containing list of dashboard panel metadata
     */
    @GetMapping("/api/metrics/panels")
    public Object listPanelsAlias() {
        try {
            List<Map<String, Object>> dashboards = metricsEnhancedService.listDashboards();
            return new JsonResult<>(dashboards);
        } catch (Exception e) {
            log.error("Failed to list panels", e);
            return new JsonResult<>(1, "Failed to list panels: " + e.getMessage());
        }
    }

    /**
     * GET /api/metrics/dashboards/{id} - Get a specific dashboard panel.
     * RIP-1 METRICS-01: Per-component detail dashboard panels.
     *
     * @param id dashboard panel ID (e.g., "cluster-overview", "consumer-lag")
     * @return JsonResult containing the dashboard panel definition
     */
    @GetMapping("/api/metrics/dashboards/{id}")
    public Object getDashboardPanel(@PathVariable String id) {
        try {
            if (id == null || id.trim().isEmpty()) {
                return new JsonResult<>(1, "Dashboard ID cannot be empty");
            }
            Map<String, Object> panel = metricsEnhancedService.getDashboardPanel(id.trim());
            if (panel.isEmpty()) {
                return new JsonResult<>(1, "Dashboard panel '" + id + "' not found");
            }
            return new JsonResult<>(panel);
        } catch (Exception e) {
            log.error("Failed to get dashboard panel: {}", id, e);
            return new JsonResult<>(1, "Failed to get dashboard panel: " + e.getMessage());
        }
    }

    /**
     * GET /api/metrics/alerts - Get pre-built alert rules in YAML format.
     * RIP-1 METRICS-01: 20+ official alert rules covering broker, topic, consumer,
     * client, and proxy categories.
     *
     * @param format optional format parameter: "json" returns structured data, default "yaml" returns YAML text
     * @return JsonResult containing alert rules
     */
    @GetMapping("/api/metrics/alerts")
    public Object getAlertRules(@RequestParam(defaultValue = "yaml") String format) {
        try {
            String alertRulesYaml = metricsEnhancedService.getAlertRulesYaml();
            if ("json".equalsIgnoreCase(format)) {
                // Return structured with metadata
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("format", "yaml");
                result.put("rules", alertRulesYaml);
                result.put("message", "Alert rules are in Prometheus Alertmanager format. "
                    + "Load into your Alertmanager to enable these rules.");
                return new JsonResult<>(result);
            }
            // Return raw YAML
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("format", "yaml");
            result.put("rules", alertRulesYaml);
            return new JsonResult<>(result);
        } catch (Exception e) {
            log.error("Failed to get alert rules", e);
            return new JsonResult<>(1, "Failed to get alert rules: " + e.getMessage());
        }
    }

    /**
     * GET /api/metrics/alerts.yaml - Export alert rules as raw Prometheus Alertmanager YAML.
     *
     * <p>Returns the alert rules directly in Prometheus Alertmanager compatible YAML format,
     * suitable for saving as a file and loading into Alertmanager. This endpoint is
     * designed for e2e test discovery and direct file download scenarios.</p>
     *
     * @return Raw YAML content in Prometheus Alertmanager format
     */
    @GetMapping(value = "/api/metrics/alerts/yaml", produces = "application/x-yaml")
    @OriginalControllerReturnValue
    public String exportAlertRulesYaml() {
        try {
            return metricsEnhancedService.getAlertRulesYaml();
        } catch (Exception e) {
            log.error("Failed to export alert rules YAML", e);
            return "# Error exporting alert rules: " + e.getMessage();
        }
    }

    /**
     * POST /api/metrics/export/grafana - Export all dashboards as Grafana JSON.
     * RIP-1 METRICS-01: One-click Grafana dashboard export for external visualization.
     *
     * @param dashboardIds optional request body containing list of dashboard IDs to export;
     *                     if empty, exports all dashboards
     * @return JsonResult containing Grafana-compatible JSON
     */
    @PostMapping("/api/metrics/export/grafana")
    public Object exportGrafanaJson(@RequestBody(required = false) Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> dashboardIds = null;
            if (request != null && request.containsKey("dashboardIds")) {
                dashboardIds = (List<String>) request.get("dashboardIds");
            }

            Map<String, Object> grafanaJson = metricsEnhancedService.exportGrafanaJson(dashboardIds);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("grafanaVersion", "8.0+");
            result.put("exportedAt", new Date().toString());
            result.put("dashboards", grafanaJson);
            result.put("message", "Import this JSON into Grafana to use the pre-built RocketMQ dashboards.");
            return new JsonResult<>(result);
        } catch (Exception e) {
            log.error("Failed to export Grafana JSON", e);
            return new JsonResult<>(1, "Failed to export Grafana JSON: " + e.getMessage());
        }
    }

    /**
     * GET /api/metrics/export/grafana - One-click export Grafana dashboard JSON.
     *
     * <p>Exports dashboards as Grafana-compatible JSON via GET request, suitable for
     * direct browser download or e2e test retrieval. Accepts optional comma-separated
     * dashboard IDs; if omitted, exports all dashboards.</p>
     *
     * @param dashboardIds optional comma-separated list of dashboard IDs to export;
     *                     if empty, exports all dashboards
     * @return JsonResult containing Grafana-compatible JSON with export metadata
     */
    @GetMapping("/api/metrics/export/grafana")
    public Object exportGrafanaJsonGet(@RequestParam(required = false) String dashboardIds) {
        try {
            List<String> ids = null;
            if (dashboardIds != null && !dashboardIds.trim().isEmpty()) {
                ids = java.util.Arrays.stream(dashboardIds.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
            }

            Map<String, Object> grafanaJson = metricsEnhancedService.exportGrafanaJson(ids);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("grafanaVersion", "8.0+");
            result.put("exportedAt", new Date().toString());
            result.put("dashboards", grafanaJson);
            result.put("message", "Import this JSON into Grafana to use the pre-built RocketMQ dashboards.");
            return new JsonResult<>(result);
        } catch (Exception e) {
            log.error("Failed to export Grafana JSON", e);
            return new JsonResult<>(1, "Failed to export Grafana JSON: " + e.getMessage());
        }
    }

    /**
     * GET /api/metrics/queries - Get pre-built PromQL query templates.
     * RIP-1 METRICS-01: Standard PromQL queries for common monitoring scenarios.
     *
     * @return JsonResult containing pre-built query templates grouped by category
     */
    @GetMapping("/api/metrics/queries")
    public Object getPrebuiltQueries() {
        try {
            Map<String, Object> queries = metricsEnhancedService.getPrebuiltQueries();
            return new JsonResult<>(queries);
        } catch (Exception e) {
            log.error("Failed to get prebuilt queries", e);
            return new JsonResult<>(1, "Failed to get prebuilt queries: " + e.getMessage());
        }
    }
}