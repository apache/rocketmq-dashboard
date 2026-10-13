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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the queue progress row of {@link QueueProgressVO}: one queue's offsets with the
 * backlog diff - where the -1 sentinel means the provider carries no per-queue offsets at
 * all, matching the broker's own unknown-offset convention.
 */
class QueueProgressVOTest {

    @Test
    void theProgressRowRoundTripsThroughTheBuilder() {
        QueueProgressVO progress = QueueProgressVO.builder()
                .topic("orders")
                .broker("broker-a")
                .queueId(3)
                .brokerOffset(1000L)
                .consumerOffset(800L)
                .diffTotal(200L)
                .build();

        assertThat(progress.getTopic()).isEqualTo("orders");
        assertThat(progress.getBroker()).isEqualTo("broker-a");
        assertThat(progress.getQueueId()).isEqualTo(3);
        assertThat(progress.getBrokerOffset()).isEqualTo(1000L);
        assertThat(progress.getConsumerOffset()).isEqualTo(800L);
        assertThat(progress.getDiffTotal()).isEqualTo(200L);
    }

    @Test
    void theSentinelMeansNoPerQueueOffsets() {
        QueueProgressVO progress = QueueProgressVO.builder()
                .topic("orders")
                .brokerOffset(-1L)
                .consumerOffset(-1L)
                .diffTotal(-1L)
                .build();

        assertThat(progress.getBrokerOffset()).isEqualTo(-1L);
        assertThat(progress.getDiffTotal()).isEqualTo(-1L);
    }
}
