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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the offset-reset preview of {@link ResetConsumerOffsetPreviewVO}: the safety verdict
 * (complete, allowReset), the queue classification counts (rewind vs fast-forward), the
 * projected lag and the warnings the operator must acknowledge before committing.
 */
class ResetConsumerOffsetPreviewVOTest {

    @Test
    void thePreviewRoundTrips() {
        ResetConsumerOffsetQueuePreviewVO queue = ResetConsumerOffsetQueuePreviewVO.builder().build();
        ResetConsumerOffsetPreviewVO preview = ResetConsumerOffsetPreviewVO.builder()
                .instanceId("instance-1")
                .groupName("order-group")
                .topic("orders")
                .timestamp(1784112606000L)
                .complete(true)
                .allowReset(false)
                .queueCount(8)
                .warningCount(2)
                .rewindQueueCount(5)
                .fastForwardQueueCount(3)
                .currentTotalLag(10000L)
                .projectedTotalLag(2000L)
                .totalOffsetDelta(-8000L)
                .warnings(List.of("3 queues would fast-forward"))
                .queues(List.of(queue))
                .build();

        assertThat(preview.getInstanceId()).isEqualTo("instance-1");
        assertThat(preview.getGroupName()).isEqualTo("order-group");
        assertThat(preview.getTopic()).isEqualTo("orders");
        assertThat(preview.getTimestamp()).isEqualTo(1784112606000L);
        assertThat(preview.isComplete()).isTrue();
        assertThat(preview.isAllowReset()).isFalse();
        assertThat(preview.getQueueCount()).isEqualTo(8);
        assertThat(preview.getWarningCount()).isEqualTo(2);
        assertThat(preview.getRewindQueueCount()).isEqualTo(5);
        assertThat(preview.getFastForwardQueueCount()).isEqualTo(3);
        assertThat(preview.getCurrentTotalLag()).isEqualTo(10000L);
        assertThat(preview.getProjectedTotalLag()).isEqualTo(2000L);
        assertThat(preview.getTotalOffsetDelta()).isEqualTo(-8000L);
        assertThat(preview.getWarnings()).containsExactly("3 queues would fast-forward");
        assertThat(preview.getQueues()).hasSize(1);
    }

    @Test
    void aFreshPreviewIsIncompleteAndUnverdicted() {
        ResetConsumerOffsetPreviewVO preview = new ResetConsumerOffsetPreviewVO();
        assertThat(preview.isComplete()).isFalse();
        assertThat(preview.isAllowReset()).isFalse();
        assertThat(preview.getQueueCount()).isZero();
        assertThat(preview.getWarnings()).isNull();
    }
}
