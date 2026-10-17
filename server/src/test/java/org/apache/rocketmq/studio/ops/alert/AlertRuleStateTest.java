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
package org.apache.rocketmq.studio.ops.alert;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the persistable rule state of {@link AlertRuleState}: the initial factory starts every
 * rule at OK with zero hits and no timestamps, and the lifecycle timestamps round-trip
 * (pending, fired, notified, resolved).
 */
class AlertRuleStateTest {

    @Test
    void theInitialStateIsOkWithZeroHitsAndNoTimestamps() {
        AlertRuleState state = AlertRuleState.initial();
        assertThat(state.status()).isEqualTo(AlertStateStatus.OK);
        assertThat(state.consecutiveHits()).isZero();
        assertThat(state.currentValue()).isNull();
        assertThat(state.firstPendingAt()).isNull();
        assertThat(state.firedAt()).isNull();
        assertThat(state.lastNotifiedAt()).isNull();
        assertThat(state.resolvedAt()).isNull();
    }

    @Test
    void theLifecycleTimestampsRoundTrip() {
        AlertRuleState state = new AlertRuleState(
                AlertStateStatus.FIRING,
                3,
                1200.5,
                Instant.parse("2026-01-02T03:00:00Z"),
                Instant.parse("2026-01-02T03:05:00Z"),
                Instant.parse("2026-01-02T03:06:00Z"),
                null);

        assertThat(state.status()).isEqualTo(AlertStateStatus.FIRING);
        assertThat(state.consecutiveHits()).isEqualTo(3);
        assertThat(state.currentValue()).isEqualTo(1200.5);
        assertThat(state.firstPendingAt()).isEqualTo(Instant.parse("2026-01-02T03:00:00Z"));
        assertThat(state.firedAt()).isEqualTo(Instant.parse("2026-01-02T03:05:00Z"));
        assertThat(state.lastNotifiedAt()).isEqualTo(Instant.parse("2026-01-02T03:06:00Z"));
        assertThat(state.resolvedAt()).isNull();
    }
}
