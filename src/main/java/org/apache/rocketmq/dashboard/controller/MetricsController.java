package org.apache.rocketmq.dashboard.controller;

import org.apache.rocketmq.dashboard.adapter.PrometheusMetricsAdapter;
import org.apache.rocketmq.dashboard.service.MetricsService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

/**
 * Metrics controller for Prometheus integration
 * Exposes RocketMQ metrics in Prometheus exposition format
 */
@RestController
@RequestMapping("/metrics")
public class MetricsController {

    @Resource
    private MetricsService metricsService;

    @Resource
    private PrometheusMetricsAdapter prometheusAdapter;

    /**
     * Export all metrics in Prometheus format
     */
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public String exportAllMetrics() {
        try {
            Map<String, Object> clusterMetrics = metricsService.getClusterMetrics();
            return prometheusAdapter.generateFullMetricsExport(clusterMetrics);
        } catch (Exception e) {
            return "# Error collecting metrics: " + e.getMessage();
        }
    }

    /**
     * Export cluster metrics
     */
    @GetMapping(value = "/cluster", produces = MediaType.TEXT_PLAIN_VALUE)
    public String exportClusterMetrics() {
        try {
            Map<String, Object> metrics = metricsService.getClusterMetrics();
            return prometheusAdapter.toPrometheusFormat(metrics);
        } catch (Exception e) {
            return "# Error: " + e.getMessage();
        }
    }

    /**
     * Export broker metrics
     */
    @GetMapping(value = "/broker/{brokerName}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String exportBrokerMetrics(@PathVariable String brokerName) {
        try {
            Map<String, Object> metrics = metricsService.getBrokerMetrics(brokerName);
            return prometheusAdapter.toPrometheusFormat(metrics);
        } catch (Exception e) {
            return "# Error: " + e.getMessage();
        }
    }

    /**
     * Export topic metrics
     */
    @GetMapping(value = "/topic/{topicName}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String exportTopicMetrics(@PathVariable String topicName) {
        try {
            Map<String, Object> metrics = metricsService.getTopicMetrics(topicName);
            return prometheusAdapter.toPrometheusFormat(metrics);
        } catch (Exception e) {
            return "# Error: " + e.getMessage();
        }
    }

    /**
     * Export consumer group metrics
     */
    @GetMapping(value = "/consumer/{groupName}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String exportConsumerGroupMetrics(@PathVariable String groupName) {
        try {
            Map<String, Object> metrics = metricsService.getConsumerGroupMetrics(groupName);
            return prometheusAdapter.toPrometheusFormat(metrics);
        } catch (Exception e) {
            return "# Error: " + e.getMessage();
        }
    }

    /**
     * Export client metrics
     */
    @GetMapping(value = "/client", produces = MediaType.TEXT_PLAIN_VALUE)
    public String exportClientMetrics() {
        try {
            Map<String, Object> metrics = metricsService.getClientMetrics();
            return prometheusAdapter.toPrometheusFormat(metrics);
        } catch (Exception e) {
            return "# Error: " + e.getMessage();
        }
    }

    /**
     * Export system metrics
     */
    @GetMapping(value = "/system", produces = MediaType.TEXT_PLAIN_VALUE)
    public String exportSystemMetrics() {
        try {
            Map<String, Object> metrics = metricsService.getSystemMetrics();
            return prometheusAdapter.toPrometheusFormat(metrics);
        } catch (Exception e) {
            return "# Error: " + e.getMessage();
        }
    }

    /**
     * Export custom metrics
     */
    @GetMapping(value = "/custom/{metricType}", produces = MediaType.TEXT_PLAIN_VALUE)
    public String exportCustomMetrics(@PathVariable String metricType) {
        try {
            Map<String, Object> metrics = metricsService.getCustomMetrics(metricType);
            return prometheusAdapter.toPrometheusFormat(metrics);
        } catch (Exception e) {
            return "# Error: " + e.getMessage();
        }
    }

    /**
     * Get metrics summary (JSON format)
     */
    @GetMapping(value = "/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getMetricsSummary() {
        return metricsService.getMetricsSummary();
    }
}