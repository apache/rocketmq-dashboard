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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AlertSilenceVOTest {

    @Test
    void builderDefaultsDescribeFreshSilence() {
        AlertSilenceVO vo = AlertSilenceVO.builder().build();

        assertNull(vo.getId());
        assertNull(vo.getDomain());
        assertNull(vo.getRuleId());
        assertNull(vo.getLabels());
        assertNull(vo.getStartsAt());
        assertNull(vo.getEndsAt());
        assertNull(vo.getRecurrence());
        assertNull(vo.getRecurrenceDays());
        assertNull(vo.getCreatedBy());
    }

    @Test
    void allArgsCarrySilenceState() {
        LocalDateTime starts = LocalDateTime.parse("2026-09-01T08:00:00");
        LocalDateTime ends = LocalDateTime.parse("2026-09-01T10:00:00");

        AlertSilenceVO vo = AlertSilenceVO.builder()
            .id(3L)
            .domain(AlertDomain.CLUSTER)
            .ruleId(7L)
            .instanceId("inst-1")
            .labels(Map.of("node", "broker-a"))
            .startsAt(starts)
            .endsAt(ends)
            .recurrence(AlertSilenceRecurrence.WEEKLY)
            .timeZone("Asia/Shanghai")
            .recurrenceDays(Set.of(1, 3))
            .recurrenceUntil(ends)
            .reason("maintenance")
            .createdBy("alice")
            .build();

        assertEquals(3L, vo.getId());
        assertEquals(AlertDomain.CLUSTER, vo.getDomain());
        assertEquals(Map.of("node", "broker-a"), vo.getLabels());
        assertEquals(AlertSilenceRecurrence.WEEKLY, vo.getRecurrence());
        assertEquals(Set.of(1, 3), vo.getRecurrenceDays());
        assertEquals("Asia/Shanghai", vo.getTimeZone());
        assertEquals("maintenance", vo.getReason());
        assertEquals("alice", vo.getCreatedBy());
    }
}
