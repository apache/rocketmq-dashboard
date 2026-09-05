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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertRuleEvaluatorTest {

    private final AlertRuleEvaluator evaluator = new AlertRuleEvaluator();

    private AlertRuleVO rule(String metric, AlertDomain domain, String operator, double threshold) {
        return AlertRuleVO.builder()
            .domain(domain)
            .metric(metric)
            .operator(operator)
            .threshold(threshold)
            .duration("5m")
            .build();
    }

    private MetricSample sample(String metricKey, AlertDomain domain, double value) {
        return new MetricSample(metricKey, domain, "inst-1", "cluster-1", Map.of(),
                value, MetricAvailability.AVAILABLE, Instant.parse("2026-09-01T08:00:00Z"));
    }

    private MetricSample unavailableSample(String metricKey, AlertDomain domain) {
        return new MetricSample(metricKey, domain, "inst-1", "cluster-1", Map.of(),
                null, MetricAvailability.UNAVAILABLE, Instant.parse("2026-09-01T08:00:00Z"));
    }

    @Test
    void mismatchingMetricDoesNotMatch() {
        AlertEvaluationResult result = evaluator.evaluate(
                rule("broker.disk.usage_ratio", AlertDomain.CLUSTER, ">", 0.8),
                sample("broker.cpu.usage", AlertDomain.CLUSTER, 0.9));

        assertFalse(result.matches());
        assertEquals(MetricAvailability.AVAILABLE, result.availability());
    }

    @Test
    void mismatchingDomainDoesNotMatch() {
        AlertEvaluationResult result = evaluator.evaluate(
                rule("consumer.lag.total", AlertDomain.BUSINESS, ">", 100),
                sample("consumer.lag.total", AlertDomain.CLUSTER, 500));

        assertFalse(result.matches());
    }

    @Test
    void unavailableSampleOnlyMatchesUnavailableOperator() {
        AlertEvaluationResult matched = evaluator.evaluate(
                rule("broker.availability", AlertDomain.CLUSTER, "UNAVAILABLE", 0),
                unavailableSample("broker.availability", AlertDomain.CLUSTER));

        assertTrue(matched.matches());
        assertTrue(matched.conditionMet());
        assertNull(matched.currentValue());

        AlertEvaluationResult notMatched = evaluator.evaluate(
                rule("broker.availability", AlertDomain.CLUSTER, ">", 0),
                unavailableSample("broker.availability", AlertDomain.CLUSTER));

        assertTrue(notMatched.matches());
        assertFalse(notMatched.conditionMet());
    }

    @Test
    void greaterThanComparesAgainstThreshold() {
        AlertRuleVO rule = rule("broker.disk.usage_ratio", AlertDomain.CLUSTER, ">", 0.8);

        assertTrue(evaluator.evaluate(rule, sample("broker.disk.usage_ratio", AlertDomain.CLUSTER, 0.9))
                .conditionMet());
        assertFalse(evaluator.evaluate(rule, sample("broker.disk.usage_ratio", AlertDomain.CLUSTER, 0.7))
                .conditionMet());
    }

    @Test
    void equalityOperatorUsesDoubleCompare() {
        AlertRuleVO rule = rule("broker.disk.usage_ratio", AlertDomain.CLUSTER, "==", 0.8);

        assertTrue(evaluator.evaluate(rule, sample("broker.disk.usage_ratio", AlertDomain.CLUSTER, 0.8))
                .conditionMet());
        assertFalse(evaluator.evaluate(rule, sample("broker.disk.usage_ratio", AlertDomain.CLUSTER, 0.81))
                .conditionMet());
    }

    @Test
    void notEqualsOperatorDetectsDifference() {
        AlertRuleVO rule = rule("broker.disk.usage_ratio", AlertDomain.CLUSTER, "!=", 0.8);

        assertTrue(evaluator.evaluate(rule, sample("broker.disk.usage_ratio", AlertDomain.CLUSTER, 0.81))
                .conditionMet());
        assertFalse(evaluator.evaluate(rule, sample("broker.disk.usage_ratio", AlertDomain.CLUSTER, 0.8))
                .conditionMet());
    }

    @Test
    void unknownOrNullOperatorNeverMatches() {
        AlertRuleVO unknown = rule("broker.disk.usage_ratio", AlertDomain.CLUSTER, "~", 0.8);
        assertFalse(evaluator.evaluate(unknown, sample("broker.disk.usage_ratio", AlertDomain.CLUSTER, 0.9))
                .conditionMet());

        AlertRuleVO nullOp = rule("broker.disk.usage_ratio", AlertDomain.CLUSTER, null, 0.8);
        assertFalse(evaluator.evaluate(nullOp, sample("broker.disk.usage_ratio", AlertDomain.CLUSTER, 0.9))
                .conditionMet());
    }

    @Test
    void nullRuleOrSampleDoesNotMatch() {
        AlertEvaluationResult noRule = evaluator.evaluate(null,
                sample("broker.disk.usage_ratio", AlertDomain.CLUSTER, 0.9));

        assertFalse(noRule.matches());
        assertEquals(MetricAvailability.AVAILABLE, noRule.availability());

        AlertEvaluationResult noSample = evaluator.evaluate(
                rule("broker.disk.usage_ratio", AlertDomain.CLUSTER, ">", 0.8), null);

        assertFalse(noSample.matches());
        assertNull(noSample.availability());
    }

    @Test
    void nullDomainDefaultsToBusiness() {
        AlertRuleVO rule = AlertRuleVO.builder()
            .metric("consumer.lag.total")
            .operator(">")
            .threshold(100)
            .build();

        AlertEvaluationResult result = evaluator.evaluate(rule,
                sample("consumer.lag.total", AlertDomain.BUSINESS, 500));

        assertTrue(result.matches());
        assertTrue(result.conditionMet());
    }
}
