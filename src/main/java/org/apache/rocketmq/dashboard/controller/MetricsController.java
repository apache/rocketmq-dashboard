package org.apache.rocketmq.dashboard.controller;

import org.apache.rocketmq.dashboard.adapter.PrometheusMetricsAdapter;
import org.apache.rocketmq.dashboard.model.request.MetricsDataSourceRequest;
import org.apache.rocketmq.dashboard.service.MetricsService;
import org.apache.rocketmq.dashboard.support.JsonResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;

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
     * @param request data source configuration
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
     * @param id data source ID
     * @return JsonResult with connection test result
     */
    @GetMapping("/api/metrics/datasources/{id}/test")
    public Object testDataSource(@PathVariable String id) {
        try {
            if (id == null || id.trim().isEmpty()) {
                return new JsonResult<>(1, "Data source ID cannot be empty");
            }

            Map<String, Object> testResult = metricsService.testDataSource(id);
            return new JsonResult<>(testResult);
        } catch (Exception e) {
            log.error("Failed to test data source: {}", id, e);
            return new JsonResult<>(1, "Failed to test data source: " + e.getMessage());
        }
    }
}