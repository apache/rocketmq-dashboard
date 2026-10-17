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
import org.apache.rocketmq.studio.ops.ai.conversation.event.StopReason;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the run view of {@link AiRunVO}: one assistant turn as the timeline renders it - the
 * run's identity, engine and model, the start/finish window with duration, the token counts
 * and the terminal stop reason.
 */
class AiRunVOTest {

    @Test
    void everyFieldRoundTripsThroughTheBuilder() {
        AiRunVO run = AiRunVO.builder()
                .id(7L)
                .conversationId(3L)
                .turn(2)
                .status(RunStatus.COMPLETED)
                .engine("claude-code")
                .model("claude-sonnet")
                .startedAt(LocalDateTime.of(2026, 1, 2, 3, 4))
                .finishedAt(LocalDateTime.of(2026, 1, 2, 3, 5))
                .durationMs(60000L)
                .inputTokens(1200)
                .outputTokens(3400)
                .stopReason(StopReason.USER_STOP)
                .build();

        assertThat(run.getId()).isEqualTo(7L);
        assertThat(run.getConversationId()).isEqualTo(3L);
        assertThat(run.getTurn()).isEqualTo(2);
        assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(run.getEngine()).isEqualTo("claude-code");
        assertThat(run.getModel()).isEqualTo("claude-sonnet");
        assertThat(run.getStartedAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4));
        assertThat(run.getFinishedAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 5));
        assertThat(run.getDurationMs()).isEqualTo(60000L);
        assertThat(run.getInputTokens()).isEqualTo(1200);
        assertThat(run.getOutputTokens()).isEqualTo(3400);
        assertThat(run.getStopReason()).isEqualTo(StopReason.USER_STOP);
    }

    @Test
    void aFreshRunCarriesNoTokensAndNoStopReason() {
        AiRunVO run = new AiRunVO();
        assertThat(run.getInputTokens()).isNull();
        assertThat(run.getOutputTokens()).isNull();
        assertThat(run.getStopReason()).isNull();
    }
}
