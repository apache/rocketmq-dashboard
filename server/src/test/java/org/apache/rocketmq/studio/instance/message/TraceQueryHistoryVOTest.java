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
package org.apache.rocketmq.studio.instance.message;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TraceQueryHistoryVOTest {

    @Test
    void builderDefaultsDescribeEmptyHistoryRow() {
        TraceQueryHistoryVO vo = TraceQueryHistoryVO.builder().build();

        assertNull(vo.getId());
        assertNull(vo.getMsgId());
        assertNull(vo.getTopic());
        assertEquals(0, vo.getNodeCount());
        assertEquals(0, vo.getConsumerCount());
        assertNull(vo.getQueriedAt());
    }

    @Test
    void allArgsCarryHistoryRow() {
        LocalDateTime queried = LocalDateTime.parse("2026-09-01T08:00:00");

        TraceQueryHistoryVO vo = TraceQueryHistoryVO.builder()
            .id(2L)
            .msgId("msg-1")
            .topic("orders")
            .traceTopic("RMQ_SYS_TRACE_TOPIC")
            .nodeCount(5)
            .consumerCount(2)
            .clusterId("cluster-1")
            .queriedBy("alice")
            .queriedAt(queried)
            .build();

        assertEquals(2L, vo.getId());
        assertEquals("msg-1", vo.getMsgId());
        assertEquals("RMQ_SYS_TRACE_TOPIC", vo.getTraceTopic());
        assertEquals(5, vo.getNodeCount());
        assertEquals(2, vo.getConsumerCount());
        assertEquals(queried, vo.getQueriedAt());
    }
}
