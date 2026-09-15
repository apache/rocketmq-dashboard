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
import org.apache.rocketmq.studio.common.config.LegacyJackson2Config;
import org.apache.rocketmq.studio.instance.message.MessageQueryPageVO;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.message.MessageService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.PageRequest;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageQueryByTopicInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
        when(messageService.queryMessagesPage(eq("instance-a"), eq("TopicA"), isNull(),
                isNull(), isNull(), any(), any(), eq(1), eq(20)))
                .thenReturn(MessageQueryPageVO.builder().items(List.of(message))
                        .total(1).page(1).size(20).resultMayBeTruncated(true).build());

        var result = handler.execute(
                new MessageQueryByTopicInput("instance-a", "TopicA", null, null, null),
                context("instance-a"));

        assertThat(result.pageOutput().items()).hasSize(1);
        var row = result.pageOutput().items().getFirst();
        assertThat(row.msgId()).isEqualTo("msg-1");
        assertThat(row.topic()).isEqualTo("TopicA");
        assertThat(row.body()).isNull();
        assertThat(result.pageOutput().page()).isEqualTo(1);
        assertThat(result.pageOutput().pageSize()).isEqualTo(20);
        assertThat(result.resultMayBeTruncated()).isTrue();
        Map<String, Object> serialized = new LegacyJackson2Config().jackson2ObjectMapper()
                .convertValue(result, new TypeReference<>() { });
        assertThat(serialized).containsKeys("page", "pageSize", "total", "items", "resultMayBeTruncated")
                .doesNotContainKey("pageOutput");
    }

    @Test
    void executeShouldConvertNumericTimeArguments() {
        MessageRecordVO message = MessageRecordVO.builder()
                .msgId("msg-2").topic("TopicA").body("hello").bodyEncoding("UTF-8")
                .bodyTruncated(false).build();
        when(messageService.queryMessagesPage(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(MessageQueryPageVO.builder().items(List.of(message))
                        .total(1).page(3).size(10).build());

        var result = handler.execute(
                new MessageQueryByTopicInput("instance-a", "TopicA", "TagA", 1000L, 2000L,
                        new PageRequest(3, 10), true),
                context("instance-a"));

        verify(messageService)
                .queryMessagesPage(eq("instance-a"), eq("TopicA"), isNull(), eq("TagA"), isNull(),
                        eq(1000L), eq(2000L), eq(3), eq(10));
        var row = result.pageOutput().items().getFirst();
        assertThat(row.body()).isEqualTo("hello");
        assertThat(row.bodyEncoding()).isEqualTo("UTF-8");
        assertThat(row.bodyTruncated()).isFalse();
    }
}
