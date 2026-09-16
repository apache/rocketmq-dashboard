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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AlertSilenceDstTest {

    @Test
    void springGapAtStartDoesNotMoveTheConfiguredEndTest() {
        AlertSilenceVO silence = daily("America/New_York",
                "2026-03-07T02:30:00-05:00", "2026-03-07T04:00:00-05:00",
                "2026-03-12T00:00:00Z");

        assertThat(AlertSilenceSchedule.activeUntil(silence, utc("2026-03-08T03:45:00-04:00")))
                .isEqualTo(utc("2026-03-08T04:00:00-04:00"));
        assertThat(AlertSilenceSchedule.activeUntil(silence, utc("2026-03-08T04:15:00-04:00")))
                .isNull();
    }

    @Test
    void firstWindowPreservesExplicitLaterOffsetDuringOverlapTest() {
        AlertSilenceVO silence = daily("America/New_York",
                "2026-11-01T01:15:00-05:00", "2026-11-01T01:45:00-05:00",
                "2026-11-04T00:00:00Z");

        assertThat(AlertSilenceSchedule.activeUntil(silence, utc("2026-11-01T01:30:00-04:00")))
                .isNull();
        assertThat(AlertSilenceSchedule.activeUntil(silence, utc("2026-11-01T01:30:00-05:00")))
                .isEqualTo(utc("2026-11-01T01:45:00-05:00"));
    }

    @Test
    void firstWindowPreservesExplicitEndOffsetDuringOverlapTest() {
        AlertSilenceVO silence = daily("America/New_York",
                "2026-11-01T00:30:00-04:00", "2026-11-01T01:45:00-05:00",
                "2026-11-04T00:00:00Z");

        assertThat(AlertSilenceSchedule.activeUntil(silence, utc("2026-11-01T01:30:00-05:00")))
                .isEqualTo(utc("2026-11-01T01:45:00-05:00"));
    }

    @Test
    void shiftedStartAtOrAfterEndDoesNotInventAnExtraWindowTest() {
        AlertSilenceVO silence = daily("America/New_York",
                "2026-03-07T02:30:00-05:00", "2026-03-07T03:15:00-05:00",
                "2026-03-12T00:00:00Z");

        assertThat(AlertSilenceSchedule.activeUntil(silence, utc("2026-03-08T03:40:00-04:00")))
                .isNull();
        assertThat(AlertSilenceSchedule.activeUntil(silence, utc("2026-03-09T02:45:00-04:00")))
                .isEqualTo(utc("2026-03-09T03:15:00-04:00"));
    }

    @Test
    void bothBoundariesInTheGapKeepTheirRelativeSpacingTest() {
        AlertSilenceVO silence = daily("America/New_York",
                "2026-03-07T02:10:00-05:00", "2026-03-07T02:40:00-05:00",
                "2026-03-12T00:00:00Z");

        assertThat(AlertSilenceSchedule.activeUntil(silence, utc("2026-03-08T03:20:00-04:00")))
                .isEqualTo(utc("2026-03-08T03:40:00-04:00"));
        assertThat(AlertSilenceSchedule.activeUntil(silence, utc("2026-03-08T03:40:00-04:00")))
                .isNull();
    }

    @Test
    void anEndInTheGapUsesTheJdkForwardResolutionTest() {
        AlertSilenceVO silence = daily("America/New_York",
                "2026-03-07T01:30:00-05:00", "2026-03-07T02:30:00-05:00",
                "2026-03-12T00:00:00Z");

        assertThat(AlertSilenceSchedule.activeUntil(silence, utc("2026-03-08T03:15:00-04:00")))
                .isEqualTo(utc("2026-03-08T03:30:00-04:00"));
    }

    @Test
    void nonHourGapDoesNotAddThirtyMinutesToEndTest() {
        AlertSilenceVO silence = daily("Australia/Lord_Howe",
                "2026-10-03T02:15:00+10:30", "2026-10-03T03:00:00+10:30",
                "2026-10-07T00:00:00Z");

        assertThat(AlertSilenceSchedule.activeUntil(silence, utc("2026-10-04T02:50:00+11:00")))
                .isEqualTo(utc("2026-10-04T03:00:00+11:00"));
        assertThat(AlertSilenceSchedule.activeUntil(silence, utc("2026-10-04T03:10:00+11:00")))
                .isNull();
    }

    @Test
    void weeklyWindowKeepsNextDayEndWhenItsStartFallsInGapTest() {
        AlertSilenceVO silence = daily("America/New_York",
                "2026-03-01T02:30:00-05:00", "2026-03-02T04:00:00-05:00",
                "2026-03-20T00:00:00Z");
        silence.setRecurrence(AlertSilenceRecurrence.WEEKLY);
        silence.setRecurrenceDays(Set.of(7));

        assertThat(AlertSilenceSchedule.activeUntil(silence, utc("2026-03-09T03:45:00-04:00")))
                .isEqualTo(utc("2026-03-09T04:00:00-04:00"));
        assertThat(AlertSilenceSchedule.activeUntil(silence, utc("2026-03-09T04:15:00-04:00")))
                .isNull();
    }

    @Test
    void recurrenceCutoffStillClipsTheResolvedGapWindowTest() {
        AlertSilenceVO silence = daily("America/New_York",
                "2026-03-07T02:30:00-05:00", "2026-03-07T04:00:00-05:00",
                "2026-03-08T03:50:00-04:00");

        assertThat(AlertSilenceSchedule.activeUntil(silence, utc("2026-03-08T03:45:00-04:00")))
                .isEqualTo(utc("2026-03-08T03:50:00-04:00"));
        assertThat(AlertSilenceSchedule.activeUntil(silence, utc("2026-03-08T03:50:00-04:00")))
                .isNull();
    }

    @Test
    void explicitOverlapOffsetsDoNotChangeFollowingNormalDaysTest() {
        AlertSilenceVO silence = daily("America/New_York",
                "2026-11-01T01:15:00-05:00", "2026-11-01T01:45:00-05:00",
                "2026-11-04T00:00:00Z");

        assertThat(AlertSilenceSchedule.activeUntil(silence, utc("2026-11-02T01:30:00-05:00")))
                .isEqualTo(utc("2026-11-02T01:45:00-05:00"));
        assertThat(AlertSilenceSchedule.activeUntil(silence, utc("2026-11-02T01:45:00-05:00")))
                .isNull();
    }

    @Test
    void laterOccurrencesKeepExistingEarlierOverlapOffsetPolicyTest() {
        AlertSilenceVO silence = daily("America/New_York",
                "2026-10-31T01:15:00-04:00", "2026-10-31T01:45:00-04:00",
                "2026-11-04T00:00:00Z");

        assertThat(AlertSilenceSchedule.activeUntil(silence, utc("2026-11-01T01:30:00-04:00")))
                .isEqualTo(utc("2026-11-01T01:45:00-04:00"));
        assertThat(AlertSilenceSchedule.activeUntil(silence, utc("2026-11-01T01:30:00-05:00")))
                .isNull();
    }

    @Test
    void firstWindowPreservesSubsecondInclusiveAndExclusiveBoundariesTest() {
        AlertSilenceVO silence = daily("America/New_York",
                "2026-11-01T01:15:00.123-05:00", "2026-11-01T01:45:00.456-05:00",
                "2026-11-04T00:00:00Z");

        assertThat(AlertSilenceSchedule.activeUntil(silence, silence.getStartsAt().minusNanos(1)))
                .isNull();
        assertThat(AlertSilenceSchedule.activeUntil(silence, silence.getStartsAt()))
                .isEqualTo(silence.getEndsAt());
        assertThat(AlertSilenceSchedule.activeUntil(silence, silence.getEndsAt().minusNanos(1)))
                .isEqualTo(silence.getEndsAt());
        assertThat(AlertSilenceSchedule.activeUntil(silence, silence.getEndsAt())).isNull();
    }

    @Test
    void weeklySeedOnAnUnselectedWeekdayRemainsInactiveTest() {
        AlertSilenceVO silence = daily("America/New_York",
                "2026-11-01T01:15:00-05:00", "2026-11-01T01:45:00-05:00",
                "2026-11-10T00:00:00Z");
        silence.setRecurrence(AlertSilenceRecurrence.WEEKLY);
        silence.setRecurrenceDays(Set.of(1));

        assertThat(AlertSilenceSchedule.activeUntil(silence, utc("2026-11-01T01:30:00-05:00")))
                .isNull();
        assertThat(AlertSilenceSchedule.activeUntil(silence, utc("2026-11-02T01:30:00-05:00")))
                .isEqualTo(utc("2026-11-02T01:45:00-05:00"));
    }

    private static AlertSilenceVO daily(String zone, String start, String end, String until) {
        return AlertSilenceVO.builder()
                .startsAt(utc(start))
                .endsAt(utc(end))
                .recurrence(AlertSilenceRecurrence.DAILY)
                .timeZone(zone)
                .recurrenceDays(Set.of())
                .recurrenceUntil(utc(until))
                .build();
    }

    private static LocalDateTime utc(String value) {
        return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
