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
package org.apache.rocketmq.studio.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the AI run entity {@link RmqAiRun}: it maps to the {@code rmq_ai_run} table and every
 * column round-trips - the run identity, the engine/model, the seq window it covers, the token
 * accounting and the terminal stop reason with its error triple.
 */
class RmqAiRunTest {

    @Test
    void theEntityMapsToTheRunTable() {
        TableName annotation = RmqAiRun.class.getAnnotation(TableName.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("rmq_ai_run");
    }

    @Test
    void everyColumnRoundTrips() {
        RmqAiRun run = new RmqAiRun();
        run.setId(7L);
        run.setConversationId(3L);
        run.setTurn(2);
        run.setStatus("COMPLETED");
        run.setEngine("claude-code");
        run.setModel("claude-sonnet");
        run.setRuntimeSessionId("sess-42");
        run.setResumedFrom("sess-41");
        run.setStartedAt(LocalDateTime.of(2026, 1, 2, 3, 4));
        run.setFinishedAt(LocalDateTime.of(2026, 1, 2, 3, 5));
        run.setDurationMs(60000L);
        run.setInputTokens(1200);
        run.setOutputTokens(3400);
        run.setTokensPerSecond(56.7);
        run.setStartSeq(1);
        run.setEndSeq(100);
        run.setStopReason("USER_STOP");
        run.setErrorCode("E1");
        run.setErrorMessage("boom");

        assertThat(run.getId()).isEqualTo(7L);
        assertThat(run.getConversationId()).isEqualTo(3L);
        assertThat(run.getTurn()).isEqualTo(2);
        assertThat(run.getStatus()).isEqualTo("COMPLETED");
        assertThat(run.getEngine()).isEqualTo("claude-code");
        assertThat(run.getModel()).isEqualTo("claude-sonnet");
        assertThat(run.getRuntimeSessionId()).isEqualTo("sess-42");
        assertThat(run.getResumedFrom()).isEqualTo("sess-41");
        assertThat(run.getStartedAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4));
        assertThat(run.getFinishedAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 5));
        assertThat(run.getDurationMs()).isEqualTo(60000L);
        assertThat(run.getInputTokens()).isEqualTo(1200);
        assertThat(run.getOutputTokens()).isEqualTo(3400);
        assertThat(run.getTokensPerSecond()).isEqualTo(56.7);
        assertThat(run.getStartSeq()).isEqualTo(1);
        assertThat(run.getEndSeq()).isEqualTo(100);
        assertThat(run.getStopReason()).isEqualTo("USER_STOP");
        assertThat(run.getErrorCode()).isEqualTo("E1");
        assertThat(run.getErrorMessage()).isEqualTo("boom");
    }
}
