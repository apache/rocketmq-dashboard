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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Set;

/** Resolves one-time and recurring maintenance windows against the UTC alert evaluation clock. */
final class AlertSilenceSchedule {
    private AlertSilenceSchedule() {
    }

    static LocalDateTime activeUntil(AlertSilenceVO silence, LocalDateTime utcNow) {
        AlertSilenceRecurrence recurrence = silence.getRecurrence() == null
                ? AlertSilenceRecurrence.ONCE : silence.getRecurrence();
        if (recurrence == AlertSilenceRecurrence.ONCE) {
            return isInside(utcNow, silence.getStartsAt(), silence.getEndsAt()) ? silence.getEndsAt() : null;
        }
        if (silence.getRecurrenceUntil() == null || !utcNow.isBefore(silence.getRecurrenceUntil())) {
            return null;
        }

        ZoneId zone = ZoneId.of(silence.getTimeZone());
        Instant now = utcNow.toInstant(ZoneOffset.UTC);
        ZonedDateTime localNow = now.atZone(zone);
        ZonedDateTime seedStart = silence.getStartsAt().toInstant(ZoneOffset.UTC).atZone(zone);
        ZonedDateTime seedEnd = silence.getEndsAt().toInstant(ZoneOffset.UTC).atZone(zone);
        Duration wallDuration = Duration.between(seedStart.toLocalDateTime(), seedEnd.toLocalDateTime());
        int daysToInspect = recurrence == AlertSilenceRecurrence.DAILY ? 1 : 6;

        for (int offset = 0; offset <= daysToInspect; offset++) {
            LocalDate candidateDate = localNow.toLocalDate().minusDays(offset);
            if (!runsOn(recurrence, silence.getRecurrenceDays(), candidateDate)) {
                continue;
            }
            ZonedDateTime candidateStart = resolve(zone, candidateDate, seedStart.toLocalTime());
            ZonedDateTime candidateEnd = resolveEnd(zone, candidateStart, wallDuration);
            Instant start = candidateStart.toInstant();
            Instant end = candidateEnd.toInstant();
            Instant scheduleStart = silence.getStartsAt().toInstant(ZoneOffset.UTC);
            Instant scheduleEnd = silence.getRecurrenceUntil().toInstant(ZoneOffset.UTC);
            if (start.isBefore(scheduleStart) || !start.isBefore(scheduleEnd)) {
                continue;
            }
            if (end.isAfter(scheduleEnd)) {
                end = scheduleEnd;
            }
            if (!now.isBefore(start) && now.isBefore(end)) {
                return LocalDateTime.ofInstant(end, ZoneOffset.UTC);
            }
        }
        return null;
    }

    private static boolean runsOn(AlertSilenceRecurrence recurrence, Set<Integer> recurrenceDays,
            LocalDate candidateDate) {
        return recurrence == AlertSilenceRecurrence.DAILY
                || recurrenceDays != null && recurrenceDays.contains(candidateDate.getDayOfWeek().getValue());
    }

    private static ZonedDateTime resolve(ZoneId zone, LocalDate date, LocalTime time) {
        return ZonedDateTime.of(LocalDateTime.of(date, time), zone);
    }

    private static ZonedDateTime resolveEnd(ZoneId zone, ZonedDateTime start, Duration wallDuration) {
        return ZonedDateTime.of(start.toLocalDateTime().plus(wallDuration), zone);
    }

    private static boolean isInside(LocalDateTime now, LocalDateTime start, LocalDateTime end) {
        return !now.isBefore(start) && now.isBefore(end);
    }
}
