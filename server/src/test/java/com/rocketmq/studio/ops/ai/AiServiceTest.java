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
package com.rocketmq.studio.ops.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    @Mock
    private LlmGateway llmGateway;

    @Mock
    private McpServerRegistry mcpServerRegistry;

    @InjectMocks
    private AiService aiService;

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
    void listToolsShouldReturnAllTools() {
        AiToolVO tool1 = AiToolVO.builder().name("list_topics").description("List all topics").build();
        AiToolVO tool2 = AiToolVO.builder().name("query_metrics").description("Query cluster metrics").build();
        AiToolVO tool3 = AiToolVO.builder().name("send_message").description("Send a test message").build();
        when(mcpServerRegistry.listTools()).thenReturn(Arrays.asList(tool1, tool2, tool3));

        List<AiToolVO> result = aiService.listTools();

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getName()).isEqualTo("list_topics");
        assertThat(result.get(0).getDescription()).isEqualTo("List all topics");
        assertThat(result.get(1).getName()).isEqualTo("query_metrics");
        assertThat(result.get(2).getName()).isEqualTo("send_message");
    }

    @Test
    void listToolsShouldReturnEmptyListWhenNoTools() {
        when(mcpServerRegistry.listTools()).thenReturn(Collections.emptyList());

        List<AiToolVO> result = aiService.listTools();

        assertThat(result).isEmpty();
    }

    @Test
    void listToolsShouldIncludeParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("clusterId", "string");
        params.put("limit", "integer");
        AiToolVO tool = AiToolVO.builder()
                .name("list_topics")
                .description("List all topics")
                .parameters(params)
                .build();
        when(mcpServerRegistry.listTools()).thenReturn(List.of(tool));

        List<AiToolVO> result = aiService.listTools();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getParameters()).isNotNull();
        assertThat(result.get(0).getParameters()).isInstanceOf(Map.class);
    }
}
