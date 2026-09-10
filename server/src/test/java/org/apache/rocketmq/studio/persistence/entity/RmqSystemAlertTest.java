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

class RmqSystemAlertTest {

    @Test
    void freshEntityCarriesNullFields() {
        RmqSystemAlert entity = new RmqSystemAlert();

        assertNull(entity.getId());
        assertNull(entity.getLevel());
        assertNull(entity.getTitle());
        assertNull(entity.getTime());
        assertNull(entity.getAcknowledged());
        assertNull(entity.getDomain());
        assertNull(entity.getTransition());
    }

    @Test
    void settersRoundTripEveryField() {
        RmqSystemAlert entity = new RmqSystemAlert();
        LocalDateTime time = LocalDateTime.parse("2026-09-01T08:00:00");
        LocalDateTime ackedAt = LocalDateTime.parse("2026-09-01T09:00:00");

        entity.setId(21L);
        entity.setLevel("critical");
        entity.setTitle("Broker down");
        entity.setDescription("broker-a unreachable");
        entity.setTime(time);
        entity.setAcknowledged(true);
        entity.setAcknowledgedBy("alice");
        entity.setAcknowledgedAt(ackedAt);
        entity.setDomain("CLUSTER");
        entity.setRuleId(7L);
        entity.setFingerprint("fp-1");
        entity.setTransition("FIRING");
        entity.setInstanceId("inst-1");
        entity.setGmtCreate(time);
        entity.setGmtModified(time);

        assertEquals(21L, entity.getId());
        assertEquals("critical", entity.getLevel());
        assertEquals("Broker down", entity.getTitle());
        assertEquals(Boolean.TRUE, entity.getAcknowledged());
        assertEquals("CLUSTER", entity.getDomain());
        assertEquals("FIRING", entity.getTransition());
        assertEquals("inst-1", entity.getInstanceId());
        assertEquals(time, entity.getGmtCreate());
    }
}
