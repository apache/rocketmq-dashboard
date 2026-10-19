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
package org.apache.rocketmq.studio.ops.ai.tool.contract.topic;

import org.apache.rocketmq.studio.instance.topic.TopicQueueStatsVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the tool contract's queue stats row of {@link TopicQueueStatsItem}: the broker/queue
 * identity with the offset window and staleness timestamp, and the from() mapping preserves
 * the VO's values - the tool result must not drift from the instance view.
 */
class TopicQueueStatsItemTest {

    @Test
    void theRowRoundTrips() {
        TopicQueueStatsItem item = new TopicQueueStatsItem(
                "broker-a", 3, 100L, 200L, 1784112606000L);

        assertThat(item.brokerName()).isEqualTo("broker-a");
        assertThat(item.queueId()).isEqualTo(3);
        assertThat(item.minOffset()).isEqualTo(100L);
        assertThat(item.maxOffset()).isEqualTo(200L);
        assertThat(item.lastUpdateTimestamp()).isEqualTo(1784112606000L);
    }

    @Test
    void fromCarriesTheInstanceViewVerbatim() {
        TopicQueueStatsVO source = TopicQueueStatsVO.builder()
                .brokerName("broker-a")
                .queueId(3)
                .minOffset(100L)
                .maxOffset(200L)
                .lastUpdateTimestamp(1784112606000L)
                .build();

        TopicQueueStatsItem item = TopicQueueStatsItem.from(source);

        assertThat(item.brokerName()).isEqualTo("broker-a");
        assertThat(item.queueId()).isEqualTo(3);
        assertThat(item.minOffset()).isEqualTo(100L);
        assertThat(item.maxOffset()).isEqualTo(200L);
        assertThat(item.lastUpdateTimestamp()).isEqualTo(1784112606000L);
    }
}
