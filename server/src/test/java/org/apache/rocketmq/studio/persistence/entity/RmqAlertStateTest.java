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

class RmqAlertStateTest {

    @Test
    void freshEntityCarriesNullFields() {
        RmqAlertState entity = new RmqAlertState();

        assertNull(entity.getId());
        assertNull(entity.getRuleId());
        assertNull(entity.getFingerprint());
        assertNull(entity.getStatus());
        assertNull(entity.getConsecutiveHits());
        assertNull(entity.getCurrentValue());
        assertNull(entity.getVersion());
    }

    @Test
    void settersRoundTripEveryField() {
        RmqAlertState entity = new RmqAlertState();
        LocalDateTime fired = LocalDateTime.parse("2026-09-01T08:00:00");

        entity.setId(1L);
        entity.setRuleId(7L);
        entity.setFingerprint("fp-1");
        entity.setStatus("FIRING");
        entity.setConsecutiveHits(3);
        entity.setCurrentValue(98.5);
        entity.setFirstPendingAt(fired);
        entity.setFiredAt(fired);
        entity.setLastNotifiedAt(fired);
        entity.setResolvedAt(null);
        entity.setVersion(2);
        entity.setGmtModified(fired);

        assertEquals(1L, entity.getId());
        assertEquals(7L, entity.getRuleId());
        assertEquals("fp-1", entity.getFingerprint());
        assertEquals("FIRING", entity.getStatus());
        assertEquals(3, entity.getConsecutiveHits());
        assertEquals(98.5, entity.getCurrentValue());
        assertEquals(fired, entity.getFiredAt());
        assertEquals(2, entity.getVersion());
        assertEquals(fired, entity.getGmtModified());
    }
}
