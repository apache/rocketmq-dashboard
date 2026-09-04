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
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NativeAlertRulePolicyTest {

    @Test
    void acceptsScopedBusinessRuleTest() {
        assertThatCode(() -> NativeAlertRulePolicy.validate(rule(AlertDomain.BUSINESS, "consumer.lag.total")
                .instanceId("local").consumerGroup("orders").consecutiveSamples(2).build()))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsConsumerDelayForAConsumerGroupTest() {
        assertThatCode(() -> NativeAlertRulePolicy.validate(rule(AlertDomain.BUSINESS, "consumer.delay.seconds")
                .instanceId("local").consumerGroup("orders").build())).doesNotThrowAnyException();
    }

    @Test
    void acceptsTopicSelectorOnlyForTopicBacklogTest() {
        assertThatCode(() -> NativeAlertRulePolicy.validate(rule(AlertDomain.BUSINESS, "topic.backlog.total")
                .instanceId("local").consumerGroup("orders").topic("orders-topic").build()))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> NativeAlertRulePolicy.validate(rule(AlertDomain.BUSINESS, "consumer.lag.total")
                .instanceId("local").topic("orders-topic").build()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("topic is not supported");
    }

    @Test
    void rejectsNativeRuleWithoutInstanceScopeTest() {
        assertThatThrownBy(() -> NativeAlertRulePolicy.validate(rule(AlertDomain.CLUSTER, "broker.availability")
                .build())).isInstanceOf(BusinessException.class).hasMessageContaining("instanceId");
    }

    @Test
    void rejectsNativeMetricInWrongDomainTest() {
        assertThatThrownBy(() -> NativeAlertRulePolicy.validate(rule(AlertDomain.CLUSTER, "consumer.lag.total")
                .instanceId("local").build())).isInstanceOf(BusinessException.class).hasMessageContaining("BUSINESS");
    }

    @Test
    void acceptsProxyAvailabilityAsAClusterMetricTest() {
        assertThatCode(() -> NativeAlertRulePolicy.validate(rule(AlertDomain.CLUSTER, "proxy.availability")
                .instanceId("local").build())).doesNotThrowAnyException();
    }

    @Test
    void acceptsExplicitUnavailableAvailabilityRuleTest() {
        assertThatCode(() -> NativeAlertRulePolicy.validate(rule(AlertDomain.CLUSTER, "broker.availability")
                .instanceId("local").operator("UNAVAILABLE").build())).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnavailableForNonAvailabilityMetricTest() {
        assertThatThrownBy(() -> NativeAlertRulePolicy.validate(rule(AlertDomain.CLUSTER,
                "broker.disk.usage_ratio").instanceId("local").operator("UNAVAILABLE").build()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("only supported");
    }

    @Test
    void leavesLegacyPrometheusRulesCompatibleTest() {
        assertThatCode(() -> NativeAlertRulePolicy.validate(rule(AlertDomain.BUSINESS,
                "rocketmq_consumer_lag_messages").build())).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsupportedNotificationChannelsOutsideTheHttpApiTest() {
        assertThatThrownBy(() -> NativeAlertRulePolicy.validate(rule(AlertDomain.BUSINESS,
                "rocketmq_consumer_lag_messages").channels(List.of("webhook")).build()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Unsupported notification channel");
    }

    @Test
    void acceptsNotificationChannelsIndependentlyOfTheDefaultLocaleTest() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThatCode(() -> NativeAlertRulePolicy.validate(rule(AlertDomain.BUSINESS,
                    "rocketmq_consumer_lag_messages").channels(List.of(" DINGTALK ")).build()))
                    .doesNotThrowAnyException();
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void rejectsOverflowingNativeRuleDurationsBeforePersistenceTest() {
        assertThatThrownBy(() -> NativeAlertRulePolicy.validate(rule(AlertDomain.CLUSTER, "broker.availability")
                .instanceId("local").duration("9223372036854775807y").build()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Invalid alert duration");
        assertThatThrownBy(() -> NativeAlertRulePolicy.validate(rule(AlertDomain.CLUSTER, "broker.availability")
                .instanceId("local").reminderInterval("9223372036854775807y").build()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Invalid alert duration");
    }

    private static AlertRuleVO.AlertRuleVOBuilder rule(AlertDomain domain, String metric) {
        return AlertRuleVO.builder().domain(domain).name("Test rule").metric(metric).consecutiveSamples(1);
    }
}
