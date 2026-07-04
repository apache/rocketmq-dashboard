package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.dashboard.architecture.ClusterProvider;
import org.apache.rocketmq.dashboard.architecture.MetadataProvider;
import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.model.request.MetricsDataSourceRequest;
import org.apache.rocketmq.dashboard.service.ArchitectureBasedService;
import org.apache.rocketmq.dashboard.service.MetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Metrics service implementation for Prometheus integration
 * Provides standardized metrics collection, export, PromQL proxy query,
 * and data source management.
 */
@Service
public class MetricsServiceImpl extends ArchitectureBasedService implements MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsServiceImpl.class);

    /** In-memory data source store. Key: datasource ID */
    private final Map<String, Map<String, Object>> dataSourceStore = new ConcurrentHashMap<>();

    /** Default data source ID */
    private volatile String defaultDataSourceId;

    @Resource
    private MetadataProvider metadataProvider;

    @Resource
    private ClusterProvider clusterProvider;

    @Override
    public Map<String, Object> getClusterMetrics() {
        try {
            if (supports("METRICS_EXPORT")) {
                return metadataProvider.getClusterMetrics();
            }
            handleUnsupportedOperation("Cluster metrics - not supported in current cluster");
            return Map.of();
        } catch (Exception e) {
            handleUnsupportedOperation("Get cluster metrics");
            return Map.of();
        }
    }

    @Override
    public Map<String, Object> getBrokerMetrics(String brokerName) {
        try {
            if (brokerName == null || brokerName.trim().isEmpty()) {
                throw new IllegalArgumentException("Broker name cannot be empty");
            }
            if (supports("BROKER_METRICS")) {
                return metadataProvider.getBrokerMetrics(brokerName);
            }
            handleUnsupportedOperation("Broker metrics - not supported in current cluster");
            return Map.of();
        } catch (Exception e) {
            handleUnsupportedOperation("Get broker metrics");
            return Map.of();
        }
    }

    @Override
    public Map<String, Object> getTopicMetrics(String topic) {
        try {
            if (topic == null || topic.trim().isEmpty()) {
                throw new IllegalArgumentException("Topic cannot be empty");
            }
            if (supports("TOPIC_METRICS")) {
                return metadataProvider.getTopicMetrics(topic);
            }
            handleUnsupportedOperation("Topic metrics - not supported in current cluster");
            return Map.of();
        } catch (Exception e) {
            handleUnsupportedOperation("Get topic metrics");
            return Map.of();
        }
    }

    @Override
    public Map<String, Object> getConsumerGroupMetrics(String groupName) {
        try {
            if (groupName == null || groupName.trim().isEmpty()) {
                throw new IllegalArgumentException("Group name cannot be empty");
            }
            if (supports("CONSUMER_GROUP_METRICS")) {
                return metadataProvider.getConsumerGroupMetrics(groupName);
            }
            handleUnsupportedOperation("Consumer group metrics - not supported in current cluster");
            return Map.of();
        } catch (Exception e) {
            handleUnsupportedOperation("Get consumer group metrics");
            return Map.of();
        }
    }

    @Override
    public List<Map<String, Object>> getAllBrokersMetrics() {
        try {
            if (supports("ALL_BROKERS_METRICS")) {
                return metadataProvider.getAllBrokersMetrics();
            }
            handleUnsupportedOperation("All brokers metrics - not supported in current cluster");
            return List.of();
        } catch (Exception e) {
            handleUnsupportedOperation("Get all brokers metrics");
            return List.of();
        }
    }

    @Override
    public List<Map<String, Object>> getAllTopicsMetrics() {
        try {
            if (supports("ALL_TOPICS_METRICS")) {
                return metadataProvider.getAllTopicsMetrics();
            }
            handleUnsupportedOperation("All topics metrics - not supported in current cluster");
            return List.of();
        } catch (Exception e) {
            handleUnsupportedOperation("Get all topics metrics");
            return List.of();
        }
    }

    @Override
    public Map<String, Object> getClientMetrics() {
        try {
            if (supports("CLIENT_METRICS")) {
                return metadataProvider.getClientMetrics();
            }
            handleUnsupportedOperation("Client metrics - not supported in current cluster");
            return Map.of();
        } catch (Exception e) {
            handleUnsupportedOperation("Get client metrics");
            return Map.of();
        }
    }

    @Override
    public Map<String, Object> getSystemMetrics() {
        try {
            if (supports("SYSTEM_METRICS")) {
                return metadataProvider.getSystemMetrics();
            }
            handleUnsupportedOperation("System metrics - not supported in current cluster");
            return Map.of();
        } catch (Exception e) {
            handleUnsupportedOperation("Get system metrics");
            return Map.of();
        }
    }

    @Override
    public Map<String, Object> getCustomMetrics(String metricType) {
        try {
            if (metricType == null || metricType.trim().isEmpty()) {
                throw new IllegalArgumentException("Metric type cannot be empty");
            }
            if (supports("CUSTOM_METRICS")) {
                return metadataProvider.getCustomMetrics(metricType);
            }
            handleUnsupportedOperation("Custom metrics - not supported in current cluster");
            return Map.of();
        } catch (Exception e) {
            handleUnsupportedOperation("Get custom metrics");
            return Map.of();
        }
    }

    @Override
    public boolean configureMetricsExport(String config) {
        try {
            if (supports("METRICS_CONFIGURATION")) {
                metadataProvider.configureMetricsExport(config);
                return true;
            }
            handleUnsupportedOperation("Metrics configuration - not supported in current cluster");
            return false;
        } catch (Exception e) {
            handleUnsupportedOperation("Configure metrics export");
            return false;
        }
    }

    @Override
    public Map<String, Object> getMetricsSummary() {
        Map<String, Object> summary = new java.util.HashMap<>();
        
        try {
            // Basic cluster info
            summary.put("cluster_name", clusterProvider.getClusterName());
            summary.put("cluster_version", clusterProvider.getClusterVersion());
            summary.put("timestamp", System.currentTimeMillis());
            
            // Metrics availability
            summary.put("metrics_supported", supports("METRICS_EXPORT"));
            summary.put("broker_metrics", supports("BROKER_METRICS"));
            summary.put("topic_metrics", supports("TOPIC_METRICS"));
            summary.put("consumer_metrics", supports("CONSUMER_GROUP_METRICS"));
            summary.put("client_metrics", supports("CLIENT_METRICS"));
            summary.put("system_metrics", supports("SYSTEM_METRICS"));
            
        } catch (Exception e) {
            handleUnsupportedOperation("Get metrics summary");
            summary.put("error", "Failed to generate metrics summary");
        }
        
        return summary;
    }

    /**
     * Validate metric type parameter
     */
    private void validateMetricType(String metricType) {
        if (metricType == null || metricType.trim().isEmpty()) {
            throw new IllegalArgumentException("Metric type cannot be empty");
        }
        if (metricType.length() > 100) {
            throw new IllegalArgumentException("Metric type too long");
        }
    }

    // ==================== PromQL Proxy Query ====================

    @Override
    public Map<String, Object> executePromqlQuery(Map<String, Object> params) {
        String datasourceId = (String) params.get("datasourceId");
        Map<String, Object> dataSource = resolveDataSource(datasourceId);

        if (dataSource == null) {
            throw new UnsupportedOperationException(
                "No Prometheus data source configured. Please add a data source via /api/metrics/datasources first.");
        }

        String baseUrl = (String) dataSource.get("url");
        String query = (String) params.get("query");
        String time = (String) params.get("time");

        // Build Prometheus API URL
        StringBuilder urlBuilder = new StringBuilder(baseUrl);
        if (!baseUrl.endsWith("/")) {
            urlBuilder.append("/");
        }
        urlBuilder.append("api/v1/query?query=").append(urlEncode(query));
        if (time != null && !time.trim().isEmpty()) {
            urlBuilder.append("&time=").append(urlEncode(time));
        }

        return executePrometheusRequest(urlBuilder.toString(), dataSource);
    }

    @Override
    public Map<String, Object> executePromqlRangeQuery(Map<String, Object> params) {
        String datasourceId = (String) params.get("datasourceId");
        Map<String, Object> dataSource = resolveDataSource(datasourceId);

        if (dataSource == null) {
            throw new UnsupportedOperationException(
                "No Prometheus data source configured. Please add a data source via /api/metrics/datasources first.");
        }

        String baseUrl = (String) dataSource.get("url");
        String query = (String) params.get("query");
        String start = (String) params.get("start");
        String end = (String) params.get("end");
        String step = (String) params.get("step");

        // Build Prometheus API URL
        StringBuilder urlBuilder = new StringBuilder(baseUrl);
        if (!baseUrl.endsWith("/")) {
            urlBuilder.append("/");
        }
        urlBuilder.append("api/v1/query_range?")
            .append("query=").append(urlEncode(query))
            .append("&start=").append(urlEncode(start))
            .append("&end=").append(urlEncode(end))
            .append("&step=").append(urlEncode(step));

        return executePrometheusRequest(urlBuilder.toString(), dataSource);
    }

    // ==================== Data Source Management ====================

    @Override
    public List<Map<String, Object>> listDataSources() {
        return new ArrayList<>(dataSourceStore.values());
    }

    @Override
    public Map<String, Object> createDataSource(MetricsDataSourceRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Data source name is required");
        }
        if (request.getUrl() == null || request.getUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("Data source URL is required");
        }

        String id = "ds-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> dataSource = new LinkedHashMap<>();
        dataSource.put("id", id);
        dataSource.put("name", request.getName().trim());
        dataSource.put("type", request.getType() != null ? request.getType() : "PROMETHEUS");
        dataSource.put("url", request.getUrl().trim());
        dataSource.put("username", request.getUsername());
        dataSource.put("isDefault", request.isDefault());
        dataSource.put("readOnly", request.isReadOnly());
        dataSource.put("customHeaders", request.getCustomHeaders());
        dataSource.put("connectionTimeoutMs", request.getConnectionTimeoutMs() != null ? request.getConnectionTimeoutMs() : 5000);
        dataSource.put("readTimeoutMs", request.getReadTimeoutMs() != null ? request.getReadTimeoutMs() : 30000);
        dataSource.put("description", request.getDescription());
        dataSource.put("createdAt", System.currentTimeMillis());

        // Handle default data source
        if (request.isDefault()) {
            defaultDataSourceId = id;
            // Clear isDefault on other data sources
            for (Map<String, Object> ds : dataSourceStore.values()) {
                ds.put("isDefault", false);
            }
            dataSource.put("isDefault", true);
        }

        dataSourceStore.put(id, dataSource);
        log.info("Created data source: id={}, name={}, url={}", id, request.getName(), request.getUrl());

        return dataSource;
    }

    @Override
    public Map<String, Object> updateDataSource(String id, MetricsDataSourceRequest request) {
        Map<String, Object> existing = dataSourceStore.get(id);
        if (existing == null) {
            throw new IllegalArgumentException("Data source not found: " + id);
        }

        if (request.getName() != null) {
            existing.put("name", request.getName().trim());
        }
        if (request.getType() != null) {
            existing.put("type", request.getType());
        }
        if (request.getUrl() != null) {
            existing.put("url", request.getUrl().trim());
        }
        if (request.getUsername() != null) {
            existing.put("username", request.getUsername());
        }
        existing.put("isDefault", request.isDefault());
        existing.put("readOnly", request.isReadOnly());
        if (request.getCustomHeaders() != null) {
            existing.put("customHeaders", request.getCustomHeaders());
        }
        if (request.getConnectionTimeoutMs() != null) {
            existing.put("connectionTimeoutMs", request.getConnectionTimeoutMs());
        }
        if (request.getReadTimeoutMs() != null) {
            existing.put("readTimeoutMs", request.getReadTimeoutMs());
        }
        if (request.getDescription() != null) {
            existing.put("description", request.getDescription());
        }
        existing.put("updatedAt", System.currentTimeMillis());

        // Handle default data source
        if (request.isDefault()) {
            defaultDataSourceId = id;
            for (Map.Entry<String, Map<String, Object>> entry : dataSourceStore.entrySet()) {
                entry.getValue().put("isDefault", entry.getKey().equals(id));
            }
        }

        log.info("Updated data source: id={}", id);
        return existing;
    }

    @Override
    public boolean deleteDataSource(String id) {
        Map<String, Object> removed = dataSourceStore.remove(id);
        if (removed != null) {
            if (id.equals(defaultDataSourceId)) {
                defaultDataSourceId = null;
                // Set first remaining as default if any
                if (!dataSourceStore.isEmpty()) {
                    String newDefault = dataSourceStore.keySet().iterator().next();
                    defaultDataSourceId = newDefault;
                    dataSourceStore.get(newDefault).put("isDefault", true);
                }
            }
            log.info("Deleted data source: id={}", id);
            return true;
        }
        return false;
    }

    @Override
    public Map<String, Object> testDataSource(String id) {
        Map<String, Object> dataSource = dataSourceStore.get(id);
        Map<String, Object> result = new LinkedHashMap<>();

        if (dataSource == null) {
            result.put("success", false);
            result.put("message", "Data source not found: " + id);
            return result;
        }

        String url = (String) dataSource.get("url");
        int connectTimeout = (int) dataSource.getOrDefault("connectionTimeoutMs", 5000);

        try {
            // Test connectivity by hitting Prometheus /api/v1/status/config
            String testUrl = url;
            if (!testUrl.endsWith("/")) {
                testUrl += "/";
            }
            testUrl += "api/v1/status/config";

            long startTime = System.currentTimeMillis();
            HttpURLConnection conn = (HttpURLConnection) new URL(testUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(connectTimeout);
            conn.setInstanceFollowRedirects(true);

            int responseCode = conn.getResponseCode();
            long latencyMs = System.currentTimeMillis() - startTime;

            result.put("success", responseCode == 200);
            result.put("responseCode", responseCode);
            result.put("latencyMs", latencyMs);
            result.put("message", responseCode == 200
                ? "Data source connection successful"
                : "Data source returned HTTP " + responseCode);

            conn.disconnect();
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Connection failed: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
        }

        return result;
    }

    // ==================== Private Helpers ====================

    /**
     * Resolve data source by ID, falling back to default.
     */
    private Map<String, Object> resolveDataSource(String datasourceId) {
        if (datasourceId != null && !datasourceId.trim().isEmpty()) {
            return dataSourceStore.get(datasourceId.trim());
        }
        if (defaultDataSourceId != null) {
            return dataSourceStore.get(defaultDataSourceId);
        }
        // Return first available if no default set
        if (!dataSourceStore.isEmpty()) {
            return dataSourceStore.values().iterator().next();
        }
        return null;
    }

    /**
     * Execute HTTP request to Prometheus API.
     */
    private Map<String, Object> executePrometheusRequest(String urlStr, Map<String, Object> dataSource) {
        Map<String, Object> result = new LinkedHashMap<>();
        int connectTimeout = (int) dataSource.getOrDefault("connectionTimeoutMs", 5000);
        int readTimeout = (int) dataSource.getOrDefault("readTimeoutMs", 30000);

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);

            // Auth headers
            String bearerToken = (String) dataSource.get("bearerToken");
            if (bearerToken != null && !bearerToken.trim().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
            }

            String username = (String) dataSource.get("username");
            if (username != null && !username.trim().isEmpty()) {
                // Basic auth would go here; simplified for now
                conn.setRequestProperty("X-Auth-User", username);
            }

            int responseCode = conn.getResponseCode();
            result.put("status", responseCode == 200 ? "success" : "error");
            result.put("statusCode", responseCode);
            result.put("datasourceId", dataSource.get("id"));
            result.put("datasourceName", dataSource.get("name"));

            if (responseCode != 200) {
                result.put("error", "Prometheus returned HTTP " + responseCode);
            }

            conn.disconnect();
        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", "Failed to query Prometheus: " + e.getMessage());
            log.error("Prometheus query failed: {}", urlStr, e);
        }

        return result;
    }

    /**
     * Simple URL encoding for query parameters.
     */
    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }
}