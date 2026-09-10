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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QueueProgressVOTest {

    @Test
    void builderDefaultsDescribeEmptyProgress() {
        QueueProgressVO vo = QueueProgressVO.builder().build();

        assertNull(vo.getTopic());
        assertNull(vo.getBroker());
        assertEquals(0, vo.getQueueId());
        assertEquals(0L, vo.getBrokerOffset());
        assertEquals(0L, vo.getConsumerOffset());
        assertEquals(0L, vo.getDiffTotal());
    }

    @Test
    void allArgsCarryProgressState() {
        QueueProgressVO vo = QueueProgressVO.builder()
            .topic("orders")
            .broker("broker-a")
            .queueId(3)
            .brokerOffset(1000L)
            .consumerOffset(900L)
            .diffTotal(100L)
            .build();

        assertEquals("orders", vo.getTopic());
        assertEquals("broker-a", vo.getBroker());
        assertEquals(3, vo.getQueueId());
        assertEquals(1000L, vo.getBrokerOffset());
        assertEquals(900L, vo.getConsumerOffset());
        assertEquals(100L, vo.getDiffTotal());
    }
}
