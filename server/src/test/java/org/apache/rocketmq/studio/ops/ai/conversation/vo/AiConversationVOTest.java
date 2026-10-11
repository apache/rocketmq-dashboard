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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the conversation list view of {@link AiConversationVO}: the immutable identity, the
 * engine/model/mode triple, the documented nullability of the instance binding and the runtime
 * session id, and the seq high-water mark that must never be null on the wire.
 */
class AiConversationVOTest {

    @Test
    void everyFieldRoundTripsThroughTheBuilder() {
        AiConversationVO vo = AiConversationVO.builder()
                .id(7L)
                .title("order triage")
                .owner("alice")
                .engine("claude-code")
                .model("claude-sonnet")
                .mode("agent")
                .instanceId("instance-1")
                .runtimeSessionId("sess-42")
                .lastSeq(100)
                .archived(true)
                .createdAt(LocalDateTime.of(2026, 1, 2, 3, 4))
                .updatedAt(LocalDateTime.of(2026, 1, 3, 4, 5))
                .build();

        assertThat(vo.getId()).isEqualTo(7L);
        assertThat(vo.getTitle()).isEqualTo("order triage");
        assertThat(vo.getOwner()).isEqualTo("alice");
        assertThat(vo.getEngine()).isEqualTo("claude-code");
        assertThat(vo.getModel()).isEqualTo("claude-sonnet");
        assertThat(vo.getMode()).isEqualTo("agent");
        assertThat(vo.getInstanceId()).isEqualTo("instance-1");
        assertThat(vo.getRuntimeSessionId()).isEqualTo("sess-42");
        assertThat(vo.getLastSeq()).isEqualTo(100);
        assertThat(vo.isArchived()).isTrue();
        assertThat(vo.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4));
        assertThat(vo.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 3, 4, 5));
    }

    @Test
    void anUnboundConversationHasNoInstanceAndNoRuntimeSession() {
        AiConversationVO vo = AiConversationVO.builder().id(7L).build();

        // documented nullability: unbound conversation, first run not yet finished
        assertThat(vo.getInstanceId()).isNull();
        assertThat(vo.getRuntimeSessionId()).isNull();
        assertThat(vo.getLastSeq()).isNull();
        assertThat(vo.isArchived()).isFalse();
    }
}
