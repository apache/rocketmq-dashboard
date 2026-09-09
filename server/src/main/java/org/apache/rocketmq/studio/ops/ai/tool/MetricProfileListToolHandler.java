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
package org.apache.rocketmq.studio.ops.ai.tool;

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.cluster.metrics.MetricProfileService;
import org.apache.rocketmq.studio.cluster.metrics.MetricProfileVO;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MetricProfileListToolHandler implements ToolHandler {

    private static final String NAME = "rmq.metrics.profile.list";

    private final MetricProfileService metricProfileService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Object execute(Map<String, Object> input) {
        return metricProfileService.listProfiles().stream()
                .map(MetricProfileListToolHandler::profileProjection)
                .toList();
    }

    private static Map<String, Object> profileProjection(MetricProfileVO profile) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", require(profile.getId(), "profile id"));
        result.put("name", require(profile.getName(), "profile name"));
        result.put("description", blankIfNull(profile.getDescription()));
        result.put("metrics", metrics(profile.getMetrics()));
        return result;
    }

    private static List<Map<String, Object>> metrics(List<MetricProfileVO.MetricMappingVO> metrics) {
        if (metrics == null) {
            return List.of();
        }
        return metrics.stream()
                .map(MetricProfileListToolHandler::metricProjection)
                .toList();
    }

    private static Map<String, Object> metricProjection(MetricProfileVO.MetricMappingVO metric) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("semanticMetric", require(metric.getSemanticMetric(), "semantic metric"));
        result.put("name", require(metric.getName(), "metric name"));
        result.put("unit", blankIfNull(metric.getUnit()));
        result.put("prometheusMetric", require(metric.getPrometheusMetric(), "prometheus metric"));
        result.put("promql", require(metric.getPromql(), "promql"));
        result.put("labels", metric.getLabels() == null ? List.of() : List.copyOf(metric.getLabels()));
        return result;
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Metric " + field + " is unavailable");
        }
        return value;
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
