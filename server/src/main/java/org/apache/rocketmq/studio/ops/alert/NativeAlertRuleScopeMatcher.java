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

import org.apache.rocketmq.studio.cluster.metrics.MetricSample;
import org.springframework.util.StringUtils;

/** Matches native samples against the instance and optional resource selectors on a rule. */
final class NativeAlertRuleScopeMatcher {
    private NativeAlertRuleScopeMatcher() {
    }

    static boolean matches(AlertRuleVO rule, MetricSample sample) {
        if (!StringUtils.hasText(rule.getInstanceId())
                || !rule.getInstanceId().trim().equals(sample.instanceId())) {
            return false;
        }
        String consumerGroup = sample.labels() == null ? null : sample.labels().get("consumerGroup");
        String brokerName = sample.labels() == null ? null : sample.labels().get("brokerName");
        String topic = sample.labels() == null ? null : sample.labels().get("topic");
        return matchesSelector(rule.getConsumerGroup(), consumerGroup)
                && matchesSelector(rule.getTopic(), topic)
                && matchesSelector(rule.getBrokerName(), brokerName)
                && matchesSelector(rule.getClusterName(), sample.clusterId());
    }

    private static boolean matchesSelector(String selector, String value) {
        return !StringUtils.hasText(selector) || "*".equals(selector.trim())
                || selector.trim().equals(value);
    }
}
