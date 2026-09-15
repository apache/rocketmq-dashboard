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
package org.apache.rocketmq.studio.ops.ai.tool.handler.message;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.message.MessageQueryPageVO;
import org.apache.rocketmq.studio.instance.message.MessageService;
import org.apache.rocketmq.studio.common.config.LegacyJackson2Config;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageQueryByTopicInput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

@ExtendWith(MockitoExtension.class)
class MessageQueryByTopicToolHandlerTest {

    @Mock
    private MessageService messageService;

    @InjectMocks
    private MessageQueryByTopicToolHandler handler;

    @Test
    void executeShouldDelegateToMessageServiceAndProject() {
        MessageRecordVO message = MessageRecordVO.builder()
                .msgId("msg-1")
                .topic("TopicA")
                .storeTime(1000L)
                .size(5)
                .build();
        when(messageService.queryMessagesPage(eq("instance-a"), eq("TopicA"), isNull(), isNull(),
                isNull(), isNull(), isNull(), eq(1), eq(20)))
                .thenReturn(MessageQueryPageVO.builder().items(List.of(message))
                        .total(1).page(1).size(20).build());

        var result = handler.execute(
                new MessageQueryByTopicInput("instance-a", "TopicA", null, null, null),
                context("instance-a"));

        assertThat(result.items()).hasSize(1);
        var row = result.items().getFirst();
        assertThat(row.msgId()).isEqualTo("msg-1");
        assertThat(row.topic()).isEqualTo("TopicA");
    }

    @Test
    void topicQueryUsesPagedServiceAndOmitsBodiesByDefaultTest() {
        MessageRecordVO message = MessageRecordVO.builder()
                .msgId("msg-1").topic("TopicA").body("private-body")
                .bodyEncoding("UTF-8").bodyTruncated(false).build();
        when(messageService.queryMessagesPage("instance-a", "TopicA", null, "tagA",
                null, 1000L, 2000L, 1, 20))
                .thenReturn(MessageQueryPageVO.builder().items(List.of(message))
                        .total(200).page(1).size(20).resultMayBeTruncated(true).build());
        ToolExecutionContext request = ToolExecutionContext.of("instance-a", null, Map.of(
                "instanceId", "instance-a", "topicName", "TopicA", "tag", "tagA",
                "startTime", 1000L, "endTime", 2000L));

        Object result = handler.execute(request.convertInput(handler.inputType()), request);
        var mapper = new LegacyJackson2Config().jackson2ObjectMapper();
        Map<String, Object> serialized = mapper.convertValue(result, new TypeReference<>() { });

        assertThat(serialized).containsEntry("total", 200L).containsEntry("page", 1)
                .containsEntry("size", 20).containsEntry("resultMayBeTruncated", true);
        Map<String, Object> item = mapper.convertValue(((List<?>) serialized.get("items")).getFirst(),
                new TypeReference<>() { });
        assertThat(item)
                .doesNotContainKeys("body", "bodyEncoding", "bodyTruncated");
        verify(messageService).queryMessagesPage("instance-a", "TopicA", null, "tagA",
                null, 1000L, 2000L, 1, 20);
    }

    @Test
    void topicQueryRespectsCustomPageAndExplicitBodyRequestTest() {
        MessageRecordVO message = MessageRecordVO.builder()
                .msgId("msg-21").topic("TopicA").body("hello").bodyEncoding("UTF-8")
                .bodyTruncated(false).build();
        when(messageService.queryMessagesPage("instance-a", "TopicA", null, null,
                null, null, null, 2, 10))
                .thenReturn(MessageQueryPageVO.builder().items(List.of(message))
                        .total(21).page(2).size(10).build());

        var result = handler.execute(new MessageQueryByTopicInput(
                "instance-a", "TopicA", null, null, null, 2, 10, true),
                context("instance-a"));

        assertThat(result.total()).isEqualTo(21);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(10);
        var mapper = new LegacyJackson2Config().jackson2ObjectMapper();
        Map<String, Object> serialized = mapper.convertValue(result.items().getFirst(),
                new TypeReference<>() { });
        assertThat(serialized).containsEntry("body", "hello")
                .containsEntry("bodyEncoding", "UTF-8")
                .containsEntry("bodyTruncated", false);
        verify(messageService).queryMessagesPage("instance-a", "TopicA", null, null,
                null, null, null, 2, 10);
    }

    @Test
    void executeShouldConvertNumericTimeArguments() {
        when(messageService.queryMessagesPage(any(), any(), any(), any(), any(), any(), any(),
                any(int.class), any(int.class)))
                .thenReturn(MessageQueryPageVO.builder().items(List.of())
                        .total(0).page(1).size(20).build());

        handler.execute(
                new MessageQueryByTopicInput("instance-a", "TopicA", null, 1000L, 2000L),
                context("instance-a"));

        verify(messageService)
                .queryMessagesPage(eq("instance-a"), eq("TopicA"), isNull(), isNull(), isNull(),
                        eq(1000L), eq(2000L), eq(1), eq(20));
    }
}
