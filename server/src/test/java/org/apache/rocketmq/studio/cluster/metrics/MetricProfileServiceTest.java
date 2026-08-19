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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class MetricProfileServiceTest {

    private final MetricProfileService service = new MetricProfileService();

    @Test
    void listProfilesShouldExposeRocketmq4And5Mappings() {
        Map<String, MetricProfileVO> profiles = service.listProfiles().stream()
                .collect(Collectors.toMap(MetricProfileVO::getId, Function.identity()));

        assertThat(profiles.keySet()).containsExactlyInAnyOrder("rocketmq4-exporter", "rocketmq5-native");
        assertThat(semanticMetrics(profiles.get("rocketmq4-exporter")))
                .containsExactlyInAnyOrderElementsOf(allSemanticMetricKeys());
        assertThat(semanticMetrics(profiles.get("rocketmq5-native")))
                .containsExactlyInAnyOrderElementsOf(allSemanticMetricKeys());
    }

    @Test
    void rocketmq5ProfileShouldUseNativeMetricNames() {
        MetricProfileVO profile = findProfile("rocketmq5-native");

        assertThat(mapping(profile, SemanticMetric.MESSAGE_IN_TPS))
                .extracting(MetricProfileVO.MetricMappingVO::getPrometheusMetric,
                        MetricProfileVO.MetricMappingVO::getPromql)
                .containsExactly("rocketmq_messages_in_total",
                        "sum(rate(rocketmq_messages_in_total[1m])) by (cluster, node_id)");
        assertThat(mapping(profile, SemanticMetric.CONSUMER_LAG_MESSAGES).getPrometheusMetric())
                .isEqualTo("rocketmq_consumer_lag_messages");
        assertThat(mapping(profile, SemanticMetric.BROKER_HEALTH).getPrometheusMetric())
                .isEqualTo("up");
        assertThat(mapping(profile, SemanticMetric.BROKER_HEALTH).getPromql())
                .isEqualTo("max(up) by (cluster, node_id)");
        assertThat(mapping(profile, SemanticMetric.BROKER_HEALTH).getLabels())
                .containsExactly("cluster", "node_id");
    }

    @Test
    void rocketmq4ProfileShouldUseExporterMetricNames() {
        MetricProfileVO profile = findProfile("rocketmq4-exporter");

        assertThat(mapping(profile, SemanticMetric.MESSAGE_IN_TPS).getPrometheusMetric())
                .isEqualTo("rocketmq_broker_tps");
        assertThat(mapping(profile, SemanticMetric.THROUGHPUT_IN).getPrometheusMetric())
                .isEqualTo("rocketmq_producer_message_size");
        assertThat(mapping(profile, SemanticMetric.THROUGHPUT_OUT).getPrometheusMetric())
                .isEqualTo("rocketmq_consumer_message_size");
        assertThat(mapping(profile, SemanticMetric.CONSUMER_LAG_MESSAGES).getPrometheusMetric())
                .isEqualTo("rocketmq_message_accumulation");
        assertThat(mapping(profile, SemanticMetric.CONSUMER_LAG_LATENCY).getPrometheusMetric())
                .isEqualTo("rocketmq_group_get_latency_by_storetime");
    }

    @Test
    void mappingsShouldExposeLabelsAndUnitsForDashboardRendering() {
        MetricProfileVO profile = findProfile("rocketmq5-native");
        MetricProfileVO.MetricMappingVO messageOut = mapping(profile, SemanticMetric.MESSAGE_OUT_TPS);

        assertThat(messageOut.getLabels()).contains("cluster", "node_id", "topic", "consumer_group");
        assertThat(messageOut.getUnit()).isEqualTo("messages/s");
        assertThat(messageOut.getName()).isEqualTo("Message Out TPS");
    }

    @Test
    void resolvePromqlShouldReturnVersionSpecificMapping() {
        assertThat(service.resolvePromql("rocketmq4-exporter", "consumer_lag_messages"))
                .isEqualTo("sum(rocketmq_message_accumulation) by (cluster, group, topic)");
        assertThat(service.resolvePromql("rocketmq5-native", "consumer_lag_messages"))
                .isEqualTo("sum(rocketmq_consumer_lag_messages) by (cluster, topic, consumer_group)");
    }

    @Test
    void resolvePromqlShouldRejectUnknownProfile() {
        assertBadRequest(() -> service.resolvePromql("rocketmq6-native", "message_in_tps"),
                "Unknown metric profile: rocketmq6-native");
    }

    @Test
    void resolvePromqlShouldRejectUnknownSemanticMetric() {
        assertBadRequest(() -> service.resolvePromql("rocketmq5-native", "queue_depth"),
                "Unknown semantic metric 'queue_depth' for profile 'rocketmq5-native'");
    }

    private MetricProfileVO findProfile(String id) {
        return service.listProfiles().stream()
                .filter(profile -> profile.getId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private MetricProfileVO.MetricMappingVO mapping(MetricProfileVO profile, SemanticMetric semanticMetric) {
        return profile.getMetrics().stream()
                .filter(metric -> metric.getSemanticMetric().equals(semanticMetric.getKey()))
                .findFirst()
                .orElseThrow();
    }

    private List<String> semanticMetrics(MetricProfileVO profile) {
        return profile.getMetrics().stream()
                .map(MetricProfileVO.MetricMappingVO::getSemanticMetric)
                .toList();
    }

    private List<String> allSemanticMetricKeys() {
        return List.of(SemanticMetric.values()).stream()
                .map(SemanticMetric::getKey)
                .toList();
    }

    private void assertBadRequest(Runnable action, String message) {
        assertThatExceptionOfType(PrometheusException.class)
                .isThrownBy(action::run)
                .satisfies(exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(400);
                    assertThat(exception.getMessage()).isEqualTo(message);
                });
    }
}
