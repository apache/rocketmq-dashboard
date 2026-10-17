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
package org.apache.rocketmq.studio.ops.ai.conversation.vo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the timeline envelope of {@link AiTimelineVO}: the items, the forward pagination
 * cursor (null exactly at the tail), the active run reference and the denormalized run stats
 * travel together - the whole replay payload one conversation page loads.
 */
class AiTimelineVOTest {

    @Test
    void theEnvelopeRoundTripsThroughTheBuilder() {
        AiTimelineItemVO item = AiTimelineItemVO.builder().build();
        AiActiveRunRef activeRun = AiActiveRunRef.builder().id(42L).build();
        AiRunStatsVO stats = AiRunStatsVO.builder().id(42L).tokensPerSecond(50.0).build();
        AiTimelineVO timeline = AiTimelineVO.builder()
                .items(List.of(item))
                .nextAfter(100)
                .activeRun(activeRun)
                .runs(List.of(stats))
                .build();

        assertThat(timeline.getItems()).containsExactly(item);
        assertThat(timeline.getNextAfter()).isEqualTo(100);
        assertThat(timeline.getActiveRun()).isSameAs(activeRun);
        assertThat(timeline.getRuns()).containsExactly(stats);
    }

    @Test
    void aTailPageCarriesANullCursorAndNoActiveRun() {
        AiTimelineVO timeline = AiTimelineVO.builder().items(List.of()).build();

        // the documented contract: null at the tail, so the client stops instead of
        // issuing one guaranteed-empty request per conversation
        assertThat(timeline.getNextAfter()).isNull();
        assertThat(timeline.getActiveRun()).isNull();
    }
}
