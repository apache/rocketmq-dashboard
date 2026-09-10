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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiPayloadGuardTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void chatMeasuresUtf8BytesAndAcceptsTheExactBoundary() {
        ChatDTO boundary = ChatDTO.builder()
                .message("\u754c".repeat(AiPayloadGuard.MAX_MESSAGE_BYTES / 3) + "x")
                .build();
        ChatDTO oversized = ChatDTO.builder()
                .message(boundary.getMessage() + "\u754c")
                .build();

        assertThatCode(() -> AiPayloadGuard.validateChat(boundary)).doesNotThrowAnyException();
        assertThatThrownBy(() -> AiPayloadGuard.validateChat(oversized))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("UTF-8 bytes");
    }

    @Test
    void commandRequiresPromptOrCommand() {
        AiCommandDTO request = AiCommandDTO.builder().context(Map.of("cluster", "main")).build();

        assertThatThrownBy(() -> AiPayloadGuard.validateCommand(request, objectMapper))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Command or prompt is required");
    }

    @Test
    void toolInputUsesItsSerializedJsonSize() {
        Map<String, Object> input = Map.of(
                "payload", "x".repeat(AiPayloadGuard.MAX_TOOL_INPUT_BYTES));

        assertThatThrownBy(() -> AiPayloadGuard.validateToolInvocation("rmq.query", input, objectMapper))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Tool input must not exceed");
    }

    @Test
    void outboundModelUsesTheProviderErrorContract() {
        assertThatThrownBy(() -> AiPayloadGuard.validateOutboundPrompt(
                "hello", "\u6a21".repeat(AiPayloadGuard.MAX_MODEL_BYTES / 3 + 1)))
                .isInstanceOfSatisfying(LlmGatewayException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(400);
                    assertThat(exception.getCode()).isEqualTo("llm.request.payload_too_large");
                    assertThat(exception.getMessage()).contains("LLM model");
                });
    }

    @Test
    void rejectsMissingRequestsAndBlankChatMessages() {
        assertThatThrownBy(() -> AiPayloadGuard.validateChat(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Chat request is required");
        assertThatThrownBy(() -> AiPayloadGuard.validateCommand(null, objectMapper))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Command request is required");

        ChatDTO blankMessage = ChatDTO.builder().message("  ").build();
        assertThatThrownBy(() -> AiPayloadGuard.validateChat(blankMessage))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Chat message is required");
    }

    @Test
    void acceptsACommandWithPromptAndEnforcesTheContextBudget() {
        AiCommandDTO valid = AiCommandDTO.builder().prompt("list topics").build();
        assertThatCode(() -> AiPayloadGuard.validateCommand(valid, objectMapper))
                .doesNotThrowAnyException();

        AiCommandDTO oversizedContext = AiCommandDTO.builder().prompt("list topics")
                .context(Map.of("blob", "x".repeat(AiPayloadGuard.MAX_CONTEXT_BYTES)))
                .build();
        assertThatThrownBy(() -> AiPayloadGuard.validateCommand(oversizedContext, objectMapper))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Command context must not exceed");
    }

    @Test
    void enforcesModelAndConversationIdBudgetsOnChat() {
        ChatDTO longModel = ChatDTO.builder().message("hi")
                .model("m".repeat(AiPayloadGuard.MAX_MODEL_BYTES + 1)).build();
        assertThatThrownBy(() -> AiPayloadGuard.validateChat(longModel))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Chat model must not exceed");

        ChatDTO longConversation = ChatDTO.builder().message("hi")
                .conversationId("c".repeat(AiPayloadGuard.MAX_CONVERSATION_ID_BYTES + 1)).build();
        assertThatThrownBy(() -> AiPayloadGuard.validateChat(longConversation))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Conversation ID must not exceed");
    }

    @Test
    void toolInvocationRequiresANameButToleratesMissingInput() {
        assertThatThrownBy(() -> AiPayloadGuard.validateToolInvocation("  ", Map.of(), objectMapper))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Tool name is required");
        assertThatCode(() -> AiPayloadGuard.validateToolInvocation("rmq.topic.list", null, objectMapper))
                .doesNotThrowAnyException();
    }
}
