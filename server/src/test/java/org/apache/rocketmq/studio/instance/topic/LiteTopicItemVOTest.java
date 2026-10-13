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
 * Pins the lite-topic pattern row of {@link LiteTopicItemVO}: one parent pattern with its
 * aggregated counts, backlog, TTL verdict and the sessions live under it.
 */
class LiteTopicItemVOTest {

    @Test
    void thePatternRowRoundTripsThroughTheBuilder() {
        LiteTopicItemVO item = LiteTopicItemVO.builder()
                .topicPattern("orders-*")
                .namespace("ns-1")
                .topicCount(12)
                .consumerCount(3)
                .totalBacklog(4500L)
                .averageTTL(3600L)
                .ttlStatus("expiring")
                .lastActiveTime(1784112606000L)
                .sessionIds(List.of("sess-1", "sess-2"))
                .build();

        assertThat(item.getTopicPattern()).isEqualTo("orders-*");
        assertThat(item.getNamespace()).isEqualTo("ns-1");
        assertThat(item.getTopicCount()).isEqualTo(12);
        assertThat(item.getConsumerCount()).isEqualTo(3);
        assertThat(item.getTotalBacklog()).isEqualTo(4500L);
        assertThat(item.getAverageTTL()).isEqualTo(3600L);
        assertThat(item.getTtlStatus()).isEqualTo("expiring");
        assertThat(item.getLastActiveTime()).isEqualTo(1784112606000L);
        assertThat(item.getSessionIds()).containsExactly("sess-1", "sess-2");
    }

    @Test
    void aMinimalPatternRowCarriesOnlyThePattern() {
        LiteTopicItemVO item = LiteTopicItemVO.builder().topicPattern("orders-*").build();
        assertThat(item.getSessionIds()).isNull();
        assertThat(item.getTopicCount()).isNull();
        assertThat(item.getTotalBacklog()).isNull();
    }
}
