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
package org.apache.rocketmq.studio.ops.alert;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;

/** Enforces the scope required to safely evaluate Studio-native metric rules. */
final class NativeAlertRulePolicy {
    private static final Map<String, AlertDomain> NATIVE_METRICS = Map.ofEntries(
            Map.entry("nameserver.availability", AlertDomain.CLUSTER),
            Map.entry("broker.availability", AlertDomain.CLUSTER),
            Map.entry("proxy.availability", AlertDomain.CLUSTER),
            Map.entry("cloud.instance.availability", AlertDomain.CLUSTER),
            Map.entry("broker.disk.usage_ratio", AlertDomain.CLUSTER),
            Map.entry("broker.jvm.heap.usage_ratio", AlertDomain.CLUSTER),
            Map.entry("broker.send_queue.usage_ratio", AlertDomain.CLUSTER),
            Map.entry("consumer.lag.total", AlertDomain.BUSINESS),
            Map.entry("consumer.lag.max_queue", AlertDomain.BUSINESS),
            Map.entry("consumer.delay.seconds", AlertDomain.BUSINESS),
            Map.entry("topic.backlog.total", AlertDomain.BUSINESS),
            Map.entry("dlq.message.count", AlertDomain.BUSINESS));
    private static final Set<String> GROUP_SCOPED_METRICS = Set.of(
            "consumer.lag.total", "consumer.lag.max_queue", "consumer.delay.seconds", "topic.backlog.total",
            "dlq.message.count");
    private static final Set<String> TOPIC_SCOPED_METRICS = Set.of("topic.backlog.total");
    private static final Set<String> AVAILABILITY_METRICS = Set.of(
            "nameserver.availability", "broker.availability", "proxy.availability", "cloud.instance.availability");
    private static final Set<String> NOTIFICATION_CHANNELS = Set.of("dingtalk", "sms", "email");

    private NativeAlertRulePolicy() {
    }

    static void validate(AlertRuleVO rule) {
        validateChannels(rule);
        if (!StringUtils.hasText(rule.getMetric())) {
            return;
        }
        AlertDomain metricDomain = NATIVE_METRICS.get(rule.getMetric());
        if (metricDomain == null) {
            return;
        }
        if (rule.getDomain() != metricDomain) {
            throw new BusinessException(400, "Native metric " + rule.getMetric()
                    + " belongs to the " + metricDomain + " alert domain");
        }
        if (!StringUtils.hasText(rule.getInstanceId())) {
            throw new BusinessException(400, "instanceId is required for native alert rules");
        }
        if ("UNAVAILABLE".equals(rule.getOperator()) && !AVAILABILITY_METRICS.contains(rule.getMetric())) {
            throw new BusinessException(400, "UNAVAILABLE is only supported for native availability metrics");
        }
        if (StringUtils.hasText(rule.getConsumerGroup()) && !GROUP_SCOPED_METRICS.contains(rule.getMetric())) {
            throw new BusinessException(400, "consumerGroup is not supported for metric " + rule.getMetric());
        }
        if (StringUtils.hasText(rule.getTopic()) && !TOPIC_SCOPED_METRICS.contains(rule.getMetric())) {
            throw new BusinessException(400, "topic is not supported for metric " + rule.getMetric());
        }
        if (rule.getConsecutiveSamples() < 1) {
            throw new BusinessException(400, "consecutiveSamples must be at least 1");
        }
    }

    private static void validateChannels(AlertRuleVO rule) {
        if (rule.getChannels() == null) {
            return;
        }
        for (String channel : rule.getChannels()) {
            if (!StringUtils.hasText(channel) || !NOTIFICATION_CHANNELS.contains(channel.trim().toLowerCase())) {
                throw new BusinessException(400, "Unsupported notification channel: " + channel);
            }
        }
    }
}
