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
import org.apache.rocketmq.studio.instance.dlq.DLQGroupVO;
import org.apache.rocketmq.studio.instance.dlq.DLQProvider;
import org.apache.rocketmq.studio.ops.alert.AlertDomain;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Collects dead-letter message counts directly from the Apache DLQ provider. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApacheRocketMqDlqMetricsCollector implements BusinessMetricsCollector {
    public static final String DLQ_MESSAGE_COUNT = "dlq.message.count";

    private final DLQProvider dlqProvider;

    @Override
    public boolean supports(InstanceVO instance) {
        return instance != null && (instance.getVendor() == null || instance.getVendor() == InstanceVendor.APACHE)
                && instance.getName() != null;
    }

    @Override
    public List<MetricSample> collect(InstanceVO instance) {
        if (!supports(instance)) {
            return List.of();
        }
        Instant collectedAt = Instant.now();
        try {
            return dlqProvider.listDLQGroups(instance.getName()).stream()
                    .filter(group -> group.getGroupName() != null && !group.getGroupName().isBlank())
                    .map(group -> sample(instance, group, collectedAt)).toList();
        } catch (RuntimeException error) {
            log.warn("Failed to collect DLQ metrics for instance {}: {}", instance.getName(), error.getMessage());
            return List.of(new MetricSample(DLQ_MESSAGE_COUNT, AlertDomain.BUSINESS, instance.getName(), null,
                    Map.of(), null, MetricAvailability.UNAVAILABLE, collectedAt));
        }
    }

    private static MetricSample sample(InstanceVO instance, DLQGroupVO group, Instant collectedAt) {
        Map<String, String> labels = Map.of("consumerGroup", group.getGroupName());
        if (!group.isStatsAvailable()) {
            return new MetricSample(DLQ_MESSAGE_COUNT, AlertDomain.BUSINESS, instance.getName(), null, labels,
                    null, MetricAvailability.UNAVAILABLE, collectedAt);
        }
        return new MetricSample(DLQ_MESSAGE_COUNT, AlertDomain.BUSINESS, instance.getName(), null, labels,
                (double) Math.max(0, group.getMessageCount()), MetricAvailability.AVAILABLE, collectedAt);
    }
}
