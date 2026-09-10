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
package org.apache.rocketmq.studio.persistence.entity;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RmqAlertSilenceTest {

    @Test
    void freshEntityCarriesNullFields() {
        RmqAlertSilence entity = new RmqAlertSilence();

        assertNull(entity.getId());
        assertNull(entity.getDomain());
        assertNull(entity.getRuleId());
        assertNull(entity.getStartsAt());
        assertNull(entity.getEndsAt());
        assertNull(entity.getRecurrence());
        assertNull(entity.getCreatedBy());
    }

    @Test
    void settersRoundTripEveryField() {
        RmqAlertSilence entity = new RmqAlertSilence();
        LocalDateTime starts = LocalDateTime.parse("2026-09-01T08:00:00");
        LocalDateTime ends = LocalDateTime.parse("2026-09-01T10:00:00");

        entity.setId(3L);
        entity.setDomain("CLUSTER");
        entity.setRuleId(7L);
        entity.setInstanceId("inst-1");
        entity.setLabelsJson("{\"node\":\"broker-a\"}");
        entity.setStartsAt(starts);
        entity.setEndsAt(ends);
        entity.setRecurrence("WEEKLY");
        entity.setTimeZone("Asia/Shanghai");
        entity.setRecurrenceDaysJson("[1,3]");
        entity.setRecurrenceUntil(ends);
        entity.setReason("maintenance");
        entity.setCreatedBy("alice");

        assertEquals(3L, entity.getId());
        assertEquals("CLUSTER", entity.getDomain());
        assertEquals(7L, entity.getRuleId());
        assertEquals(starts, entity.getStartsAt());
        assertEquals(ends, entity.getEndsAt());
        assertEquals("WEEKLY", entity.getRecurrence());
        assertEquals("Asia/Shanghai", entity.getTimeZone());
        assertEquals("maintenance", entity.getReason());
        assertEquals("alice", entity.getCreatedBy());
    }
}
