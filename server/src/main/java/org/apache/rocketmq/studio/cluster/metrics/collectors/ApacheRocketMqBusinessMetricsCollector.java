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
package org.apache.rocketmq.studio.cluster.metrics.collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.cluster.metrics.BusinessMetricsCollector;
import org.apache.rocketmq.studio.cluster.metrics.MetricAvailability;
import org.apache.rocketmq.studio.cluster.metrics.MetricSample;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.apache.rocketmq.studio.provider.InstanceProvider;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.apache.rocketmq.studio.ops.alert.AlertDomain;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Collects consumer-group lag directly from Studio's Apache instance provider. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApacheRocketMqBusinessMetricsCollector implements BusinessMetricsCollector {
    public static final String CONSUMER_LAG_TOTAL = "consumer.lag.total";
    public static final String CONSUMER_LAG_MAX_QUEUE = "consumer.lag.max_queue";
    public static final String CONSUMER_DELAY_SECONDS = "consumer.delay.seconds";
    public static final String TOPIC_BACKLOG_TOTAL = "topic.backlog.total";

    private final InstanceProviderRegistry providerRegistry;

    @Override
    public boolean supports(InstanceVO instance) {
        return instance != null && (instance.getVendor() == null || instance.getVendor() == InstanceVendor.APACHE)
                && instance.getName() != null;
    }

    @Override
    public Set<String> metricKeys() {
        return Set.of(CONSUMER_LAG_TOTAL, CONSUMER_LAG_MAX_QUEUE, CONSUMER_DELAY_SECONDS, TOPIC_BACKLOG_TOTAL);
    }

    @Override
    public List<MetricSample> collect(InstanceVO instance) {
        if (!supports(instance)) {
            return List.of();
        }
        Instant collectedAt = Instant.now();
        try {
            InstanceProvider provider = providerRegistry.byInstanceId(instance.getName()).orElseThrow();
            List<MetricSample> samples = new ArrayList<>();
            for (ConsumerGroupVO group : provider.listConsumerGroups(instance.getName(), null)) {
                if (group.getName() == null || group.getName().isBlank()) {
                    continue;
                }
                if (!group.isConsumeStatsAvailable()) {
                    Map<String, String> labels = Map.of("consumerGroup", group.getName());
                    samples.add(unavailable(CONSUMER_LAG_TOTAL, instance, labels, collectedAt,
                            "CONSUMER_STATS_UNAVAILABLE"));
                    samples.add(unavailable(CONSUMER_LAG_MAX_QUEUE, instance, labels, collectedAt,
                            "CONSUMER_STATS_UNAVAILABLE"));
                    samples.add(unavailable(CONSUMER_DELAY_SECONDS, instance, labels, collectedAt,
                            "CONSUMER_STATS_UNAVAILABLE"));
                    samples.add(unavailable(TOPIC_BACKLOG_TOTAL, instance, labels, collectedAt,
                            "CONSUMER_STATS_UNAVAILABLE"));
                    continue;
                }
                samples.add(totalLagSample(instance, group, collectedAt));
                if (group.isConsumptionTimestampAvailable()) {
                    samples.add(delaySample(instance, group, collectedAt));
                }
                samples.addAll(queueLagSamples(provider, instance, group, collectedAt));
            }
            return samples;
        } catch (RuntimeException error) {
            log.warn("Failed to collect consumer lag for instance {}: {}", instance.getName(), error.getMessage());
            return List.of(unavailable(CONSUMER_LAG_TOTAL, instance, Map.of(), collectedAt,
                    "BUSINESS_METRICS_COLLECTION_FAILED"),
                    unavailable(CONSUMER_LAG_MAX_QUEUE, instance, Map.of(), collectedAt,
                            "BUSINESS_METRICS_COLLECTION_FAILED"),
                    unavailable(CONSUMER_DELAY_SECONDS, instance, Map.of(), collectedAt,
                            "BUSINESS_METRICS_COLLECTION_FAILED"),
                    unavailable(TOPIC_BACKLOG_TOTAL, instance, Map.of(), collectedAt,
                            "BUSINESS_METRICS_COLLECTION_FAILED"));
        }
    }

    private static MetricSample totalLagSample(InstanceVO instance, ConsumerGroupVO group, Instant collectedAt) {
        return new MetricSample(CONSUMER_LAG_TOTAL, AlertDomain.BUSINESS, instance.getName(), group.getClusterId(),
                Map.of("consumerGroup", group.getName()), (double) Math.max(0, group.getTotalLag()),
                MetricAvailability.AVAILABLE, collectedAt);
    }

    private static MetricSample delaySample(InstanceVO instance, ConsumerGroupVO group, Instant collectedAt) {
        return new MetricSample(CONSUMER_DELAY_SECONDS, AlertDomain.BUSINESS, instance.getName(), group.getClusterId(),
                Map.of("consumerGroup", group.getName()), (double) Math.max(0, group.getDelaySeconds()),
                MetricAvailability.AVAILABLE, collectedAt);
    }

    private static List<MetricSample> queueLagSamples(InstanceProvider provider, InstanceVO instance,
            ConsumerGroupVO group, Instant collectedAt) {
        Map<String, String> labels = Map.of("consumerGroup", group.getName());
        try {
            List<QueueProgressVO> progress = provider.getGroupProgress(instance.getName(), group.getName());
            long maxLag = progress.stream().mapToLong(QueueProgressVO::getDiffTotal)
                    .map(value -> Math.max(0, value)).max().orElse(0);
            List<MetricSample> samples = new ArrayList<>();
            samples.add(new MetricSample(CONSUMER_LAG_MAX_QUEUE, AlertDomain.BUSINESS, instance.getName(),
                    group.getClusterId(), labels, (double) maxLag, MetricAvailability.AVAILABLE, collectedAt));
            progress.stream().filter(row -> row.getTopic() != null && !row.getTopic().isBlank())
                    .collect(java.util.stream.Collectors.groupingBy(QueueProgressVO::getTopic,
                            java.util.stream.Collectors.summingLong(
                                    row -> Math.max(0, row.getDiffTotal()))))
                    .forEach((topic, lag) -> samples.add(new MetricSample(TOPIC_BACKLOG_TOTAL, AlertDomain.BUSINESS,
                            instance.getName(), group.getClusterId(), Map.of("consumerGroup", group.getName(),
                            "topic", topic), (double) lag, MetricAvailability.AVAILABLE, collectedAt)));
            return samples;
        } catch (RuntimeException error) {
            log.warn("Failed to collect queue lag for group {} on instance {}: {}", group.getName(),
                    instance.getName(), error.getMessage());
            return List.of(unavailable(CONSUMER_LAG_MAX_QUEUE, instance, labels, collectedAt,
                    "CONSUMER_PROGRESS_UNAVAILABLE"),
                    unavailable(TOPIC_BACKLOG_TOTAL, instance, labels, collectedAt,
                            "CONSUMER_PROGRESS_UNAVAILABLE"));
        }
    }

    private static MetricSample unavailable(String metric, InstanceVO instance, Map<String, String> labels,
            Instant collectedAt, String reason) {
        return new MetricSample(metric, AlertDomain.BUSINESS, instance.getName(), null, labels, null,
                MetricAvailability.UNAVAILABLE, collectedAt, reason);
    }
}
