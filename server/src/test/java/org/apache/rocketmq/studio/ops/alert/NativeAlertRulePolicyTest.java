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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeAlertRulePolicyTest {

    private AlertRuleVO validNativeRule() {
        return AlertRuleVO.builder()
            .domain(AlertDomain.CLUSTER)
            .metric("broker.availability")
            .operator("UNAVAILABLE")
            .threshold(0)
            .duration("5m")
            .instanceId("inst-1")
            .channels(List.of("email"))
            .build();
    }

    private BusinessException rejectionOf(AlertRuleVO rule) {
        return assertThrows(BusinessException.class, () -> NativeAlertRulePolicy.validate(rule));
    }

    @Test
    void acceptsValidNativeAvailabilityRule() {
        assertDoesNotThrow(() -> NativeAlertRulePolicy.validate(validNativeRule()));
    }

    @Test
    void unknownMetricSkipsNativeChecks() {
        AlertRuleVO rule = AlertRuleVO.builder()
            .domain(AlertDomain.BUSINESS)
            .metric("custom.app.latency")
            .operator(">")
            .threshold(200)
            .duration("5m")
            .channels(List.of("email"))
            .build();

        assertDoesNotThrow(() -> NativeAlertRulePolicy.validate(rule));
    }

    @Test
    void rejectsUnsupportedChannel() {
        AlertRuleVO rule = validNativeRule();
        rule.setChannels(List.of("slack"));

        BusinessException ex = rejectionOf(rule);

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("Unsupported notification channel"));
    }

    @Test
    void rejectsDomainMismatchForNativeMetric() {
        AlertRuleVO rule = validNativeRule();
        rule.setDomain(AlertDomain.BUSINESS);

        BusinessException ex = rejectionOf(rule);

        assertTrue(ex.getMessage().contains("belongs to the CLUSTER alert domain"));
    }

    @Test
    void rejectsMissingInstanceIdForNativeMetric() {
        AlertRuleVO rule = validNativeRule();
        rule.setInstanceId(null);

        BusinessException ex = rejectionOf(rule);

        assertTrue(ex.getMessage().contains("instanceId is required for native alert rules"));
    }

    @Test
    void rejectsUnavailableOperatorOnNonAvailabilityMetric() {
        AlertRuleVO rule = AlertRuleVO.builder()
            .domain(AlertDomain.CLUSTER)
            .metric("broker.disk.usage_ratio")
            .operator("UNAVAILABLE")
            .threshold(0)
            .duration("5m")
            .instanceId("inst-1")
            .build();

        BusinessException ex = rejectionOf(rule);

        assertTrue(ex.getMessage().contains("UNAVAILABLE is only supported for native availability metrics"));
    }

    @Test
    void rejectsConsumerGroupOnNonGroupScopedMetric() {
        AlertRuleVO rule = AlertRuleVO.builder()
            .domain(AlertDomain.CLUSTER)
            .metric("broker.disk.usage_ratio")
            .operator(">")
            .threshold(0.8)
            .duration("5m")
            .instanceId("inst-1")
            .consumerGroup("cg-1")
            .build();

        BusinessException ex = rejectionOf(rule);

        assertTrue(ex.getMessage().contains("consumerGroup is not supported for metric"));
    }

    @Test
    void rejectsTopicOnNonTopicScopedMetric() {
        AlertRuleVO rule = AlertRuleVO.builder()
            .domain(AlertDomain.CLUSTER)
            .metric("broker.disk.usage_ratio")
            .operator(">")
            .threshold(0.8)
            .duration("5m")
            .instanceId("inst-1")
            .topic("order-topic")
            .build();

        BusinessException ex = rejectionOf(rule);

        assertTrue(ex.getMessage().contains("topic is not supported for metric"));
    }

    @Test
    void acceptsConsumerLagRuleWithGroupScopedChecks() {
        AlertRuleVO rule = AlertRuleVO.builder()
            .domain(AlertDomain.BUSINESS)
            .metric("consumer.lag.total")
            .operator(">")
            .threshold(1000)
            .duration("5m")
            .instanceId("inst-1")
            .consumerGroup("cg-1")
            .build();

        assertDoesNotThrow(() -> NativeAlertRulePolicy.validate(rule));
    }

    @Test
    void rejectsMalformedDuration() {
        AlertRuleVO rule = validNativeRule();
        rule.setDuration("5x");

        BusinessException ex = rejectionOf(rule);

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("Invalid alert duration"));
    }
}
