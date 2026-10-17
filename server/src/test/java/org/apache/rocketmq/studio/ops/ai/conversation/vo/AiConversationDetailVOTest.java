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

import org.apache.rocketmq.studio.ops.ai.conversation.event.RunStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the conversation detail of {@link AiConversationDetailVO}: the subclass must expose the
 * parent's fields through the shared {@code @SuperBuilder} - the documented reason a plain
 * {@code @Builder} on a subclass would silently drop them - and adds the active run reference.
 */
class AiConversationDetailVOTest {

    @Test
    void theSuperBuilderCarriesBothParentAndChildFields() {
        AiActiveRunRef activeRun = AiActiveRunRef.builder().id(42L).status(RunStatus.RUNNING).build();
        AiConversationDetailVO detail = AiConversationDetailVO.builder()
                // parent fields
                .id(7L)
                .title("order triage")
                .owner("alice")
                .engine("claude-code")
                .model("claude-sonnet")
                .mode("agent")
                .instanceId("instance-1")
                .runtimeSessionId("sess-42")
                .lastSeq(100)
                .archived(false)
                .createdAt(LocalDateTime.of(2026, 1, 2, 3, 4))
                .updatedAt(LocalDateTime.of(2026, 1, 3, 4, 5))
                // child field
                .activeRun(activeRun)
                .build();

        assertThat(detail.getId()).isEqualTo(7L);
        assertThat(detail.getTitle()).isEqualTo("order triage");
        assertThat(detail.getOwner()).isEqualTo("alice");
        assertThat(detail.getEngine()).isEqualTo("claude-code");
        assertThat(detail.getInstanceId()).isEqualTo("instance-1");
        assertThat(detail.getRuntimeSessionId()).isEqualTo("sess-42");
        assertThat(detail.getLastSeq()).isEqualTo(100);
        assertThat(detail.isArchived()).isFalse();
        assertThat(detail.getActiveRun()).isSameAs(activeRun);
    }

    @Test
    void aDetailWithoutAnActiveRunCarriesNone() {
        AiConversationDetailVO detail = AiConversationDetailVO.builder().id(7L).build();
        assertThat(detail.getActiveRun()).isNull();
    }
}
