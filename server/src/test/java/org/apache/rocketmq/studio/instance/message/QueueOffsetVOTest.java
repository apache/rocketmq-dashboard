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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the queue offset view of {@link QueueOffsetVO}: one queue's readable offset window on
 * one broker, the bounds the message browser paginates within.
 */
class QueueOffsetVOTest {

    @Test
    void theOffsetWindowRoundTrips() {
        QueueOffsetVO offsets = QueueOffsetVO.builder()
                .brokerName("broker-a")
                .queueId(3)
                .minOffset(100L)
                .maxOffset(200L)
                .build();

        assertThat(offsets.getBrokerName()).isEqualTo("broker-a");
        assertThat(offsets.getQueueId()).isEqualTo(3);
        assertThat(offsets.getMinOffset()).isEqualTo(100L);
        assertThat(offsets.getMaxOffset()).isEqualTo(200L);
    }

    @Test
    void aFreshWindowStartsAtZero() {
        QueueOffsetVO offsets = new QueueOffsetVO();
        assertThat(offsets.getMinOffset()).isZero();
        assertThat(offsets.getMaxOffset()).isZero();
    }
}
