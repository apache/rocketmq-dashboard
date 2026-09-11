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
package org.apache.rocketmq.studio.cluster.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MetricProfileService {

    private final PrometheusProperties prometheusProperties;

    public List<MetricProfileVO> listProfiles() {
        List<MetricProfileVO> profiles = new ArrayList<>(List.of(
                profile(MetricProfile.ROCKETMQ_4_EXPORTER, rocketmq4ExporterMetrics()),
                profile(MetricProfile.ROCKETMQ_5_NATIVE, rocketmq5NativeMetrics())
        ));
        String preferredId = prometheusProperties.getProfile();
        for (int i = 1; i < profiles.size(); i++) {
            if (profiles.get(i).getId().equals(preferredId)) {
                profiles.add(0, profiles.remove(i));
                break;
            }
        }
        return List.copyOf(profiles);
    }

    public String resolvePromql(String profileId, String semanticMetric) {
        MetricProfileVO profile = listProfiles().stream()
                .filter(candidate -> candidate.getId().equals(profileId))
                .findFirst()
                .orElseThrow(() -> badRequest("Unknown metric profile: " + profileId));
        return profile.getMetrics().stream()
                .filter(metric -> metric.getSemanticMetric().equals(semanticMetric))
                .map(MetricProfileVO.MetricMappingVO::getPromql)
                .findFirst()
                .orElseThrow(() -> badRequest("Unknown semantic metric '" + semanticMetric
                        + "' for profile '" + profileId + "'"));
    }

    /**
     * Resolves the prometheus metric name for a semantic metric under the currently configured
     * profile (the one {@link #listProfiles()} orders first). Returns empty when the active profile
     * has no mapping for the semantic metric, so callers can treat it as unexportable rather than
     * falling back to a hardcoded name that would be wrong for the deployed profile (e.g. consumer
     * lag is {@code rocketmq_message_accumulation} on the 4.x exporter profile, not the 5.x name).
     */
    public Optional<String> resolveCurrentPrometheusMetric(String semanticMetric) {
        return listProfiles().get(0).getMetrics().stream()
                .filter(metric -> metric.getSemanticMetric().equals(semanticMetric))
                .map(MetricProfileVO.MetricMappingVO::getPrometheusMetric)
                .findFirst();
    }

    /**
     * Resolves the label name the active profile uses for a scope dimension of a semantic
     * metric's series — e.g. the consumer_group dimension is labeled {@code group} by the
     * 4.x exporter profile and {@code consumer_group} by the 5.x native profile. Returns
     * empty when the active profile has no mapping for the semantic metric or its series
     * are not broken out by that dimension, so callers drop the selector instead of guessing
     * a label name that would silently match an empty series set.
     */
    public Optional<String> resolveCurrentScopeLabel(String semanticMetric, String scope) {
        return listProfiles().get(0).getMetrics().stream()
                .filter(metric -> metric.getSemanticMetric().equals(semanticMetric))
                .findFirst()
                .flatMap(metric -> Optional.ofNullable(metric.getScopeLabels().get(scope)));
    }

    private MetricProfileVO profile(MetricProfile profile,
                                    List<MetricProfileVO.MetricMappingVO> metrics) {
        return MetricProfileVO.builder()
                .id(profile.getId())
                .name(profile.getDisplayName())
                .description(profile.getDescription())
                .metrics(metrics)
                .build();
    }

    private List<MetricProfileVO.MetricMappingVO> rocketmq4ExporterMetrics() {
        return List.of(
                mapping(SemanticMetric.MESSAGE_IN_TPS, "rocketmq_broker_tps",
                        "sum(rocketmq_broker_tps) by (cluster, broker)",
                        Map.of("cluster", "cluster", "broker", "broker"),
                        "cluster", "broker"),
                mapping(SemanticMetric.MESSAGE_OUT_TPS, "rocketmq_consumer_tps",
                        "sum(rocketmq_consumer_tps) by (cluster, group, topic)",
                        Map.of("cluster", "cluster", "consumer_group", "group", "topic", "topic"),
                        "cluster", "group", "topic"),
                mapping(SemanticMetric.THROUGHPUT_IN, "rocketmq_producer_message_size",
                        "sum(rocketmq_producer_message_size) by (cluster, topic)",
                        Map.of("cluster", "cluster", "topic", "topic"),
                        "cluster", "topic"),
                mapping(SemanticMetric.THROUGHPUT_OUT, "rocketmq_consumer_message_size",
                        "sum(rocketmq_consumer_message_size) by (cluster, group, topic)",
                        Map.of("cluster", "cluster", "consumer_group", "group", "topic", "topic"),
                        "cluster", "group", "topic"),
                mapping(SemanticMetric.CONSUMER_LAG_MESSAGES, "rocketmq_message_accumulation",
                        "sum(rocketmq_message_accumulation) by (cluster, group, topic)",
                        Map.of("cluster", "cluster", "consumer_group", "group", "topic", "topic"),
                        "cluster", "group", "topic"),
                mapping(SemanticMetric.CONSUMER_LAG_LATENCY, "rocketmq_group_get_latency_by_storetime",
                        "max(rocketmq_group_get_latency_by_storetime) by (cluster, group, topic)",
                        Map.of("cluster", "cluster", "consumer_group", "group", "topic", "topic"),
                        "cluster", "group", "topic"),
                mapping(SemanticMetric.BROKER_HEALTH, "up",
                        "min(up{job=~\".*rocketmq.*\"}) by (job, instance)",
                        Map.of(),
                        "job", "instance")
        );
    }

    private List<MetricProfileVO.MetricMappingVO> rocketmq5NativeMetrics() {
        return List.of(
                mapping(SemanticMetric.MESSAGE_IN_TPS, "rocketmq_messages_in_total",
                        "sum(rate(rocketmq_messages_in_total[1m])) by (cluster, node_id)",
                        Map.of("cluster", "cluster", "topic", "topic"),
                        "cluster", "node_id", "topic", "message_type"),
                mapping(SemanticMetric.MESSAGE_OUT_TPS, "rocketmq_messages_out_total",
                        "sum(rate(rocketmq_messages_out_total[1m])) by (cluster, node_id, consumer_group)",
                        Map.of("cluster", "cluster", "topic", "topic", "consumer_group", "consumer_group"),
                        "cluster", "node_id", "topic", "consumer_group"),
                mapping(SemanticMetric.THROUGHPUT_IN, "rocketmq_throughput_in_total",
                        "sum(rate(rocketmq_throughput_in_total[1m])) by (cluster, node_id)",
                        Map.of("cluster", "cluster", "topic", "topic"),
                        "cluster", "node_id", "topic", "message_type"),
                mapping(SemanticMetric.THROUGHPUT_OUT, "rocketmq_throughput_out_total",
                        "sum(rate(rocketmq_throughput_out_total[1m])) by (cluster, node_id, consumer_group)",
                        Map.of("cluster", "cluster", "topic", "topic", "consumer_group", "consumer_group"),
                        "cluster", "node_id", "topic", "consumer_group"),
                mapping(SemanticMetric.CONSUMER_LAG_MESSAGES, "rocketmq_consumer_lag_messages",
                        "sum(rocketmq_consumer_lag_messages) by (cluster, topic, consumer_group)",
                        Map.of("cluster", "cluster", "topic", "topic", "consumer_group", "consumer_group"),
                        "cluster", "topic", "consumer_group"),
                mapping(SemanticMetric.CONSUMER_LAG_LATENCY, "rocketmq_consumer_lag_latency_milliseconds",
                        "max(rocketmq_consumer_lag_latency_milliseconds) by (cluster, topic, consumer_group)",
                        Map.of("cluster", "cluster", "topic", "topic", "consumer_group", "consumer_group"),
                        "cluster", "topic", "consumer_group"),
                // Every broker reports the same cluster-level count; max avoids double counting.
                mapping(SemanticMetric.TOPIC_NUMBER, "rocketmq_topic_number",
                        "max(rocketmq_topic_number) by (cluster)",
                        Map.of("cluster", "cluster"),
                        "cluster"),
                mapping(SemanticMetric.CONSUMER_GROUP_NUMBER, "rocketmq_consumer_group_number",
                        "max(rocketmq_consumer_group_number) by (cluster)",
                        Map.of("cluster", "cluster"),
                        "cluster"),
                mapping(SemanticMetric.BROKER_HEALTH, "up",
                        "min(up{job=~\".*rocketmq.*\"}) by (job, instance)",
                        Map.of(),
                        "job", "instance")
        );
    }

    private MetricProfileVO.MetricMappingVO mapping(SemanticMetric semanticMetric, String prometheusMetric,
                                                    String promql, Map<String, String> scopeLabels,
                                                    String... labels) {
        return MetricProfileVO.MetricMappingVO.builder()
                .semanticMetric(semanticMetric.getKey())
                .name(semanticMetric.getDisplayName())
                .unit(semanticMetric.getUnit())
                .prometheusMetric(prometheusMetric)
                .promql(promql)
                .scopeLabels(scopeLabels)
                .labels(List.of(labels))
                .build();
    }

    private PrometheusException badRequest(String message) {
        return new PrometheusException(HttpStatus.BAD_REQUEST.value(), message);
    }
}
