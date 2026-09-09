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

import org.apache.rocketmq.studio.cluster.metrics.MetricProfileService;
import org.apache.rocketmq.studio.cluster.metrics.MetricProfileVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricProfileListToolHandlerTest {

    @Mock
    private MetricProfileService metricProfileService;

    @InjectMocks
    private MetricProfileListToolHandler handler;

    @Test
    @SuppressWarnings("unchecked")
    void executeShouldDelegateToMetricProfileServiceAndProjectProfiles() {
        when(metricProfileService.listProfiles()).thenReturn(List.of(profile()));

        Object result = handler.execute(Map.of("cluster", "cluster-v5"));

        assertThat(result).isInstanceOf(List.class);
        List<Map<String, Object>> profiles = (List<Map<String, Object>>) result;
        assertThat(profiles).hasSize(1);
        Map<String, Object> profile = profiles.get(0);
        assertThat(profile).containsEntry("id", "rocketmq5-native");
        assertThat(profile).containsEntry("name", "RocketMQ 5.x Native");
        assertThat(profile).containsEntry("description", "native metrics");

        List<Map<String, Object>> metrics = (List<Map<String, Object>>) profile.get("metrics");
        assertThat(metrics).hasSize(1);
        assertThat(metrics.get(0)).containsAllEntriesOf(Map.of(
                "semanticMetric", "message_in_tps",
                "name", "Message In TPS",
                "unit", "messages/s",
                "prometheusMetric", "rocketmq_messages_in_total",
                "promql", "sum(rate(rocketmq_messages_in_total[1m])) by (cluster, node_id)"));
        assertThat(metrics.get(0)).containsEntry("labels", List.of("cluster", "node_id"));
        verify(metricProfileService).listProfiles();
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeShouldReturnEmptyMetricListWhenProfileHasNoMetrics() {
        when(metricProfileService.listProfiles()).thenReturn(List.of(MetricProfileVO.builder()
                .id("empty")
                .name("Empty")
                .description(null)
                .metrics(null)
                .build()));

        Object result = handler.execute(Map.of("cluster", "cluster-v5"));

        List<Map<String, Object>> profiles = (List<Map<String, Object>>) result;
        assertThat(profiles.get(0)).containsEntry("description", "");
        assertThat(profiles.get(0)).containsEntry("metrics", List.of());
    }

    private static MetricProfileVO profile() {
        return MetricProfileVO.builder()
                .id("rocketmq5-native")
                .name("RocketMQ 5.x Native")
                .description("native metrics")
                .metrics(List.of(MetricProfileVO.MetricMappingVO.builder()
                        .semanticMetric("message_in_tps")
                        .name("Message In TPS")
                        .unit("messages/s")
                        .prometheusMetric("rocketmq_messages_in_total")
                        .promql("sum(rate(rocketmq_messages_in_total[1m])) by (cluster, node_id)")
                        .labels(List.of("cluster", "node_id"))
                        .build()))
                .build();
    }
}
