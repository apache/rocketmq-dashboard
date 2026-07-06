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
package org.apache.rocketmq.dashboard.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Prometheus metrics adapter
 * Converts internal metrics format to Prometheus exposition format
 */
@Component
public class PrometheusMetricsAdapter {

    private static final Logger log = LoggerFactory.getLogger(PrometheusMetricsAdapter.class);

    private static final String HELP_PREFIX = "# HELP ";
    private static final String TYPE_PREFIX = "# TYPE ";
    private static final String METRIC_SEPARATOR = "\n";

    /**
     * Convert metrics map to Prometheus format
     */
    public String toPrometheusFormat(Map<String, Object> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return "";
        }

        StringBuilder prometheus = new StringBuilder();
        
        try {
            for (Map.Entry<String, Object> entry : metrics.entrySet()) {
                String metricName = sanitizeMetricName(entry.getKey());
                Object value = entry.getValue();
                
                if (value instanceof Number) {
                    prometheus.append(metricName)
                              .append(" ")
                              .append(value)
                              .append(METRIC_SEPARATOR);
                } else if (value instanceof Map) {
                    appendMetricWithLabels(prometheus, metricName, (Map<String, Object>) value);
                } else {
                    prometheus.append(metricName)
                              .append(" ")
                              .append(value.toString())
                              .append(METRIC_SEPARATOR);
                }
            }
        } catch (Exception e) {
            log.error("Error converting metrics to Prometheus format", e);
            return "";
        }

        return prometheus.toString();
    }

    /**
     * Convert multiple metrics to Prometheus format
     */
    public String toPrometheusFormat(List<Map<String, Object>> metricsList) {
        if (metricsList == null || metricsList.isEmpty()) {
            return "";
        }
        return metricsList.stream()
            .map(this::toPrometheusFormat)
            .collect(Collectors.joining(METRIC_SEPARATOR));
    }

    /**
     * Generate metric family definition
     */
    public String generateMetricFamily(String metricName, String type, String help) {
        StringBuilder family = new StringBuilder();
        family.append(HELP_PREFIX).append(metricName).append(" ").append(help).append(METRIC_SEPARATOR);
        family.append(TYPE_PREFIX).append(metricName).append(" ").append(type).append(METRIC_SEPARATOR);
        return family.toString();
    }

    /**
     * Generate complete metrics export with headers
     */
    public String generateFullMetricsExport(Map<String, Object> metrics) {
        StringBuilder output = new StringBuilder();
        output.append("# RocketMQ Dashboard Metrics\n");
        output.append("# Generated at: ").append(System.currentTimeMillis()).append("\n\n");
        output.append(toPrometheusFormat(metrics));
        return output.toString();
    }

    /**
     * Append metric with labels
     */
    private void appendMetricWithLabels(StringBuilder prometheus, String metricName, Map<String, Object> metricData) {
        metricData.forEach((labelKey, labelValue) -> {
            String labels = "{" + sanitizeMetricName(labelKey) + "=\"" + sanitizeLabelValue(labelValue.toString()) + "\"}";
            prometheus.append(metricName).append(labels).append(" 1").append(METRIC_SEPARATOR);
        });
    }

    /**
     * Sanitize metric name for Prometheus format
     */
    private String sanitizeMetricName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_:]", "_").toLowerCase();
    }

    /**
     * Sanitize label value for Prometheus format
     */
    private String sanitizeLabelValue(String value) {
        return value.replaceAll("[\"\\\\]", "_").replaceAll("\n", "_");
    }
}