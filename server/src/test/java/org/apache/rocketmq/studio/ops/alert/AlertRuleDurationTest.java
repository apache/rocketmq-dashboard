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
}
