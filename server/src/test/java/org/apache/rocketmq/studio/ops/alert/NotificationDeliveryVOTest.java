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

class NotificationDeliveryVOTest {

    @Test
    void builderDefaultsDescribeFreshDelivery() {
        NotificationDeliveryVO vo = NotificationDeliveryVO.builder().build();

        assertNull(vo.getId());
        assertNull(vo.getChannel());
        assertNull(vo.getStatus());
        assertEquals(0, vo.getAttemptCount());
        assertNull(vo.getNextAttemptAt());
        assertNull(vo.getLastError());
        assertNull(vo.getDeliveredAt());
    }

    @Test
    void allArgsCarryDeliveryState() {
        LocalDateTime next = LocalDateTime.parse("2026-09-01T08:00:00");
        LocalDateTime delivered = LocalDateTime.parse("2026-09-01T08:05:00");

        NotificationDeliveryVO vo = NotificationDeliveryVO.builder()
            .id(1L)
            .channel("email")
            .status(NotificationOutboxStatus.DELIVERED)
            .attemptCount(2)
            .nextAttemptAt(next)
            .lastError(null)
            .deliveredAt(delivered)
            .build();

        assertEquals(1L, vo.getId());
        assertEquals("email", vo.getChannel());
        assertEquals(NotificationOutboxStatus.DELIVERED, vo.getStatus());
        assertEquals(2, vo.getAttemptCount());
        assertEquals(next, vo.getNextAttemptAt());
        assertEquals(delivered, vo.getDeliveredAt());
    }
}
