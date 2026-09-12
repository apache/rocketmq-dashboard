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

import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.message.MessageService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageQueryByTopicInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

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
        when(messageService.queryMessages(eq("instance-a"), eq("TopicA"), isNull(), isNull(), isNull(), any(), any()))
                .thenReturn(List.of(message));

        ListOutput<MessageItem> result = handler.execute(
                new MessageQueryByTopicInput("instance-a", "TopicA", null, null, null),
                context("instance-a"));

        assertThat(result.items()).hasSize(1);
        MessageItem row = result.items().getFirst();
        assertThat(row.msgId()).isEqualTo("msg-1");
        assertThat(row.topic()).isEqualTo("TopicA");
    }

    @Test
    void executeShouldConvertNumericTimeArguments() {
        when(messageService.queryMessages(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        handler.execute(
                new MessageQueryByTopicInput("instance-a", "TopicA", null, 1000L, 2000L),
                context("instance-a"));

        verify(messageService)
                .queryMessages(eq("instance-a"), eq("TopicA"), isNull(), isNull(), isNull(), eq(1000L), eq(2000L));
    }
}
