/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.ops.alert;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertRuleDurationTest {
    @Test
    void parsesTheRuleDurationSyntaxTest() {
        assertThat(AlertRuleDuration.parse("1h30m")).isEqualTo(Duration.ofMinutes(90));
        assertThat(AlertRuleDuration.parse(null)).isEqualTo(Duration.ZERO);
    }

    @Test
    void parsesSingleAndCompoundUnits() {
        assertThat(AlertRuleDuration.parse("500ms")).isEqualTo(Duration.ofMillis(500));
        assertThat(AlertRuleDuration.parse("30s")).isEqualTo(Duration.ofSeconds(30));
        assertThat(AlertRuleDuration.parse("1h")).isEqualTo(Duration.ofHours(1));
        assertThat(AlertRuleDuration.parse("1d")).isEqualTo(Duration.ofDays(1));
        assertThat(AlertRuleDuration.parse("1w")).isEqualTo(Duration.ofDays(7));
        assertThat(AlertRuleDuration.parse("1y")).isEqualTo(Duration.ofDays(365));
        assertThat(AlertRuleDuration.parse("2w3d")).isEqualTo(Duration.ofDays(17));
    }

    @Test
    void rejectsMalformedDurations() {
        assertThatThrownBy(() -> AlertRuleDuration.parse("1x"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid alert duration");
        assertThatThrownBy(() -> AlertRuleDuration.parse("1h30"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid alert duration");
        assertThatThrownBy(() -> AlertRuleDuration.parse("h1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid alert duration");
        assertThatThrownBy(() -> AlertRuleDuration.parse("1h 30m"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid alert duration");
    }

    @Test
    void treatsBlankValueAsZeroDuration() {
        assertThat(AlertRuleDuration.parse("   ")).isEqualTo(Duration.ZERO);
    }

    @Test
    void rejectsDurationsThatOverflowTest() {
        assertThatThrownBy(() -> AlertRuleDuration.parse("9223372036854775807y"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid alert duration");
        assertThatThrownBy(() -> AlertRuleDuration.parse("9223372036854775807s9223372036854775807s"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid alert duration");
    }
}
