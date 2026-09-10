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
package org.apache.rocketmq.studio.instance.topic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SendMessageVOTest {

    @Test
    void builderDefaultsDescribeEmptySend() {
        SendMessageVO vo = SendMessageVO.builder().build();

        assertNull(vo.getMsgId());
        assertEquals(0L, vo.getSendTime());
        assertNull(vo.getOffsetMsgId());
    }

    @Test
    void allArgsCarrySendState() {
        SendMessageVO vo = SendMessageVO.builder()
            .msgId("msg-1")
            .sendTime(1784246400000L)
            .offsetMsgId("commit-1")
            .build();

        assertEquals("msg-1", vo.getMsgId());
        assertEquals(1784246400000L, vo.getSendTime());
        assertEquals("commit-1", vo.getOffsetMsgId());
    }
}
