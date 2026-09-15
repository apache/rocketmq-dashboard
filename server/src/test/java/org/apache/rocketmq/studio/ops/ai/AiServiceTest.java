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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private LlmGateway llmGateway;

    private AiService aiService;

    @BeforeEach
    void setUp() {
        aiService = new AiService(
                llmGateway,
                new ObjectMapper());
    }

    @Test
    void chatShouldReturnSseEmitterFromGateway() {
        ChatDTO request = ChatDTO.builder()
                .message("What is the broker status?")
                .mode("chat")
                .model("gpt-4")
                .conversationId("conv-1")
                .build();
        SseEmitter mockEmitter = new SseEmitter();
        when(llmGateway.chat(request)).thenReturn(mockEmitter);

        SseEmitter result = aiService.chat(request);

        assertThat(result).isSameAs(mockEmitter);
        verify(llmGateway).chat(request);
    }

    @Test
    void chatShouldPassRequestDirectlyToGateway() {
        ChatDTO request = ChatDTO.builder()
                .message("List all topics")
                .mode("agent")
                .build();
        SseEmitter mockEmitter = new SseEmitter();
        when(llmGateway.chat(any(ChatDTO.class))).thenReturn(mockEmitter);

        aiService.chat(request);

        verify(llmGateway).chat(request);
    }

    @Test
    void executeShouldReturnSuccessResult() {
        AiCommandDTO command = AiCommandDTO.builder()
                .command("list_topics")
                .mode("agent")
                .model("gpt-4")
                .prompt("List all topics in the cluster")
                .build();
        when(llmGateway.execute(command)).thenReturn("Found 5 topics: topic-a, topic-b, topic-c, topic-d, topic-e");

        AiExecuteResultVO result = aiService.execute(command);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResult()).contains("Found 5 topics");
        verify(llmGateway).execute(command);
    }

    @Test
    void executeShouldReturnFailureWhenGatewayThrows() {
        AiCommandDTO command = AiCommandDTO.builder()
                .command("delete_topic")
                .mode("agent")
                .prompt("Delete topic-x")
                .build();
        when(llmGateway.execute(command)).thenThrow(new RuntimeException("Permission denied"));

        AiExecuteResultVO result = aiService.execute(command);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getResult()).contains("Error: Permission denied");
    }

    @Test
    void executeShouldHandleNullMessageInException() {
        AiCommandDTO command = AiCommandDTO.builder().command("bad_cmd").build();
        when(llmGateway.execute(command)).thenThrow(new RuntimeException());

        AiExecuteResultVO result = aiService.execute(command);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getResult()).startsWith("Error:");
    }

    @Test
    void executeShouldPassContextToGateway() {
        Map<String, Object> context = new HashMap<>();
        context.put("clusterId", "cluster-1");
        context.put("namespace", "default");
        AiCommandDTO command = AiCommandDTO.builder()
                .command("query_metrics")
                .mode("agent")
                .context(context)
                .build();
        when(llmGateway.execute(command)).thenReturn("CPU: 45%, Memory: 72%");

        AiExecuteResultVO result = aiService.execute(command);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResult()).contains("CPU: 45%");
    }

    @Test
    void chatRejectsNullRequest() {
        assertThatThrownBy(() -> aiService.chat(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Chat request is required");
        verifyNoInteractions(llmGateway);
    }

    @Test
    void chatRejectsOversizedMessageBeforeCallingGateway() {
        ChatDTO request = ChatDTO.builder()
                .message("\u754c".repeat(AiPayloadGuard.MAX_MESSAGE_BYTES / 3 + 1))
                .build();

        assertThatThrownBy(() -> aiService.chat(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Chat message must not exceed");
        verifyNoInteractions(llmGateway);
    }

    @Test
    void executeRejectsOversizedContextBeforeCallingGateway() {
        AiCommandDTO command = AiCommandDTO.builder()
                .command("query_metrics")
                .context(Map.of("payload", "x".repeat(AiPayloadGuard.MAX_CONTEXT_BYTES)))
                .build();

        AiExecuteResultVO result = aiService.execute(command);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getResult()).contains("Command context must not exceed");
        verifyNoInteractions(llmGateway);
    }

    @Test
    void executeHandlesNullCommand() {
        AiExecuteResultVO result = aiService.execute(null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getResult()).isEqualTo("Command request is required");
        verifyNoInteractions(llmGateway);
    }
}
