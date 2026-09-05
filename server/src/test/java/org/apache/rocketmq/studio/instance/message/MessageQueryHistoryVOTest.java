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

class MessageQueryHistoryVOTest {

    @Test
    void builderDefaultsDescribeEmptyHistoryRow() {
        MessageQueryHistoryVO vo = MessageQueryHistoryVO.builder().build();

        assertNull(vo.getId());
        assertNull(vo.getQueryType());
        assertNull(vo.getTopic());
        assertEquals(0, vo.getResultCount());
        assertNull(vo.getQueriedAt());
    }

    @Test
    void allArgsCarryHistoryRow() {
        LocalDateTime queried = LocalDateTime.parse("2026-09-01T08:00:00");

        MessageQueryHistoryVO vo = MessageQueryHistoryVO.builder()
            .id(1L)
            .queryType("TOPIC")
            .topic("orders")
            .msgId("msg-1")
            .tag("created")
            .messageKey("order-1")
            .startTime(1784246400000L)
            .endTime(1784332800000L)
            .resultCount(2)
            .clusterId("cluster-1")
            .queriedBy("alice")
            .queriedAt(queried)
            .build();

        assertEquals(1L, vo.getId());
        assertEquals("TOPIC", vo.getQueryType());
        assertEquals("orders", vo.getTopic());
        assertEquals(2, vo.getResultCount());
        assertEquals("alice", vo.getQueriedBy());
        assertEquals(queried, vo.getQueriedAt());
    }
}
