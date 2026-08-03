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
package org.apache.rocketmq.studio.ops.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmConversationMemoryTest {

    @Test
    void appendUserAndSnapshotShouldIncludePreviousTurnsForConversation() {
        LlmConversationMemory memory = new LlmConversationMemory();

        List<LlmChatMessage> first = memory.appendUserAndSnapshot(" conv-1 ", " first question ");
        memory.appendAssistant("conv-1", " first answer ");
        List<LlmChatMessage> second = memory.appendUserAndSnapshot("conv-1", "follow up");

        assertThat(first).extracting(LlmChatMessage::role).containsExactly("user");
        assertThat(first).extracting(LlmChatMessage::content).containsExactly("first question");
        assertThat(second).extracting(LlmChatMessage::role)
                .containsExactly("user", "assistant", "user");
        assertThat(second).extracting(LlmChatMessage::content)
                .containsExactly("first question", "first answer", "follow up");
    }

    @Test
    void blankConversationIdShouldRemainSingleTurn() {
        LlmConversationMemory memory = new LlmConversationMemory();

        List<LlmChatMessage> first = memory.appendUserAndSnapshot("", "first");
        memory.appendAssistant("", "answer");
        List<LlmChatMessage> second = memory.appendUserAndSnapshot("", "second");

        assertThat(first).extracting(LlmChatMessage::content).containsExactly("first");
        assertThat(second).extracting(LlmChatMessage::content).containsExactly("second");
    }

    @Test
    void conversationHistoryShouldBeBounded() {
        LlmConversationMemory memory = new LlmConversationMemory();

        for (int i = 0; i < 10; i++) {
            memory.appendUserAndSnapshot("conv-1", "question-" + i);
            memory.appendAssistant("conv-1", "answer-" + i);
        }
        List<LlmChatMessage> snapshot = memory.appendUserAndSnapshot("conv-1", "latest");

        assertThat(snapshot).hasSize(12);
        assertThat(snapshot.get(0).content()).isEqualTo("answer-4");
        assertThat(snapshot.get(11).content()).isEqualTo("latest");
    }
}
