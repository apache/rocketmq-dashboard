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
package org.apache.rocketmq.studio.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LiteTopicSessionTest {

    @Test
    void consumptionProgressShouldHandleUnsetConsumedMessages() {
        LiteTopicSession session = new LiteTopicSession();
        session.setTotalMessages(10L);

        assertThat(session.getConsumptionProgress()).isZero();
    }

    @Test
    void consumptionProgressShouldCalculatePercentage() {
        LiteTopicSession session = new LiteTopicSession();
        session.setTotalMessages(10L);
        session.setConsumedMessages(4L);

        assertThat(session.getConsumptionProgress()).isEqualTo(40.0);
    }

    @Test
    void hasActiveConsumptionShouldRequireActiveStatusAndPositiveRate() {
        LiteTopicSession active = new LiteTopicSession();
        active.setStatus("ACTIVE");
        active.setConsumptionRate(5.0);
        assertThat(active.hasActiveConsumption()).isTrue();

        LiteTopicSession noRate = new LiteTopicSession();
        noRate.setStatus("ACTIVE");
        assertThat(noRate.hasActiveConsumption()).isFalse();

        LiteTopicSession zeroRate = new LiteTopicSession();
        zeroRate.setStatus("ACTIVE");
        zeroRate.setConsumptionRate(0.0);
        assertThat(zeroRate.hasActiveConsumption()).isFalse();

        LiteTopicSession idle = new LiteTopicSession();
        idle.setStatus("IDLE");
        idle.setConsumptionRate(5.0);
        assertThat(idle.hasActiveConsumption()).isFalse();
    }

    @Test
    void isExpiredShouldReflectStatusAndRemainingTtl() {
        LiteTopicSession expiredStatus = new LiteTopicSession();
        expiredStatus.setStatus("EXPIRED");
        assertThat(expiredStatus.isExpired()).isTrue();

        LiteTopicSession zeroTtl = new LiteTopicSession();
        zeroTtl.setStatus("ACTIVE");
        zeroTtl.setTtlRemaining(0L);
        assertThat(zeroTtl.isExpired()).isTrue();

        LiteTopicSession negativeTtl = new LiteTopicSession();
        negativeTtl.setStatus("ACTIVE");
        negativeTtl.setTtlRemaining(-5L);
        assertThat(negativeTtl.isExpired()).isTrue();

        LiteTopicSession live = new LiteTopicSession();
        live.setStatus("ACTIVE");
        live.setTtlRemaining(300L);
        assertThat(live.isExpired()).isFalse();
    }

    @Test
    void consumptionProgressShouldNotClampOverConsumption() {
        LiteTopicSession session = new LiteTopicSession();
        session.setTotalMessages(10L);
        session.setConsumedMessages(16L);

        assertThat(session.getConsumptionProgress()).isEqualTo(160.0);
    }
}
