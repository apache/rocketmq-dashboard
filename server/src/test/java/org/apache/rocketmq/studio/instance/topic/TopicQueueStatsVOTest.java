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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the topic queue stats of {@link TopicQueueStatsVO}: one queue's offset window plus the
 * last update timestamp - the staleness signal the topic detail page shows per queue.
 */
class TopicQueueStatsVOTest {

    @Test
    void theWindowAndStalenessRoundTrip() {
        TopicQueueStatsVO stats = TopicQueueStatsVO.builder()
                .brokerName("broker-a")
                .queueId(3)
                .minOffset(100L)
                .maxOffset(200L)
                .lastUpdateTimestamp(1784112606000L)
                .build();

        assertThat(stats.getBrokerName()).isEqualTo("broker-a");
        assertThat(stats.getQueueId()).isEqualTo(3);
        assertThat(stats.getMinOffset()).isEqualTo(100L);
        assertThat(stats.getMaxOffset()).isEqualTo(200L);
        assertThat(stats.getLastUpdateTimestamp()).isEqualTo(1784112606000L);
    }

    @Test
    void aFreshWindowCarriesNoUpdateTimestamp() {
        TopicQueueStatsVO stats = new TopicQueueStatsVO();
        assertThat(stats.getLastUpdateTimestamp()).isZero();
    }
}
