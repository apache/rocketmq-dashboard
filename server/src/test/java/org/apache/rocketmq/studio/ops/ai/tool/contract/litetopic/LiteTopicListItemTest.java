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
package org.apache.rocketmq.studio.ops.ai.tool.contract.litetopic;

import org.apache.rocketmq.studio.instance.topic.LiteTopicItemVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link LiteTopicListItem}: the pattern-level LiteTopic aggregate. Statistics the broker
 * cannot report must stay absent (null) instead of being zero-filled — an unknown backlog is
 * not a zero backlog.
 */
class LiteTopicListItemTest {

    @Test
    void mapsEveryAggregateField() {
        LiteTopicItemVO vo = LiteTopicItemVO.builder()
                .topicPattern("orders-*").namespace("production")
                .topicCount(12).consumerCount(3)
                .totalBacklog(45_000L).averageTTL(7200L)
                .ttlStatus("NORMAL").lastActiveTime(1_800_000_000L)
                .sessionIds(List.of("session-1", "session-2"))
                .build();

        LiteTopicListItem item = LiteTopicListItem.from(vo);

        assertThat(item.topicPattern()).isEqualTo("orders-*");
        assertThat(item.namespace()).isEqualTo("production");
        assertThat(item.topicCount()).isEqualTo(12);
        assertThat(item.consumerCount()).isEqualTo(3);
        assertThat(item.totalBacklog()).isEqualTo(45_000L);
        assertThat(item.averageTTL()).isEqualTo(7200L);
        assertThat(item.ttlStatus()).isEqualTo("NORMAL");
        assertThat(item.lastActiveTime()).isEqualTo(1_800_000_000L);
        assertThat(item.sessionIds()).containsExactly("session-1", "session-2");
    }

    @Test
    void unreportedStatisticsStayNullNotZero() {
        LiteTopicItemVO vo = LiteTopicItemVO.builder()
                .topicPattern("orders-*").build();

        LiteTopicListItem item = LiteTopicListItem.from(vo);

        assertThat(item.topicCount()).isNull();
        assertThat(item.consumerCount()).isNull();
        assertThat(item.totalBacklog()).isNull();
        assertThat(item.averageTTL()).isNull();
        assertThat(item.topicPattern()).isEqualTo("orders-*");
    }

    @Test
    void aNullSessionListStaysNull() {
        LiteTopicItemVO vo = LiteTopicItemVO.builder()
                .topicPattern("orders-*").sessionIds(null).build();

        assertThat(LiteTopicListItem.from(vo).sessionIds()).isNull();
    }
}
