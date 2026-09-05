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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NotificationDeliveryPageVOTest {

    @Test
    void builderDefaultsDescribeFreshDeliveryRow() {
        NotificationDeliveryPageVO vo = NotificationDeliveryPageVO.builder().build();

        assertNull(vo.getId());
        assertNull(vo.getAlertId());
        assertNull(vo.getChannel());
        assertNull(vo.getStatus());
        assertEquals(0, vo.getAttemptCount());
        assertNull(vo.getAlertTitle());
        assertNull(vo.getAlertDomain());
        assertNull(vo.getInstanceId());
    }

    @Test
    void allArgsCarryDeliveryRowState() {
        LocalDateTime next = LocalDateTime.parse("2026-09-01T08:00:00");
        LocalDateTime delivered = LocalDateTime.parse("2026-09-01T08:05:00");

        NotificationDeliveryPageVO vo = NotificationDeliveryPageVO.builder()
            .id(2L)
            .alertId(21L)
            .channel("sms")
            .status(NotificationOutboxStatus.DELIVERED)
            .attemptCount(1)
            .nextAttemptAt(next)
            .lastError(null)
            .deliveredAt(delivered)
            .createdAt(next)
            .messageContent("{\"title\":\"Broker down\"}")
            .alertTitle("Broker down")
            .alertDomain(AlertDomain.CLUSTER)
            .transition("FIRING")
            .instanceId("inst-1")
            .build();

        assertEquals(2L, vo.getId());
        assertEquals(21L, vo.getAlertId());
        assertEquals("sms", vo.getChannel());
        assertEquals(NotificationOutboxStatus.DELIVERED, vo.getStatus());
        assertEquals(1, vo.getAttemptCount());
        assertEquals("Broker down", vo.getAlertTitle());
        assertEquals(AlertDomain.CLUSTER, vo.getAlertDomain());
        assertEquals("inst-1", vo.getInstanceId());
        assertEquals(delivered, vo.getDeliveredAt());
    }
}
