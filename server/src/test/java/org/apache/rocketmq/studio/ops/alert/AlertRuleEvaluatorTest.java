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

import org.apache.rocketmq.studio.cluster.metrics.MetricAvailability;
import org.apache.rocketmq.studio.cluster.metrics.MetricSample;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AlertRuleEvaluatorTest {
    private final AlertRuleEvaluator evaluator = new AlertRuleEvaluator();

    @Test
    void triggersMatchingAvailableMetricTest() {
        AlertRuleVO rule = AlertRuleVO.builder().domain(AlertDomain.CLUSTER).metric("broker.disk.usage_ratio")
                .operator(">=").threshold(0.85).enabled(true).build();
        MetricSample sample = sample(MetricAvailability.AVAILABLE, 0.9);

        AlertEvaluationResult result = evaluator.evaluate(rule, sample);

        assertThat(result.matches()).isTrue();
        assertThat(result.conditionMet()).isTrue();
        assertThat(result.currentValue()).isEqualTo(0.9);
    }

    @Test
    void evaluatesPercentageThresholdsForNativeRatioMetricsTest() {
        AlertRuleVO rule = AlertRuleVO.builder().domain(AlertDomain.CLUSTER).metric("broker.disk.usage_ratio")
                .operator(">=").threshold(85).thresholdUnit("%").enabled(true).build();

        AlertEvaluationResult result = evaluator.evaluate(rule, sample(MetricAvailability.AVAILABLE, 0.9));

        assertThat(result.conditionMet()).isTrue();
    }

    @Test
    void unavailableMetricDoesNotBehaveAsZeroTest() {
        AlertRuleVO rule = AlertRuleVO.builder().domain(AlertDomain.CLUSTER).metric("broker.disk.usage_ratio")
                .operator("<").threshold(0.1).enabled(true).build();

        AlertEvaluationResult result = evaluator.evaluate(rule, sample(MetricAvailability.UNAVAILABLE, null));

        assertThat(result.matches()).isTrue();
        assertThat(result.conditionMet()).isFalse();
        assertThat(result.currentValue()).isNull();
    }

    @Test
    void explicitlyTriggersAvailabilityRuleForUnavailableSampleTest() {
        AlertRuleVO rule = AlertRuleVO.builder().domain(AlertDomain.CLUSTER).metric("broker.availability")
                .operator("UNAVAILABLE").enabled(true).build();
        MetricSample sample = new MetricSample("broker.availability", AlertDomain.CLUSTER, "local", null, null,
                null, MetricAvailability.UNAVAILABLE, Instant.now());

        AlertEvaluationResult result = evaluator.evaluate(rule, sample);

        assertThat(result.matches()).isTrue();
        assertThat(result.conditionMet()).isTrue();
        assertThat(result.currentValue()).isNull();
    }

    @Test
    void doesNotMatchOtherRuleDomainTest() {
        AlertRuleVO rule = AlertRuleVO.builder().domain(AlertDomain.BUSINESS).metric("broker.disk.usage_ratio")
                .operator(">=").threshold(0.85).enabled(true).build();

        assertThat(evaluator.evaluate(rule, sample(MetricAvailability.AVAILABLE, 0.9)).matches()).isFalse();
    }

    private static MetricSample sample(MetricAvailability availability, Double value) {
        return new MetricSample("broker.disk.usage_ratio", AlertDomain.CLUSTER, "local", null, null, value,
                availability, Instant.now());
    }
}
