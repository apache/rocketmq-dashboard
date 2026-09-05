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

class RmqAlertNotificationOutboxTest {

    @Test
    void freshEntityCarriesNullFields() {
        RmqAlertNotificationOutbox entity = new RmqAlertNotificationOutbox();

        assertNull(entity.getId());
        assertNull(entity.getAlertId());
        assertNull(entity.getChannel());
        assertNull(entity.getStatus());
        assertNull(entity.getAttemptCount());
        assertNull(entity.getClaimToken());
        assertNull(entity.getDeliveredAt());
    }

    @Test
    void settersRoundTripEveryField() {
        RmqAlertNotificationOutbox entity = new RmqAlertNotificationOutbox();
        LocalDateTime next = LocalDateTime.parse("2026-09-01T08:00:00");

        entity.setId(1L);
        entity.setAlertId(21L);
        entity.setChannel("email");
        entity.setStatus("RETRY_WAIT");
        entity.setAttemptCount(2);
        entity.setNextAttemptAt(next);
        entity.setSendingStartedAt(next);
        entity.setClaimToken("token-1");
        entity.setLastError("timeout");
        entity.setMessageContent("{\"title\":\"Broker down\"}");
        entity.setDeliveredAt(null);
        entity.setGmtModified(next);

        assertEquals(1L, entity.getId());
        assertEquals(21L, entity.getAlertId());
        assertEquals("email", entity.getChannel());
        assertEquals("RETRY_WAIT", entity.getStatus());
        assertEquals(2, entity.getAttemptCount());
        assertEquals("token-1", entity.getClaimToken());
        assertEquals("timeout", entity.getLastError());
        assertEquals(next, entity.getNextAttemptAt());
    }
}
