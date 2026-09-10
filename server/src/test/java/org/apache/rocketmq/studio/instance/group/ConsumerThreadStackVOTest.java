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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConsumerThreadStackVOTest {

    @Test
    void builderDefaultsDescribeEmptyStack() {
        ConsumerThreadStackVO vo = ConsumerThreadStackVO.builder().build();

        assertNull(vo.getThreadName());
        assertEquals(0L, vo.getThreadId());
        assertNull(vo.getState());
        assertEquals(0L, vo.getBlockedTime());
        assertEquals(0L, vo.getWaitedTime());
        assertNull(vo.getStackTrace());
    }

    @Test
    void allArgsCarryThreadStackState() {
        ConsumerThreadStackVO vo = ConsumerThreadStackVO.builder()
            .threadName("consume-1")
            .threadId(42L)
            .state("WAITING")
            .blockedTime(100L)
            .waitedTime(200L)
            .stackTrace(List.of("at com.example.Consumer.run()"))
            .build();

        assertEquals("consume-1", vo.getThreadName());
        assertEquals(42L, vo.getThreadId());
        assertEquals("WAITING", vo.getState());
        assertEquals(100L, vo.getBlockedTime());
        assertEquals(200L, vo.getWaitedTime());
        assertEquals(List.of("at com.example.Consumer.run()"), vo.getStackTrace());
    }
}
