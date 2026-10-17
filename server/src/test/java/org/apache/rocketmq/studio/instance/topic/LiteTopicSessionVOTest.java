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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the lite topic session view of {@link LiteTopicSessionVO}: one lightweight session's
 * identity, liveness window, TTL countdown and consumption progress round-trip through the
 * builder.
 */
class LiteTopicSessionVOTest {

    @Test
    void theSessionRoundTripsThroughTheBuilder() {
        LiteTopicSessionVO.SessionLiteTopic liteTopic = new LiteTopicSessionVO.SessionLiteTopic();
        LiteTopicSessionVO session = LiteTopicSessionVO.builder()
                .sessionId("sess-1")
                .clientId("client-1")
                .clientAddress("10.0.0.1:10920")
                .parentTopic("orders")
                .consumerGroup("order-group")
                .createTime(1784110000000L)
                .lastActiveTime(1784112606000L)
                .ttl(86400000L)
                .ttlRemaining(3600000L)
                .status("ACTIVE")
                .totalMessages(1000L)
                .consumedMessages(900L)
                .pendingMessages(100L)
                .popProgress(50)
                .liteTopicCreationCount(3)
                .liteTopics(List.of(liteTopic))
                .build();

        assertThat(session.getSessionId()).isEqualTo("sess-1");
        assertThat(session.getClientId()).isEqualTo("client-1");
        assertThat(session.getClientAddress()).isEqualTo("10.0.0.1:10920");
        assertThat(session.getParentTopic()).isEqualTo("orders");
        assertThat(session.getConsumerGroup()).isEqualTo("order-group");
        assertThat(session.getTtl()).isEqualTo(86400000L);
        assertThat(session.getTtlRemaining()).isEqualTo(3600000L);
        assertThat(session.getStatus()).isEqualTo("ACTIVE");
        assertThat(session.getTotalMessages()).isEqualTo(1000L);
        assertThat(session.getConsumedMessages()).isEqualTo(900L);
        assertThat(session.getPendingMessages()).isEqualTo(100L);
        assertThat(session.getPopProgress()).isEqualTo(50);
        assertThat(session.getLiteTopicCreationCount()).isEqualTo(3);
        assertThat(session.getLiteTopics()).hasSize(1);
    }

    @Test
    void aFreshSessionCountsNothing() {
        LiteTopicSessionVO session = LiteTopicSessionVO.builder().build();
        assertThat(session.getTotalMessages()).isNull();
        assertThat(session.getPendingMessages()).isNull();
        assertThat(session.getLiteTopics()).isNull();
    }
}
