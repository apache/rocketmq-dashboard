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

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LiteTopicSessionVOTest {

    @Test
    void builderDefaultsDescribeEmptySession() {
        LiteTopicSessionVO vo = LiteTopicSessionVO.builder().build();

        assertNull(vo.getSessionId());
        assertNull(vo.getClientId());
        assertNull(vo.getParentTopic());
        assertNull(vo.getCreateTime());
        assertNull(vo.getTtl());
        assertNull(vo.getStatus());
        assertNull(vo.getLiteTopics());
    }

    @Test
    void allArgsCarrySessionState() {
        LiteTopicSessionVO.SessionLiteTopic liteTopic = new LiteTopicSessionVO.SessionLiteTopic(
                "order-1", "ACTIVE", 1200L);

        LiteTopicSessionVO vo = LiteTopicSessionVO.builder()
            .sessionId("s1")
            .clientId("client-1")
            .clientAddress("10.0.0.1:8080")
            .parentTopic("orders")
            .consumerGroup("cg-orders")
            .createTime(1784246400000L)
            .lastActiveTime(1784246400000L)
            .ttl(3600L)
            .ttlRemaining(1800L)
            .status("ACTIVE")
            .totalMessages(10L)
            .consumedMessages(6L)
            .pendingMessages(4L)
            .popProgress(60)
            .liteTopicCreationCount(1)
            .liteTopics(List.of(liteTopic))
            .build();

        assertEquals("s1", vo.getSessionId());
        assertEquals("orders", vo.getParentTopic());
        assertEquals(3600L, vo.getTtl());
        assertEquals(1800L, vo.getTtlRemaining());
        assertEquals(60, vo.getPopProgress());
        assertEquals("order-1", vo.getLiteTopics().get(0).getTopicName());
        assertEquals(1200L, vo.getLiteTopics().get(0).getTtlRemaining());
    }
}
