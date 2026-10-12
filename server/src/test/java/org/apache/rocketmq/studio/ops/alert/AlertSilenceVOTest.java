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
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the alert silence view of {@link AlertSilenceVO}: the silence window with its optional
 * recurrence (time zone and day-of-week mask) round-trips, and a fresh silence carries no
 * labels and no day mask - a one-off silence.
 */
class AlertSilenceVOTest {

    @Test
    void theSilenceRoundTripsThroughTheBuilder() {
        AlertSilenceVO silence = AlertSilenceVO.builder()
                .id(7L)
                .domain(AlertDomain.BUSINESS)
                .ruleId(42L)
                .instanceId("instance-1")
                .labels(Map.of("group", "order"))
                .startsAt(LocalDateTime.of(2026, 1, 2, 3, 0))
                .endsAt(LocalDateTime.of(2026, 1, 2, 5, 0))
                .recurrence(AlertSilenceRecurrence.WEEKLY)
                .timeZone("Asia/Shanghai")
                .recurrenceDays(Set.of(1, 2, 3, 4, 5))
                .recurrenceUntil(LocalDateTime.of(2026, 2, 1, 0, 0))
                .reason("maintenance window")
                .createdBy("alice")
                .build();

        assertThat(silence.getId()).isEqualTo(7L);
        assertThat(silence.getDomain()).isEqualTo(AlertDomain.BUSINESS);
        assertThat(silence.getRuleId()).isEqualTo(42L);
        assertThat(silence.getLabels()).containsEntry("group", "order");
        assertThat(silence.getStartsAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 0));
        assertThat(silence.getEndsAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 5, 0));
        assertThat(silence.getRecurrence()).isEqualTo(AlertSilenceRecurrence.WEEKLY);
        assertThat(silence.getTimeZone()).isEqualTo("Asia/Shanghai");
        assertThat(silence.getRecurrenceDays()).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
        assertThat(silence.getRecurrenceUntil()).isEqualTo(LocalDateTime.of(2026, 2, 1, 0, 0));
        assertThat(silence.getReason()).isEqualTo("maintenance window");
        assertThat(silence.getCreatedBy()).isEqualTo("alice");
    }

    @Test
    void aFreshSilenceCarriesNoFilters() {
        // no @Builder.Defaults here: a fresh silence has no labels, no day mask
        // and no recurrence - the caller states the window, nothing is assumed
        AlertSilenceVO silence = new AlertSilenceVO();
        assertThat(silence.getLabels()).isNull();
        assertThat(silence.getRecurrenceDays()).isNull();
        assertThat(silence.getRecurrence()).isNull();
    }
}
