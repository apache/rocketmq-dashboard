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

import org.apache.rocketmq.studio.common.domain.enums.Protocol;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConsumerInstanceVOTest {

    @Test
    void builderDefaultsDescribeEmptyInstance() {
        ConsumerInstanceVO vo = ConsumerInstanceVO.builder().build();

        assertNull(vo.getClientId());
        assertNull(vo.getProtocol());
        assertNull(vo.getAddress());
        assertNull(vo.getSubscribedTopics());
        assertNull(vo.getLastHeartbeat());
        assertNull(vo.getTopicLag());
    }

    @Test
    void allArgsCarryConsumerInstanceState() {
        LocalDateTime heartbeat = LocalDateTime.parse("2026-09-01T08:00:00");

        ConsumerInstanceVO vo = ConsumerInstanceVO.builder()
            .clientId("client-1")
            .protocol(Protocol.gRPC)
            .address("10.0.0.1:8081")
            .subscribedTopics(List.of("orders"))
            .lastHeartbeat(heartbeat)
            .topicLag(Map.of("orders", 5L))
            .build();

        assertEquals("client-1", vo.getClientId());
        assertEquals(Protocol.gRPC, vo.getProtocol());
        assertEquals("10.0.0.1:8081", vo.getAddress());
        assertEquals(List.of("orders"), vo.getSubscribedTopics());
        assertEquals(heartbeat, vo.getLastHeartbeat());
        assertEquals(Map.of("orders", 5L), vo.getTopicLag());
    }
}
