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

import static org.assertj.core.api.Assertions.assertThat;

class LiteTopicQuotaTest {

    @Test
    void quotaShouldNotBeExceededWhenMaxIsUnset() {
        LiteTopicQuota quota = new LiteTopicQuota();
        quota.setCurrentTopicCount(10);

        assertThat(quota.isQuotaExceeded()).isFalse();
    }

    @Test
    void nonPositiveLimitsShouldRemainUnavailableInsteadOfExceeded() {
        LiteTopicQuota quota = new LiteTopicQuota();
        quota.setMaxTopicCount(0);
        quota.setCurrentTopicCount(1);
        quota.setMaxSessionCount(-1);
        quota.setCurrentSessionCount(1);

        assertThat(quota.getUsageRate()).isZero();
        assertThat(quota.getSessionUsageRate()).isZero();
        assertThat(quota.isQuotaExceeded()).isFalse();
        assertThat(quota.getRemainingQuota()).isZero();
    }

    @Test
    void quotaShouldBeExceededOnlyAtOrAbovePositiveMax() {
        LiteTopicQuota quota = new LiteTopicQuota();
        quota.setMaxTopicCount(10);

        quota.setCurrentTopicCount(9);
        assertThat(quota.isQuotaExceeded()).isFalse();
        assertThat(quota.getRemainingQuota()).isEqualTo(1);

        quota.setCurrentTopicCount(10);
        assertThat(quota.isQuotaExceeded()).isTrue();

        quota.setCurrentTopicCount(11);
        assertThat(quota.isQuotaExceeded()).isTrue();
        assertThat(quota.getRemainingQuota()).isZero();
    }

    @Test
    void quotaCalculationsShouldHandleUnsetCurrentCounts() {
        LiteTopicQuota quota = new LiteTopicQuota();
        quota.setMaxTopicCount(10);
        quota.setMaxSessionCount(5);

        assertThat(quota.getUsageRate()).isZero();
        assertThat(quota.getSessionUsageRate()).isZero();
        assertThat(quota.getRemainingQuota()).isEqualTo(10);
    }
}
