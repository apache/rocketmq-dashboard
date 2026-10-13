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
package org.apache.rocketmq.studio.instance.dlq;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the dead-letter group row of {@link DLQGroupVO}: the group with its %DLQ% topic, the
 * counters, the last enqueue time, and statsAvailable defaulting to true - a group reads as
 * having real stats until the provider says otherwise (the same honest-zero contract the
 * topic consumer row uses).
 */
class DLQGroupVOTest {

    @Test
    void everyFieldRoundTripsThroughTheBuilder() {
        DLQGroupVO group = DLQGroupVO.builder()
                .groupName("order-group")
                .dlqTopic("%DLQ%order-group")
                .messageCount(42L)
                .lastEnqueueTime(LocalDateTime.of(2026, 1, 2, 3, 4))
                .retryCount(16)
                .status("ACTIVE")
                .statsAvailable(true)
                .build();

        assertThat(group.getGroupName()).isEqualTo("order-group");
        assertThat(group.getDlqTopic()).isEqualTo("%DLQ%order-group");
        assertThat(group.getMessageCount()).isEqualTo(42L);
        assertThat(group.getLastEnqueueTime()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4));
        assertThat(group.getRetryCount()).isEqualTo(16);
        assertThat(group.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void aFreshRowReadsAsHavingRealStats() {
        DLQGroupVO group = new DLQGroupVO();
        assertThat(group.isStatsAvailable()).isTrue();
        assertThat(group.getMessageCount()).isZero();
        assertThat(group.getRetryCount()).isZero();
    }

    @Test
    void aRowWithoutStatsCanBeFlagged() {
        DLQGroupVO group = DLQGroupVO.builder()
                .groupName("order-group")
                .statsAvailable(false)
                .build();
        assertThat(group.isStatsAvailable()).isFalse();
    }
}
