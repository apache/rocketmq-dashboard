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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AlertSilenceScheduleTest {

    @Test
    void oneTimeWindowIncludesStartAndExcludesEndTest() {
        AlertSilenceVO silence = once("2026-09-01T01:00:00", "2026-09-01T02:00:00");

        assertThat(AlertSilenceSchedule.activeUntil(silence, at("2026-09-01T00:59:59"))).isNull();
        assertThat(AlertSilenceSchedule.activeUntil(silence, at("2026-09-01T01:00:00")))
                .isEqualTo(at("2026-09-01T02:00:00"));
        assertThat(AlertSilenceSchedule.activeUntil(silence, at("2026-09-01T01:59:59")))
                .isEqualTo(at("2026-09-01T02:00:00"));
        assertThat(AlertSilenceSchedule.activeUntil(silence, at("2026-09-01T02:00:00"))).isNull();
    }

    @Test
    void dailyWindowRepeatsAtTheConfiguredLocalTimeTest() {
        AlertSilenceVO silence = recurring(AlertSilenceRecurrence.DAILY, "Asia/Shanghai", Set.of(),
                "2026-09-01T01:00:00", "2026-09-01T02:00:00", "2026-09-10T00:00:00");

        assertThat(AlertSilenceSchedule.activeUntil(silence, at("2026-09-03T01:30:00")))
                .isEqualTo(at("2026-09-03T02:00:00"));
        assertThat(AlertSilenceSchedule.activeUntil(silence, at("2026-09-03T02:30:00"))).isNull();
    }

    @Test
    void dailyWindowCanCrossLocalMidnightTest() {
        AlertSilenceVO silence = recurring(AlertSilenceRecurrence.DAILY, "Asia/Shanghai", Set.of(),
                "2026-09-01T15:00:00", "2026-09-01T17:00:00", "2026-09-10T00:00:00");

        assertThat(AlertSilenceSchedule.activeUntil(silence, at("2026-09-03T16:30:00")))
                .isEqualTo(at("2026-09-03T17:00:00"));
        assertThat(AlertSilenceSchedule.activeUntil(silence, at("2026-09-03T14:59:59"))).isNull();
    }

    @Test
    void weeklyWindowRunsOnlyOnSelectedIsoWeekdaysTest() {
        AlertSilenceVO silence = recurring(AlertSilenceRecurrence.WEEKLY, "UTC", Set.of(2, 4),
                "2026-09-01T10:00:00", "2026-09-01T11:00:00", "2026-10-01T00:00:00");

        assertThat(AlertSilenceSchedule.activeUntil(silence, at("2026-09-08T10:30:00")))
                .isEqualTo(at("2026-09-08T11:00:00"));
        assertThat(AlertSilenceSchedule.activeUntil(silence, at("2026-09-10T10:30:00")))
                .isEqualTo(at("2026-09-10T11:00:00"));
        assertThat(AlertSilenceSchedule.activeUntil(silence, at("2026-09-09T10:30:00"))).isNull();
    }

    @Test
    void weeklyWindowCanRemainActiveOnFollowingDayTest() {
        AlertSilenceVO silence = recurring(AlertSilenceRecurrence.WEEKLY, "UTC", Set.of(2),
                "2026-09-01T22:00:00", "2026-09-03T02:00:00", "2026-10-01T00:00:00");

        assertThat(AlertSilenceSchedule.activeUntil(silence, at("2026-09-09T01:00:00")))
                .isEqualTo(at("2026-09-10T02:00:00"));
        assertThat(AlertSilenceSchedule.activeUntil(silence, at("2026-09-10T02:00:00"))).isNull();
    }

    @Test
    void recurrenceNeverStartsBeforeSeedWindowTest() {
        AlertSilenceVO silence = recurring(AlertSilenceRecurrence.DAILY, "UTC", Set.of(),
                "2026-09-10T10:00:00", "2026-09-10T11:00:00", "2026-09-20T00:00:00");

        assertThat(AlertSilenceSchedule.activeUntil(silence, at("2026-09-09T10:30:00"))).isNull();
        assertThat(AlertSilenceSchedule.activeUntil(silence, at("2026-09-10T10:30:00")))
                .isEqualTo(at("2026-09-10T11:00:00"));
    }

    @Test
    void recurrenceEndCapsTheLastOccurrenceTest() {
        AlertSilenceVO silence = recurring(AlertSilenceRecurrence.DAILY, "UTC", Set.of(),
                "2026-09-01T10:00:00", "2026-09-01T12:00:00", "2026-09-03T11:00:00");

        assertThat(AlertSilenceSchedule.activeUntil(silence, at("2026-09-03T10:30:00")))
                .isEqualTo(at("2026-09-03T11:00:00"));
        assertThat(AlertSilenceSchedule.activeUntil(silence, at("2026-09-03T11:00:00"))).isNull();
    }

    @Test
    void dailyWindowKeepsWallClockTimesAcrossSpringDstTest() {
        AlertSilenceVO silence = recurring(AlertSilenceRecurrence.DAILY, "America/New_York", Set.of(),
                "2026-03-07T06:30:00", "2026-03-07T08:30:00", "2026-03-12T00:00:00");

        assertThat(AlertSilenceSchedule.activeUntil(silence, at("2026-03-08T07:15:00")))
                .isEqualTo(at("2026-03-08T07:30:00"));
        assertThat(AlertSilenceSchedule.activeUntil(silence, at("2026-03-09T06:45:00")))
                .isEqualTo(at("2026-03-09T07:30:00"));
    }

    @Test
    void weeklyWindowWithoutSelectedWeekdaysNeverActivatesTest() {
        AlertSilenceVO silence = recurring(AlertSilenceRecurrence.WEEKLY, "UTC", null,
                "2026-09-01T10:00:00", "2026-09-01T11:00:00", "2026-10-01T00:00:00");

        assertThat(AlertSilenceSchedule.activeUntil(silence, at("2026-09-08T10:30:00"))).isNull();
    }

    @Test
    void recurrenceDefaultsToOnceWhenFieldIsNullTest() {
        AlertSilenceVO silence = AlertSilenceVO.builder()
                .startsAt(at("2026-09-01T01:00:00"))
                .endsAt(at("2026-09-01T02:00:00"))
                .build();

        assertThat(AlertSilenceSchedule.activeUntil(silence, at("2026-09-01T01:30:00")))
                .isEqualTo(at("2026-09-01T02:00:00"));
        assertThat(AlertSilenceSchedule.activeUntil(silence, at("2026-09-01T02:00:00"))).isNull();
    }

    @Test
    void windowDoesNotActivateOnceRecurrenceUntilHasPassedTest() {
        AlertSilenceVO silence = recurring(AlertSilenceRecurrence.DAILY, "UTC", Set.of(),
                "2026-09-01T10:00:00", "2026-09-01T11:00:00", "2026-09-03T11:00:00");

        assertThat(AlertSilenceSchedule.activeUntil(silence, at("2026-09-04T10:30:00"))).isNull();
    }

    private static AlertSilenceVO once(String start, String end) {
        return AlertSilenceVO.builder().startsAt(at(start)).endsAt(at(end))
                .recurrence(AlertSilenceRecurrence.ONCE).recurrenceDays(Set.of()).build();
    }

    private static AlertSilenceVO recurring(AlertSilenceRecurrence recurrence, String timeZone, Set<Integer> days,
            String start, String end, String recurrenceUntil) {
        return AlertSilenceVO.builder().startsAt(at(start)).endsAt(at(end)).recurrence(recurrence)
                .timeZone(timeZone).recurrenceDays(days).recurrenceUntil(at(recurrenceUntil)).build();
    }

    private static LocalDateTime at(String value) {
        return LocalDateTime.parse(value);
    }
}
