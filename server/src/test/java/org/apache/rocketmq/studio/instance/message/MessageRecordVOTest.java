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

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageRecordVOTest {

    @Test
    void builderDefaultsDescribeEmptyRecord() {
        MessageRecordVO vo = MessageRecordVO.builder().build();

        assertNull(vo.getMsgId());
        assertNull(vo.getQueueId());
        assertNull(vo.getQueueOffset());
        assertNull(vo.getBody());
        assertFalse(vo.isBodyTruncated());
        assertEquals(0L, vo.getStoreTime());
        assertNull(vo.getProperties());
        assertFalse(vo.isPropertiesTruncated());
        assertEquals(0, vo.getSize());
    }

    @Test
    void allArgsCarryMessageState() {
        MessageRecordVO vo = MessageRecordVO.builder()
            .msgId("msg-1")
            .topic("orders")
            .tag("created")
            .key("order-1")
            .brokerName("broker-a")
            .queueId(3)
            .queueOffset(100L)
            .body("hello")
            .bodyEncoding("UTF-8")
            .bodyTruncated(true)
            .storeTime(1784246400000L)
            .bornHost("10.0.0.1")
            .storeHost("broker-a")
            .properties(Map.of("KEYS", "order-1"))
            .propertiesTruncated(false)
            .size(128)
            .build();

        assertEquals("msg-1", vo.getMsgId());
        assertEquals("orders", vo.getTopic());
        assertEquals(3, vo.getQueueId());
        assertEquals(100L, vo.getQueueOffset());
        assertTrue(vo.isBodyTruncated());
        assertEquals(Map.of("KEYS", "order-1"), vo.getProperties());
        assertEquals(128, vo.getSize());
    }
}
