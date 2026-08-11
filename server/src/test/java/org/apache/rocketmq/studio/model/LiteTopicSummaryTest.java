/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.rocketmq.studio.model;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class LiteTopicSummaryTest {

    @Test
    void ttlStatusShouldPreferExpiredOverExpiringSoon() {
        LiteTopicSummary summary = new LiteTopicSummary();
        summary.setLastActiveTime(new Date(System.currentTimeMillis() - 20_000));
        summary.setAverageTTL(10_000L);

        assertThat(summary.getTTLStatus()).isEqualTo("EXPIRED");
    }

    @Test
    void ttlStatusShouldReturnExpiringSoonWithinThreshold() {
        LiteTopicSummary summary = new LiteTopicSummary();
        summary.setLastActiveTime(new Date(System.currentTimeMillis() - 9_000));
        summary.setAverageTTL(10_000L);

        assertThat(summary.getTTLStatus()).isEqualTo("EXPIRING_SOON");
    }

    @Test
    void ttlStatusShouldReturnActiveWhenNoTTLConfigured() {
        LiteTopicSummary summary = new LiteTopicSummary();
        summary.setLastActiveTime(new Date(System.currentTimeMillis() - 1_000));

        assertThat(summary.getTTLStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void aggregationHelpersShouldHandleUnsetConsumerCount() {
        LiteTopicSummary summary = new LiteTopicSummary();
        summary.setTopicCount(5);

        assertThat(summary.getConsumerDensity()).isZero();
        assertThat(summary.isEmptyAggregation()).isTrue();
    }
}
