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

    @Test
    void ttlStatusIsUnknownWithoutALastActiveTime() {
        LiteTopicSummary summary = new LiteTopicSummary();
        summary.setAverageTTL(10_000L);

        assertThat(summary.getTTLStatus()).isEqualTo("UNKNOWN");
    }

    @Test
    void ttlStatusSeparatesActiveAndExpiringBoundaries() {
        long now = System.currentTimeMillis();
        long averageTtl = 60_000L;
        LiteTopicSummary summary = new LiteTopicSummary();
        summary.setAverageTTL(averageTtl);

        summary.setLastActiveTime(new Date(now - 30_000));
        assertThat(summary.getTTLStatus()).isEqualTo("ACTIVE");

        summary.setLastActiveTime(new Date(now - 50_000));
        assertThat(summary.getTTLStatus()).isEqualTo("EXPIRING_SOON");
    }

    @Test
    void consumerDensityAndEmptyAggregationSemantics() {
        LiteTopicSummary summary = new LiteTopicSummary();
        summary.setTopicCount(6);
        summary.setConsumerCount(3);

        assertThat(summary.getConsumerDensity()).isEqualTo(0.5);
        assertThat(summary.isEmptyAggregation()).isFalse();

        summary.setConsumerCount(null);
        summary.setTotalBacklog(5L);
        assertThat(summary.isEmptyAggregation()).isFalse();

        summary.setConsumerCount(0);
        summary.setTotalBacklog(0L);
        assertThat(summary.isEmptyAggregation()).isTrue();

        LiteTopicSummary unset = new LiteTopicSummary();
        assertThat(unset.getConsumerDensity()).isZero();
        assertThat(unset.isEmptyAggregation()).isTrue();
    }
}
