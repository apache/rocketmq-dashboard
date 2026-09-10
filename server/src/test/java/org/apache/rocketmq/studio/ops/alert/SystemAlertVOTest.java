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

import org.apache.rocketmq.studio.common.domain.enums.AlertLevel;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemAlertVOTest {

    @Test
    void builderDefaultsDescribeFreshAlert() {
        SystemAlertVO vo = SystemAlertVO.builder().build();

        assertEquals(Map.of(), vo.getLabels());
        assertFalse(vo.isAcknowledged());
        assertFalse(vo.isNotificationSuppressed());
        assertTrue(vo.getLabels().isEmpty());
    }

    @Test
    void allArgsCarryAlertState() {
        LocalDateTime time = LocalDateTime.parse("2026-09-01T08:00:00");
        LocalDateTime ackedAt = LocalDateTime.parse("2026-09-01T09:00:00");

        SystemAlertVO vo = SystemAlertVO.builder()
            .id(21L)
            .level(AlertLevel.warning)
            .title("Broker down")
            .description("broker-a is unreachable")
            .time(time)
            .acknowledged(true)
            .acknowledgedBy("alice")
            .acknowledgedAt(ackedAt)
            .domain(AlertDomain.CLUSTER)
            .ruleId(7L)
            .fingerprint("fp-1")
            .transition("FIRING")
            .instanceId("inst-1")
            .currentValue(0.0)
            .notificationSuppressed(true)
            .suppressionCauseAlertId(9L)
            .suppressionReason("maintenance window")
            .labels(Map.of("node", "broker-a"))
            .build();

        assertEquals(21L, vo.getId());
        assertEquals(AlertLevel.warning, vo.getLevel());
        assertEquals("Broker down", vo.getTitle());
        assertEquals(time, vo.getTime());
        assertTrue(vo.isAcknowledged());
        assertEquals("alice", vo.getAcknowledgedBy());
        assertEquals(AlertDomain.CLUSTER, vo.getDomain());
        assertEquals("FIRING", vo.getTransition());
        assertEquals(Map.of("node", "broker-a"), vo.getLabels());
        assertTrue(vo.isNotificationSuppressed());
    }
}
