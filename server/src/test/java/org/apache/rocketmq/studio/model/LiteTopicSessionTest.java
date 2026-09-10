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
    void activeConsumptionRequiresActiveStatusAndPositiveRate() {
        LiteTopicSession session = new LiteTopicSession();

        session.setStatus("ACTIVE");
        session.setConsumptionRate(5.0);
        assertThat(session.hasActiveConsumption()).isTrue();

        session.setConsumptionRate(0.0);
        assertThat(session.hasActiveConsumption()).isFalse();

        session.setConsumptionRate(null);
        assertThat(session.hasActiveConsumption()).isFalse();

        session.setStatus("EXPIRED");
        session.setConsumptionRate(5.0);
        assertThat(session.hasActiveConsumption()).isFalse();
    }

    @Test
    void expirationComesFromStatusOrTtlRemaining() {
        LiteTopicSession session = new LiteTopicSession();

        session.setStatus("EXPIRED");
        assertThat(session.isExpired()).isTrue();

        session.setStatus("ACTIVE");
        session.setTtlRemaining(0L);
        assertThat(session.isExpired()).isTrue();

        session.setTtlRemaining(-5L);
        assertThat(session.isExpired()).isTrue();

        session.setTtlRemaining(10L);
        assertThat(session.isExpired()).isFalse();

        session.setTtlRemaining(null);
        assertThat(session.isExpired()).isFalse();
    }

    @Test
    void progressHandlesNullOrZeroTotals() {
        LiteTopicSession session = new LiteTopicSession();

        assertThat(session.getConsumptionProgress()).isZero();

        session.setConsumedMessages(4L);
        assertThat(session.getConsumptionProgress()).isZero();

        session.setTotalMessages(0L);
        assertThat(session.getConsumptionProgress()).isZero();
    }
}
