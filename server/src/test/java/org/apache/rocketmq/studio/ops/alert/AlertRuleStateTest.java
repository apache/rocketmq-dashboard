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
 * Unit tests for {@link AlertRuleState}, the persistable state for a rule/resource
 * fingerprint that drives native alert state transitions.
 */
class AlertRuleStateTest {

    @Test
    void initialStateIsOkWithNoTimestamps() {
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
    void recordCarriesThePersistedFieldsWithValueEquality() {
        Instant now = Instant.parse("2026-07-01T10:00:00Z");
        AlertRuleState state = new AlertRuleState(AlertStateStatus.FIRING, 3, 90.0,
                now, now, now, null);
        AlertRuleState same = new AlertRuleState(AlertStateStatus.FIRING, 3, 90.0,
                now, now, now, null);

        assertThat(state.status()).isEqualTo(AlertStateStatus.FIRING);
        assertThat(state.consecutiveHits()).isEqualTo(3);
        assertThat(state.currentValue()).isEqualTo(90.0);
        assertThat(state.firedAt()).isEqualTo(now);
        assertThat(state).isEqualTo(same).hasSameHashCodeAs(same);

        AlertRuleState resolved = new AlertRuleState(AlertStateStatus.RESOLVED, 0, null,
                now, now, null, now.plusSeconds(60));
        assertThat(state).isNotEqualTo(resolved);
    }
}
