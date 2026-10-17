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
 * Pins the conversation list row of {@link AiConversationListItemVO}: the identity with the
 * engine/model/mode triple and the last run's id and status - the pair the list sorts and
 * badges by - round-trip through the builder.
 */
class AiConversationListItemVOTest {

    @Test
    void theRowRoundTripsThroughTheBuilder() {
        AiConversationListItemVO row = AiConversationListItemVO.builder()
                .id(7L)
                .title("order triage")
                .engine("claude-code")
                .model("claude-sonnet")
                .mode("agent")
                .instanceId("instance-1")
                .lastRunId(42L)
                .lastRunStatus(RunStatus.COMPLETED)
                .updatedAt(LocalDateTime.of(2026, 1, 3, 4, 5))
                .createdAt(LocalDateTime.of(2026, 1, 2, 3, 4))
                .build();

        assertThat(row.getId()).isEqualTo(7L);
        assertThat(row.getTitle()).isEqualTo("order triage");
        assertThat(row.getEngine()).isEqualTo("claude-code");
        assertThat(row.getModel()).isEqualTo("claude-sonnet");
        assertThat(row.getMode()).isEqualTo("agent");
        assertThat(row.getInstanceId()).isEqualTo("instance-1");
        assertThat(row.getLastRunId()).isEqualTo(42L);
        assertThat(row.getLastRunStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(row.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 3, 4, 5));
    }

    @Test
    void aRowWithoutRunsCarriesNoLastRun() {
        AiConversationListItemVO row = AiConversationListItemVO.builder().id(7L).build();
        assertThat(row.getLastRunId()).isNull();
        assertThat(row.getLastRunStatus()).isNull();
    }
}
