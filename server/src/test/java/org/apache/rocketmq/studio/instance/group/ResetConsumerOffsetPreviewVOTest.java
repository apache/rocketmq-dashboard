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
package org.apache.rocketmq.studio.instance.group;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResetConsumerOffsetPreviewVOTest {

    @Test
    void builderDefaultsDescribeEmptyPreview() {
        ResetConsumerOffsetPreviewVO vo = ResetConsumerOffsetPreviewVO.builder().build();

        assertNull(vo.getInstanceId());
        assertNull(vo.getGroupName());
        assertNull(vo.getTopic());
        assertEquals(0L, vo.getTimestamp());
        assertFalse(vo.isComplete());
        assertFalse(vo.isAllowReset());
        assertEquals(0, vo.getQueueCount());
        assertEquals(0L, vo.getCurrentTotalLag());
        assertEquals(0L, vo.getTotalOffsetDelta());
        assertNull(vo.getWarnings());
        assertNull(vo.getQueues());
    }

    @Test
    void allArgsCarryPreviewState() {
        ResetConsumerOffsetPreviewVO vo = ResetConsumerOffsetPreviewVO.builder()
            .instanceId("inst-1")
            .groupName("cg-orders")
            .topic("orders")
            .timestamp(1784246400000L)
            .complete(true)
            .allowReset(true)
            .queueCount(4)
            .warningCount(0)
            .rewindQueueCount(1)
            .fastForwardQueueCount(1)
            .currentTotalLag(500L)
            .projectedTotalLag(10L)
            .totalOffsetDelta(-490L)
            .warnings(List.of())
            .queues(List.of())
            .build();

        assertEquals("cg-orders", vo.getGroupName());
        assertEquals(1784246400000L, vo.getTimestamp());
        assertTrue(vo.isComplete());
        assertTrue(vo.isAllowReset());
        assertEquals(4, vo.getQueueCount());
        assertEquals(500L, vo.getCurrentTotalLag());
        assertEquals(-490L, vo.getTotalOffsetDelta());
        assertEquals(List.of(), vo.getQueues());
    }
}
