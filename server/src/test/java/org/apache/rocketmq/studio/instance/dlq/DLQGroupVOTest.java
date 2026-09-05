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
package org.apache.rocketmq.studio.instance.dlq;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DLQGroupVOTest {

    @Test
    void builderDefaultsDescribeFreshGroup() {
        DLQGroupVO vo = DLQGroupVO.builder().build();

        assertNull(vo.getGroupName());
        assertNull(vo.getDlqTopic());
        assertEquals(0L, vo.getMessageCount());
        assertNull(vo.getLastEnqueueTime());
        assertEquals(0, vo.getRetryCount());
        assertNull(vo.getStatus());
        assertTrue(vo.isStatsAvailable());
    }

    @Test
    void allArgsCarryDlqGroupState() {
        LocalDateTime enqueued = LocalDateTime.parse("2026-09-01T08:00:00");

        DLQGroupVO vo = DLQGroupVO.builder()
            .groupName("cg-orders")
            .dlqTopic("%DLQ%cg-orders")
            .messageCount(5L)
            .lastEnqueueTime(enqueued)
            .retryCount(2)
            .status("RUNNING")
            .statsAvailable(false)
            .build();

        assertEquals("cg-orders", vo.getGroupName());
        assertEquals("%DLQ%cg-orders", vo.getDlqTopic());
        assertEquals(5L, vo.getMessageCount());
        assertEquals(enqueued, vo.getLastEnqueueTime());
        assertEquals(2, vo.getRetryCount());
        assertEquals("RUNNING", vo.getStatus());
        assertFalse(vo.isStatsAvailable());
    }
}
