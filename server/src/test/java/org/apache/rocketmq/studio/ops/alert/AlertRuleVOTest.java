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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the alert rule view of {@link AlertRuleVO}, in particular its {@code @Builder.Default}s:
 * a fresh rule defaults to the BUSINESS domain, LAST aggregation and a zero window - the
 * evaluation semantics every rule inherits unless it says otherwise.
 */
class AlertRuleVOTest {

    @Test
    void theBuilderDefaultsMatchTheEvaluationSemantics() {
        AlertRuleVO rule = AlertRuleVO.builder().name("lag").build();

        assertThat(rule.getDomain()).isEqualTo(AlertDomain.BUSINESS);
        assertThat(rule.getAggregation()).isEqualTo("LAST");
        assertThat(rule.getWindowSeconds()).isZero();
    }

    @Test
    void everyFieldRoundTripsThroughTheBuilder() {
        AlertRuleVO rule = AlertRuleVO.builder()
                .id(7L)
                .domain(AlertDomain.CLUSTER)
                .name("lag")
                .metric("consumer_lag_messages")
                .operator(">")
                .threshold(1000.0)
                .thresholdUnit("messages")
                .duration("5m")
                .aggregation("AVG")
                .windowSeconds(300)
                .channels(List.of("dingtalk"))
                .enabled(true)
                .lastTriggered("2026-01-02T03:04:05Z")
                .description("consumer lag above threshold")
                .build();

        assertThat(rule.getId()).isEqualTo(7L);
        assertThat(rule.getDomain()).isEqualTo(AlertDomain.CLUSTER);
        assertThat(rule.getName()).isEqualTo("lag");
        assertThat(rule.getMetric()).isEqualTo("consumer_lag_messages");
        assertThat(rule.getOperator()).isEqualTo(">");
        assertThat(rule.getThreshold()).isEqualTo(1000.0);
        assertThat(rule.getThresholdUnit()).isEqualTo("messages");
        assertThat(rule.getDuration()).isEqualTo("5m");
        assertThat(rule.getAggregation()).isEqualTo("AVG");
        assertThat(rule.getWindowSeconds()).isEqualTo(300);
        assertThat(rule.getChannels()).containsExactly("dingtalk");
        assertThat(rule.isEnabled()).isTrue();
        assertThat(rule.getLastTriggered()).isEqualTo("2026-01-02T03:04:05Z");
        assertThat(rule.getDescription()).isEqualTo("consumer lag above threshold");
    }

    @Test
    void aNoArgsRuleAlsoCarriesTheDefaultDomain() {
        // @Builder.Default is a field initializer, so the no-args constructor
        // carries the same defaults as the builder - not nulls
        AlertRuleVO rule = new AlertRuleVO();
        assertThat(rule.getDomain()).isEqualTo(AlertDomain.BUSINESS);
        assertThat(rule.getAggregation()).isEqualTo("LAST");
        assertThat(rule.getWindowSeconds()).isZero();
        assertThat(rule.isEnabled()).isFalse();
        assertThat(rule.getThreshold()).isZero();
    }
}
