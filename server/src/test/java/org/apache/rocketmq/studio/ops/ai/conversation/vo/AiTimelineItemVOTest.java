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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.ops.ai.conversation.event.TimelineEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the timeline row of {@link AiTimelineItemVO}: the wire position (id, turn, seq) with
 * the runId that is never null (rmq_ai_event.run_id is NOT NULL), the deserialised event -
 * and the serialization never leaks the raw event payload shape.
 */
class AiTimelineItemVOTest {

    @Test
    void everyFieldRoundTripsThroughTheBuilder() {
        TimelineEvent event = new TimelineEvent.Text("hello");
        AiTimelineItemVO item = AiTimelineItemVO.builder()
                .id(7L)
                .turn(2)
                .seq(100)
                .createdAt(LocalDateTime.of(2026, 1, 2, 3, 4))
                .runId(42L)
                .event(event)
                .build();

        assertThat(item.getId()).isEqualTo(7L);
        assertThat(item.getTurn()).isEqualTo(2);
        assertThat(item.getSeq()).isEqualTo(100);
        assertThat(item.getRunId()).isEqualTo(42L);
        assertThat(item.getEvent()).isSameAs(event);
    }

    @Test
    void theWirePositionIsTheEnvelopeAndTheEventIsThePayload() throws Exception {
        TimelineEvent event = new TimelineEvent.Text("hello");
        AiTimelineItemVO item = AiTimelineItemVO.builder()
                .id(7L)
                .seq(100)
                .runId(42L)
                .event(event)
                .build();

        String json = new ObjectMapper().writeValueAsString(item);

        assertThat(json).contains("\"seq\":100");
        assertThat(json).contains("\"runId\":42");
        // the deserialised event is serialised as its own wire vocabulary (type: "text")
        assertThat(json).contains("\"type\":\"text\"");
    }
}
