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
package org.apache.rocketmq.studio.instance.topic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the lite-topic quota view of {@link LiteTopicQuotaVO}: the current/max pairs for topics,
 * sessions and creation rate, the derived usage rates, and the TTL bounds round-trip through
 * the builder; a fresh quota carries no limits.
 */
class LiteTopicQuotaVOTest {

    @Test
    void everyFieldRoundTripsThroughTheBuilder() {
        LiteTopicQuotaVO quota = LiteTopicQuotaVO.builder()
                .currentTopicCount(10)
                .maxTopicCount(100)
                .currentSessionCount(5)
                .maxSessionCount(50)
                .currentCreationRate(2)
                .maxCreationRate(10)
                .usageRate(0.1)
                .sessionUsageRate(0.1)
                .defaultTTL(86_400L)
                .maxTTL(604_800L)
                .remainingQuota(90)
                .build();

        assertThat(quota.getCurrentTopicCount()).isEqualTo(10);
        assertThat(quota.getMaxTopicCount()).isEqualTo(100);
        assertThat(quota.getCurrentSessionCount()).isEqualTo(5);
        assertThat(quota.getMaxSessionCount()).isEqualTo(50);
        assertThat(quota.getCurrentCreationRate()).isEqualTo(2);
        assertThat(quota.getMaxCreationRate()).isEqualTo(10);
        assertThat(quota.getUsageRate()).isEqualTo(0.1);
        assertThat(quota.getSessionUsageRate()).isEqualTo(0.1);
        assertThat(quota.getDefaultTTL()).isEqualTo(86_400L);
        assertThat(quota.getMaxTTL()).isEqualTo(604_800L);
        assertThat(quota.getRemainingQuota()).isEqualTo(90);
    }

    @Test
    void aMinimalQuotaCarriesNoLimits() {
        LiteTopicQuotaVO quota = LiteTopicQuotaVO.builder().build();
        assertThat(quota.getCurrentTopicCount()).isNull();
        assertThat(quota.getMaxTopicCount()).isNull();
        assertThat(quota.getRemainingQuota()).isNull();
    }
}
