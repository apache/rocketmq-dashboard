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
    void rejectsDurationsThatOverflowTest() {
        assertThatThrownBy(() -> AlertRuleDuration.parse("9223372036854775807y"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid alert duration");
        assertThatThrownBy(() -> AlertRuleDuration.parse("9223372036854775807s9223372036854775807s"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid alert duration");
    }

    @Test
    void parsesEverySupportedUnitTest() {
        assertThat(AlertRuleDuration.parse("500ms")).isEqualTo(Duration.ofMillis(500));
        assertThat(AlertRuleDuration.parse("45s")).isEqualTo(Duration.ofSeconds(45));
        assertThat(AlertRuleDuration.parse("5m")).isEqualTo(Duration.ofMinutes(5));
        assertThat(AlertRuleDuration.parse("2h")).isEqualTo(Duration.ofHours(2));
        assertThat(AlertRuleDuration.parse("3d")).isEqualTo(Duration.ofDays(3));
        assertThat(AlertRuleDuration.parse("2w")).isEqualTo(Duration.ofDays(14));
        assertThat(AlertRuleDuration.parse("1y")).isEqualTo(Duration.ofDays(365));
    }

    @Test
    void parsesBlankAndZeroValuesAsNoDurationTest() {
        assertThat(AlertRuleDuration.parse("")).isEqualTo(Duration.ZERO);
        assertThat(AlertRuleDuration.parse("   ")).isEqualTo(Duration.ZERO);
        assertThat(AlertRuleDuration.parse("0s")).isEqualTo(Duration.ZERO);
    }

    @Test
    void parsesCompoundDurationsAcrossUnitsTest() {
        assertThat(AlertRuleDuration.parse("2d12h")).isEqualTo(Duration.ofHours(60));
        assertThat(AlertRuleDuration.parse("1w2d3h")).isEqualTo(Duration.ofHours(219));
        assertThat(AlertRuleDuration.parse("1m30s500ms")).isEqualTo(Duration.ofMillis(90_500));
    }

    @Test
    void trimsOuterWhitespaceBeforeParsingTest() {
        assertThat(AlertRuleDuration.parse("  5m  ")).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void rejectsMalformedSyntaxTest() {
        for (String malformed : new String[] {"5", "h30m", "1h 30m", "1h30", "1.5h", "5x", "-5m"}) {
            assertThatThrownBy(() -> AlertRuleDuration.parse(malformed))
                    .as("malformed duration %s", malformed)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Invalid alert duration");
        }
    }
}
