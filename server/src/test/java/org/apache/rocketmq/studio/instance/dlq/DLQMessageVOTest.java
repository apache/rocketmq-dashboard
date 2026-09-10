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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DLQMessageVOTest {

    @Test
    void builderDefaultsDescribeEmptyMessage() {
        DLQMessageVO vo = DLQMessageVO.builder().build();

        assertNull(vo.getMsgId());
        assertNull(vo.getTopic());
        assertEquals(0, vo.getQueueId());
        assertEquals(0L, vo.getOffset());
        assertEquals(0L, vo.getStoreTime());
        assertNull(vo.getKeys());
        assertNull(vo.getBody());
        assertNull(vo.getBodyBase64());
    }

    @Test
    void allArgsCarryDlqMessageState() {
        DLQMessageVO vo = DLQMessageVO.builder()
            .msgId("msg-1")
            .topic("%DLQ%cg-orders")
            .queueId(3)
            .offset(100L)
            .storeTime(1784246400000L)
            .keys("order-1")
            .body("hello")
            .bodyBase64("aGVsbG8=")
            .build();

        assertEquals("msg-1", vo.getMsgId());
        assertEquals("%DLQ%cg-orders", vo.getTopic());
        assertEquals(3, vo.getQueueId());
        assertEquals(100L, vo.getOffset());
        assertEquals(1784246400000L, vo.getStoreTime());
        assertEquals("order-1", vo.getKeys());
        assertEquals("hello", vo.getBody());
        assertEquals("aGVsbG8=", vo.getBodyBase64());
    }
}
