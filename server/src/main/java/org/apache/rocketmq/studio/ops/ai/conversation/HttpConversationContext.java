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
import org.apache.rocketmq.studio.ops.ai.conversation.event.TimelineEvent;
import org.apache.rocketmq.studio.persistence.entity.RmqAiEvent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** A bounded suffix of complete prose turns; reasoning and tools never become model instructions. */
record HttpConversationContext(List<ChatMessage> messages, boolean truncated) {
    private static final int MAX_ROWS = 200;
    private static final int MAX_TURNS = 16;
    private static final int MAX_BYTES = 64 * 1024;

    static HttpConversationContext read(AiEventRepository repository, ObjectMapper mapper,
                                        Long conversationId, Integer beforeSeq) {
        if (beforeSeq == null || beforeSeq <= 1) return new HttpConversationContext(List.of(), false);
        List<RmqAiEvent> rows = repository.findRecentBeforeSeq(conversationId, beforeSeq, MAX_ROWS + 1);
        boolean truncated = rows.size() > MAX_ROWS;
        TreeMap<Integer, Turn> turns = new TreeMap<>();
        for (RmqAiEvent row : rows.subList(Math.max(0, rows.size() - MAX_ROWS), rows.size())) {
            Turn turn = turns.computeIfAbsent(row.getTurn(), ignored -> new Turn());
            TimelineEvent event = AiEventCodec.read(mapper, row).orElse(null);
            if (event == null) {
                turn.invalid = true;
            } else if (event instanceof TimelineEvent.User user) {
                turn.question = user.text();
            } else if (event instanceof TimelineEvent.Text text) {
                if (text.text() == null) turn.invalid = true;
                else turn.answer.append(text.text());
            }
        }
        List<List<ChatMessage>> selected = new ArrayList<>();
        int bytes = 0;
        for (Turn turn : turns.descendingMap().values()) {
            if (turn.invalid || turn.question == null || turn.answer.isEmpty()) {
                truncated = true;
                continue;
            }
            String answer = turn.answer.toString();
            int size = turn.question.getBytes(StandardCharsets.UTF_8).length
                    + answer.getBytes(StandardCharsets.UTF_8).length;
            if (selected.size() >= MAX_TURNS || size > MAX_BYTES - bytes) {
                truncated = true;
                break;
            }
            bytes += size;
            selected.add(List.of(new ChatMessage("user", turn.question), new ChatMessage("assistant", answer)));
        }
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = selected.size() - 1; i >= 0; i--) messages.addAll(selected.get(i));
        return new HttpConversationContext(List.copyOf(messages), truncated);
    }

    private static final class Turn {
        private String question;
        private final StringBuilder answer = new StringBuilder();
        private boolean invalid;
    }
}
