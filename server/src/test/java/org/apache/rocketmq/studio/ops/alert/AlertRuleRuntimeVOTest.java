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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the rule runtime row of {@link AlertRuleRuntimeVO}: the live evaluation state of one
 * rule fingerprint - status, consecutive hits, current value and the notified/next-reminder
 * pair the runtime column renders.
 */
class AlertRuleRuntimeVOTest {

    @Test
    void theRuntimeRowRoundTripsThroughTheBuilder() {
        AlertRuleRuntimeVO row = AlertRuleRuntimeVO.builder()
                .ruleId(42L)
                .fingerprint("fp-1")
                .status(AlertStateStatus.FIRING)
                .consecutiveHits(3)
                .currentValue(1200.5)
                .lastNotifiedAt(LocalDateTime.of(2026, 1, 2, 3, 4))
                .nextReminderAt(LocalDateTime.of(2026, 1, 2, 9, 4))
                .build();

        assertThat(row.getRuleId()).isEqualTo(42L);
        assertThat(row.getFingerprint()).isEqualTo("fp-1");
        assertThat(row.getStatus()).isEqualTo(AlertStateStatus.FIRING);
        assertThat(row.getConsecutiveHits()).isEqualTo(3);
        assertThat(row.getCurrentValue()).isEqualTo(1200.5);
        assertThat(row.getLastNotifiedAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4));
        assertThat(row.getNextReminderAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 9, 4));
    }

    @Test
    void aRowWithoutNotificationsCarriesNoReminder() {
        AlertRuleRuntimeVO row = AlertRuleRuntimeVO.builder().ruleId(42L).build();
        assertThat(row.getLastNotifiedAt()).isNull();
        assertThat(row.getNextReminderAt()).isNull();
        assertThat(row.getConsecutiveHits()).isZero();
    }
}
