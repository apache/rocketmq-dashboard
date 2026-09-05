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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QueueOffsetVOTest {

    @Test
    void builderDefaultsDescribeUnsetOffsets() {
        QueueOffsetVO vo = QueueOffsetVO.builder().build();

        assertNull(vo.getBrokerName());
        assertEquals(0, vo.getQueueId());
        assertEquals(0L, vo.getMinOffset());
        assertEquals(0L, vo.getMaxOffset());
    }

    @Test
    void allArgsCarryQueueOffsetState() {
        QueueOffsetVO vo = new QueueOffsetVO("broker-a", 3, 100L, 200L);

        assertEquals("broker-a", vo.getBrokerName());
        assertEquals(3, vo.getQueueId());
        assertEquals(100L, vo.getMinOffset());
        assertEquals(200L, vo.getMaxOffset());
    }
}
