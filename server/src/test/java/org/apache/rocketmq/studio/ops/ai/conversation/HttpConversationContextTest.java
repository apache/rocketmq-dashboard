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
package org.apache.rocketmq.studio.ops.ai.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.ops.ai.OpenAiCompatibleLlmClient.ChatMessage;
import org.apache.rocketmq.studio.persistence.entity.RmqAiEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpConversationContextTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AiEventRepository repository = mock(AiEventRepository.class);

    @Test
    void onlyCompleteQuestionAnswerTurnsShouldBecomeRoleMessagesTest() {
        when(repository.findRecentBeforeSeq(eq(7L), eq(20), anyInt())).thenReturn(List.of(
                row(1, 1, "user", "question"), row(2, 1, "text", "answer "),
                row(3, 1, "thinking", "private reasoning"), row(4, 1, "text", "tail"),
                row(5, 2, "user", "unanswered"), row(6, 3, "text", "orphan fragment")));
        HttpConversationContext context = HttpConversationContext.read(repository, mapper, 7L, 20);
        assertThat(context.messages()).containsExactly(new ChatMessage("user", "question"),
                new ChatMessage("assistant", "answer tail"));
        assertThat(context.truncated()).isTrue();
    }

    @Test
    void recentTurnsShouldWinTheMessageBudgetTest() {
        List<RmqAiEvent> rows = new ArrayList<>();
        for (int i = 1; i <= 18; i++) {
            rows.add(row(2 * i - 1, i, "user", "q" + i));
            rows.add(row(2 * i, i, "text", "a" + i));
        }
        when(repository.findRecentBeforeSeq(eq(7L), eq(100), anyInt())).thenReturn(rows);
        HttpConversationContext context = HttpConversationContext.read(repository, mapper, 7L, 100);
        assertThat(context.messages()).hasSize(32);
        assertThat(context.messages().get(0).content()).isEqualTo("q3");
        assertThat(context.messages().get(31).content()).isEqualTo("a18");
        assertThat(context.truncated()).isTrue();
    }

    @Test
    void eventWindowShouldNotIncludeAnOrphanedLeadingAnswerTest() {
        List<RmqAiEvent> rows = new ArrayList<>();
        for (int i = 1; i <= 101; i++) {
            rows.add(row(2 * i - 1, i, "user", "q" + i));
            if (i <= 100) rows.add(row(2 * i, i, "text", "a" + i));
        }
        when(repository.findRecentBeforeSeq(eq(7L), eq(300), anyInt())).thenReturn(rows);
        HttpConversationContext context = HttpConversationContext.read(repository, mapper, 7L, 300);
        verify(repository).findRecentBeforeSeq(7L, 300, 201);
        assertThat(context.messages()).hasSize(32);
        assertThat(context.messages().get(0).content()).isEqualTo("q85");
        assertThat(context.messages().get(31).content()).isEqualTo("a100");
        assertThat(context.truncated()).isTrue();
    }

    @Test
    void byteBudgetShouldDropWholeTurnsRatherThanSplitUnicodeTest() {
        String large = new String(Character.toChars(0x1F600)).repeat(17_000);
        when(repository.findRecentBeforeSeq(eq(7L), eq(100), anyInt())).thenReturn(List.of(
                row(1, 1, "user", "older"), row(2, 1, "text", "old answer"),
                row(3, 2, "user", "newer"), row(4, 2, "text", large)));
        HttpConversationContext context = HttpConversationContext.read(repository, mapper, 7L, 100);
        assertThat(context.messages()).isEmpty();
        assertThat(context.truncated()).isTrue();
    }

    @Test
    void unreadableTurnShouldNotBecomeAnUnpairedAnswerTest() {
        RmqAiEvent bad = row(1, 1, "user", "bad");
        bad.setPayload("{broken");
        when(repository.findRecentBeforeSeq(eq(7L), eq(100), anyInt())).thenReturn(List.of(
                bad, row(2, 1, "text", "do not replay"),
                row(3, 2, "user", "valid"), row(4, 2, "text", "valid answer")));
        HttpConversationContext context = HttpConversationContext.read(repository, mapper, 7L, 100);
        assertThat(context.messages()).containsExactly(new ChatMessage("user", "valid"),
                new ChatMessage("assistant", "valid answer"));
        assertThat(context.truncated()).isTrue();
    }

    private RmqAiEvent row(int seq, int turn, String type, String text) {
        RmqAiEvent row = new RmqAiEvent();
        row.setConversationId(7L);
        row.setSeq(seq);
        row.setTurn(turn);
        String source = "thinking".equals(type) ? ",\"source\":\"model\"" : "";
        row.setPayload("{\"type\":\"" + type + "\",\"text\":\"" + text + "\"" + source + "}");
        return row;
    }
}
