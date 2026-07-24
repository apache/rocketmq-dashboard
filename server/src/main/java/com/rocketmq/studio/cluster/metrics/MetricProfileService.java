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
package com.rocketmq.studio.cluster.metrics;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MetricProfileService {

    public List<MetricProfileVO> listProfiles() {
        return List.of(
                profile(MetricProfile.ROCKETMQ_4_EXPORTER, rocketmq4ExporterMetrics()),
                profile(MetricProfile.ROCKETMQ_5_NATIVE, rocketmq5NativeMetrics())
        );
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
                        "cluster", "broker"),
                mapping(SemanticMetric.MESSAGE_OUT_TPS, "rocketmq_consumer_tps",
                        "sum(rocketmq_consumer_tps) by (cluster, group, topic)",
                        "cluster", "group", "topic"),
                mapping(SemanticMetric.THROUGHPUT_IN, "rocketmq_producer_message_size",
                        "sum(rocketmq_producer_message_size) by (cluster, topic)",
                        "cluster", "topic"),
                mapping(SemanticMetric.THROUGHPUT_OUT, "rocketmq_consumer_message_size",
                        "sum(rocketmq_consumer_message_size) by (cluster, group, topic)",
                        "cluster", "group", "topic"),
                mapping(SemanticMetric.CONSUMER_LAG_MESSAGES, "rocketmq_message_accumulation",
                        "sum(rocketmq_message_accumulation) by (cluster, group, topic)",
                        "cluster", "group", "topic"),
                mapping(SemanticMetric.CONSUMER_LAG_LATENCY, "rocketmq_group_get_latency_by_storetime",
                        "max(rocketmq_group_get_latency_by_storetime) by (cluster, group, topic)",
                        "cluster", "group", "topic"),
                mapping(SemanticMetric.BROKER_HEALTH, "up",
                        "min(up{job=~\".*rocketmq.*\"}) by (job, instance)",
                        "job", "instance")
        );
    }

    private List<MetricProfileVO.MetricMappingVO> rocketmq5NativeMetrics() {
        return List.of(
                mapping(SemanticMetric.MESSAGE_IN_TPS, "rocketmq_messages_in_total",
                        "sum(rate(rocketmq_messages_in_total[1m])) by (cluster, node_id)",
                        "cluster", "node_id", "topic", "message_type"),
                mapping(SemanticMetric.MESSAGE_OUT_TPS, "rocketmq_messages_out_total",
                        "sum(rate(rocketmq_messages_out_total[1m])) by (cluster, node_id, consumer_group)",
                        "cluster", "node_id", "topic", "consumer_group"),
                mapping(SemanticMetric.THROUGHPUT_IN, "rocketmq_throughput_in_total",
                        "sum(rate(rocketmq_throughput_in_total[1m])) by (cluster, node_id)",
                        "cluster", "node_id", "topic", "message_type"),
                mapping(SemanticMetric.THROUGHPUT_OUT, "rocketmq_throughput_out_total",
                        "sum(rate(rocketmq_throughput_out_total[1m])) by (cluster, node_id, consumer_group)",
                        "cluster", "node_id", "topic", "consumer_group"),
                mapping(SemanticMetric.CONSUMER_LAG_MESSAGES, "rocketmq_consumer_lag_messages",
                        "sum(rocketmq_consumer_lag_messages) by (cluster, topic, consumer_group)",
                        "cluster", "topic", "consumer_group"),
                mapping(SemanticMetric.CONSUMER_LAG_LATENCY, "rocketmq_consumer_lag_latency",
                        "max(rocketmq_consumer_lag_latency) by (cluster, topic, consumer_group)",
                        "cluster", "topic", "consumer_group"),
                mapping(SemanticMetric.BROKER_HEALTH, "rocketmq_processor_watermark",
                        "max(rocketmq_processor_watermark) by (cluster, node_id, processor)",
                        "cluster", "node_id", "processor")
        );
    }

    private MetricProfileVO.MetricMappingVO mapping(SemanticMetric semanticMetric, String prometheusMetric,
                                                    String promql, String... labels) {
        return MetricProfileVO.MetricMappingVO.builder()
                .semanticMetric(semanticMetric.getKey())
                .name(semanticMetric.getDisplayName())
                .unit(semanticMetric.getUnit())
                .prometheusMetric(prometheusMetric)
                .promql(promql)
                .labels(List.of(labels))
                .build();
    }
}
