package org.apache.rocketmq.dashboard.service;

import java.util.List;
import java.util.Map;

/**
 * Metrics collection and export service interface
 * Provides Prometheus-compatible metrics for RocketMQ monitoring
 */
public interface MetricsService {

    /**
     * Get cluster-level metrics
     */
    Map<String, Object> getClusterMetrics();

    /**
     * Get broker-specific metrics
     */
    Map<String, Object> getBrokerMetrics(String brokerName);

    /**
     * Get topic-specific metrics
     */
    Map<String, Object> getTopicMetrics(String topic);

    /**
     * Get consumer group metrics
     */
    Map<String, Object> getConsumerGroupMetrics(String groupName);

    /**
     * Get metrics from all brokers
     */
    List<Map<String, Object>> getAllBrokersMetrics();

    /**
     * Get metrics from all topics
     */
    List<Map<String, Object>> getAllTopicsMetrics();

    /**
     * Get client connection metrics
     */
    Map<String, Object> getClientMetrics();

    /**
     * Get system-level metrics (CPU, memory, disk)
     */
    Map<String, Object> getSystemMetrics();

    /**
     * Get custom metrics by type
     */
    Map<String, Object> getCustomMetrics(String metricType);

    /**
     * Configure metrics export settings
     */
    boolean configureMetricsExport(String config);

    /**
     * Get metrics availability summary
     */
    Map<String, Object> getMetricsSummary();
}