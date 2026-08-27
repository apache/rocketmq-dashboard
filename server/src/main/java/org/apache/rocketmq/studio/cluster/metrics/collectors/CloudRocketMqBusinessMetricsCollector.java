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
import org.apache.rocketmq.studio.ops.alert.AlertDomain;
import org.apache.rocketmq.studio.provider.InstanceProvider;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Collects cloud consumer lag using the provider's existing consumer progress API. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CloudRocketMqBusinessMetricsCollector implements BusinessMetricsCollector {
    private static final String CONSUMER_LAG_TOTAL = "consumer.lag.total";
    private static final String CONSUMER_LAG_MAX_QUEUE = "consumer.lag.max_queue";
    private static final String TOPIC_BACKLOG_TOTAL = "topic.backlog.total";

    private final InstanceProviderRegistry providerRegistry;

    @Override
    public boolean supports(InstanceVO instance) {
        return instance != null && (instance.getVendor() == InstanceVendor.ALIYUN
                || instance.getVendor() == InstanceVendor.TENCENT) && instance.getName() != null;
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
                if (group.getName() != null && !group.getName().isBlank()) {
                    samples.addAll(collectGroup(provider, instance, group, collectedAt));
                }
            }
            return samples;
        } catch (RuntimeException error) {
            log.warn("Failed to collect cloud consumer lag for instance {}: {}", instance.getName(),
                    error.getMessage());
            return List.of(unavailable(CONSUMER_LAG_TOTAL, instance, Map.of(), collectedAt),
                    unavailable(CONSUMER_LAG_MAX_QUEUE, instance, Map.of(), collectedAt),
                    unavailable(TOPIC_BACKLOG_TOTAL, instance, Map.of(), collectedAt));
        }
    }

    private static List<MetricSample> collectGroup(InstanceProvider provider, InstanceVO instance,
            ConsumerGroupVO group, Instant collectedAt) {
        Map<String, String> labels = Map.of("consumerGroup", group.getName());
        try {
            List<QueueProgressVO> progress = provider.getGroupProgress(instance.getName(), group.getName());
            double totalLag = progress.stream().mapToDouble(row -> Math.max(0L, row.getDiffTotal())).sum();
            double maxQueueLag = progress.stream().mapToDouble(row -> Math.max(0L, row.getDiffTotal())).max()
                    .orElse(0D);
            List<MetricSample> samples = new ArrayList<>();
            samples.add(available(CONSUMER_LAG_TOTAL, instance, group.getClusterId(), labels, totalLag, collectedAt));
            samples.add(available(CONSUMER_LAG_MAX_QUEUE, instance, group.getClusterId(), labels, maxQueueLag,
                    collectedAt));
            progress.stream().filter(row -> row.getTopic() != null && !row.getTopic().isBlank())
                    .collect(java.util.stream.Collectors.groupingBy(QueueProgressVO::getTopic,
                            java.util.stream.Collectors.summingLong(
                                    row -> Math.max(0, row.getDiffTotal()))))
                    .forEach((topic, lag) -> samples.add(available(TOPIC_BACKLOG_TOTAL, instance, group.getClusterId(),
                            Map.of("consumerGroup", group.getName(), "topic", topic), lag, collectedAt)));
            return samples;
        } catch (RuntimeException error) {
            log.warn("Failed to collect cloud consumer lag for group {} on instance {}: {}", group.getName(),
                    instance.getName(), error.getMessage());
            return List.of(unavailable(CONSUMER_LAG_TOTAL, instance, labels, collectedAt),
                    unavailable(CONSUMER_LAG_MAX_QUEUE, instance, labels, collectedAt),
                    unavailable(TOPIC_BACKLOG_TOTAL, instance, labels, collectedAt));
        }
    }

    private static MetricSample available(String metric, InstanceVO instance, String clusterId,
            Map<String, String> labels, double value, Instant collectedAt) {
        return new MetricSample(metric, AlertDomain.BUSINESS, instance.getName(), clusterId, labels, value,
                MetricAvailability.AVAILABLE, collectedAt);
    }

    private static MetricSample unavailable(String metric, InstanceVO instance, Map<String, String> labels,
            Instant collectedAt) {
        return new MetricSample(metric, AlertDomain.BUSINESS, instance.getName(), null, labels, null,
                MetricAvailability.UNAVAILABLE, collectedAt);
    }
}
