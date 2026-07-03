package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.dashboard.architecture.ClusterProvider;
import org.apache.rocketmq.dashboard.architecture.MetadataProvider;
import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.service.ArchitectureBasedService;
import org.apache.rocketmq.dashboard.service.MetricsService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * Metrics service implementation for Prometheus integration
 * Provides standardized metrics collection and export
 */
@Service
public class MetricsServiceImpl extends ArchitectureBasedService implements MetricsService {

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
}