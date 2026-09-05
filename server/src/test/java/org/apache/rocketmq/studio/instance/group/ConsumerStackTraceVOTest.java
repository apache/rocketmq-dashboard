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

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConsumerStackTraceVOTest {

    @Test
    void builderDefaultsDescribeEmptyStack() {
        ConsumerStackTraceVO vo = ConsumerStackTraceVO.builder().build();

        assertNull(vo.getGroupName());
        assertNull(vo.getClientId());
        assertNull(vo.getCapturedAt());
        assertEquals(0, vo.getThreadCount());
        assertNull(vo.getThreads());
    }

    @Test
    void allArgsCarryStackTraceState() {
        LocalDateTime captured = LocalDateTime.parse("2026-09-01T08:00:00");

        ConsumerStackTraceVO vo = ConsumerStackTraceVO.builder()
            .groupName("cg-orders")
            .clientId("client-1")
            .capturedAt(captured)
            .threadCount(3)
            .threads(List.of())
            .build();

        assertEquals("cg-orders", vo.getGroupName());
        assertEquals("client-1", vo.getClientId());
        assertEquals(captured, vo.getCapturedAt());
        assertEquals(3, vo.getThreadCount());
        assertEquals(List.of(), vo.getThreads());
    }
}
