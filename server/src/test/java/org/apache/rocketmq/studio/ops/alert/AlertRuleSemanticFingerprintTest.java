/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 */
package org.apache.rocketmq.studio.ops.alert;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlertRuleSemanticFingerprintTest {

    @Test
    void ignoresPresentationAndDeliverySettingsTest() {
        AlertRuleVO first = AlertRuleVO.builder()
                .domain(AlertDomain.CLUSTER).name("Disk warning").instanceId("local")
                .metric("broker.disk.usage_ratio").operator(">=").threshold(85)
                .duration("5m").aggregation("LAST").channels(List.of("email"))
                .severity("warning").enabled(true).reminderInterval("30m").build();
        AlertRuleVO second = AlertRuleVO.builder()
                .domain(AlertDomain.CLUSTER).name("Disk critical").instanceId(" local ")
                .metric(" broker.disk.usage_ratio ").operator(" >= ").threshold(85.0)
                .duration("5M").channels(List.of("dingtalk", "sms"))
                .severity("critical").enabled(false).reminderInterval("1h").build();

        assertThat(AlertRuleSemanticFingerprint.of(second))
                .isEqualTo(AlertRuleSemanticFingerprint.of(first));
    }

    @Test
    void changesWhenTheEvaluationScopeOrWindowChangesTest() {
        AlertRuleVO base = AlertRuleVO.builder()
                .domain(AlertDomain.BUSINESS).instanceId("local").metric("consumer.lag.total")
                .operator(">=").threshold(1000).duration("5m").consumerGroup("group-a").build();
        AlertRuleVO differentScope = AlertRuleVO.builder()
                .domain(AlertDomain.BUSINESS).instanceId("local").metric("consumer.lag.total")
                .operator(">=").threshold(1000).duration("5m").consumerGroup("group-b").build();
        AlertRuleVO differentWindow = AlertRuleVO.builder()
                .domain(AlertDomain.BUSINESS).instanceId("local").metric("consumer.lag.total")
                .operator(">=").threshold(1000).duration("5m").consumerGroup("group-a")
                .windowSeconds(60).build();

        assertThat(AlertRuleSemanticFingerprint.of(differentScope))
                .isNotEqualTo(AlertRuleSemanticFingerprint.of(base));
        assertThat(AlertRuleSemanticFingerprint.of(differentWindow))
                .isNotEqualTo(AlertRuleSemanticFingerprint.of(base));
    }

    @Test
    void ratioThresholdsShouldUseTheirNormalizedValueForSemanticIdentityTest() {
        AlertRuleVO percentage = AlertRuleVO.builder()
                .domain(AlertDomain.CLUSTER)
                .metric("broker.disk.usage_ratio")
                .operator(">=")
                .threshold(85)
                .thresholdUnit("%")
                .build();
        AlertRuleVO fraction = AlertRuleVO.builder()
                .domain(AlertDomain.CLUSTER)
                .metric("broker.disk.usage_ratio")
                .operator(">=")
                .threshold(0.85)
                .thresholdUnit(null)
                .build();
        AlertRuleVO rawValue = AlertRuleVO.builder()
                .domain(AlertDomain.CLUSTER)
                .metric("broker.disk.usage_ratio")
                .operator(">=")
                .threshold(85)
                .thresholdUnit(null)
                .build();

        assertThat(AlertRuleSemanticFingerprint.of(percentage))
                .isEqualTo(AlertRuleSemanticFingerprint.of(fraction))
                .isNotEqualTo(AlertRuleSemanticFingerprint.of(rawValue));
    }

    @Test
    void domainDefaultsToBusinessAndBlankInstanceMatchesNull() {
        AlertRuleVO nullDomain = AlertRuleVO.builder()
                .domain(null)
                .instanceId(null)
                .metric("consumer.lag.total").operator(">=").threshold(100)
                .duration("5m").build();
        AlertRuleVO defaulted = AlertRuleVO.builder()
                .domain(AlertDomain.BUSINESS)
                .instanceId("   ")
                .metric("consumer.lag.total").operator(">=").threshold(100)
                .duration("5m").build();

        assertThat(AlertRuleSemanticFingerprint.of(nullDomain))
                .isEqualTo(AlertRuleSemanticFingerprint.of(defaulted));
    }

    @Test
    void blankAggregationFallsBackToLastWhileExplicitValueChangesFingerprint() {
        AlertRuleVO base = AlertRuleVO.builder()
                .metric("consumer.lag.total").operator(">=").threshold(100)
                .duration("5m").build();
        AlertRuleVO blank = AlertRuleVO.builder()
                .metric("consumer.lag.total").operator(">=").threshold(100)
                .duration("5m").aggregation("  ").build();
        AlertRuleVO avg = AlertRuleVO.builder()
                .metric("consumer.lag.total").operator(">=").threshold(100)
                .duration("5m").aggregation("AVG").build();

        assertThat(AlertRuleSemanticFingerprint.of(blank))
                .isEqualTo(AlertRuleSemanticFingerprint.of(base));
        assertThat(AlertRuleSemanticFingerprint.of(avg))
                .isNotEqualTo(AlertRuleSemanticFingerprint.of(base));
    }

    @Test
    void percentageUnitStaysRawForNonRatioMetrics() {
        AlertRuleVO withUnit = AlertRuleVO.builder()
                .metric("consumer.lag.total").operator(">=").threshold(85)
                .thresholdUnit("%").duration("5m").build();
        AlertRuleVO raw = AlertRuleVO.builder()
                .metric("consumer.lag.total").operator(">=").threshold(85)
                .duration("5m").build();
        AlertRuleVO fraction = AlertRuleVO.builder()
                .metric("consumer.lag.total").operator(">=").threshold(0.85)
                .duration("5m").build();

        assertThat(AlertRuleSemanticFingerprint.of(withUnit))
                .isEqualTo(AlertRuleSemanticFingerprint.of(raw))
                .isNotEqualTo(AlertRuleSemanticFingerprint.of(fraction));
    }

    @Test
    void negativeWindowAndConsecutiveSamplesAreClamped() {
        AlertRuleVO base = AlertRuleVO.builder()
                .metric("consumer.lag.total").operator(">=").threshold(100)
                .duration("5m").windowSeconds(0).consecutiveSamples(1).build();
        AlertRuleVO clamped = AlertRuleVO.builder()
                .metric("consumer.lag.total").operator(">=").threshold(100)
                .duration("5m").windowSeconds(-10).consecutiveSamples(-3).build();

        assertThat(AlertRuleSemanticFingerprint.of(clamped))
                .isEqualTo(AlertRuleSemanticFingerprint.of(base));
    }
}
