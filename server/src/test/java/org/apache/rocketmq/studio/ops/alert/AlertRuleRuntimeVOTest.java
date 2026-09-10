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

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AlertRuleRuntimeVOTest {

    @Test
    void builderDefaultsDescribeUnobservedRule() {
        AlertRuleRuntimeVO vo = AlertRuleRuntimeVO.builder().build();

        assertNull(vo.getRuleId());
        assertNull(vo.getFingerprint());
        assertNull(vo.getStatus());
        assertEquals(0, vo.getConsecutiveHits());
        assertNull(vo.getCurrentValue());
        assertNull(vo.getLastNotifiedAt());
        assertNull(vo.getNextReminderAt());
    }

    @Test
    void allArgsCarryRuntimeState() {
        LocalDateTime notified = LocalDateTime.parse("2026-09-01T08:00:00");
        LocalDateTime reminder = LocalDateTime.parse("2026-09-01T09:00:00");

        AlertRuleRuntimeVO vo = AlertRuleRuntimeVO.builder()
            .ruleId(7L)
            .fingerprint("fp-1")
            .status(AlertStateStatus.FIRING)
            .consecutiveHits(3)
            .currentValue(98.5)
            .lastNotifiedAt(notified)
            .nextReminderAt(reminder)
            .build();

        assertEquals(7L, vo.getRuleId());
        assertEquals("fp-1", vo.getFingerprint());
        assertEquals(AlertStateStatus.FIRING, vo.getStatus());
        assertEquals(3, vo.getConsecutiveHits());
        assertEquals(98.5, vo.getCurrentValue());
        assertEquals(notified, vo.getLastNotifiedAt());
        assertEquals(reminder, vo.getNextReminderAt());
    }
}
