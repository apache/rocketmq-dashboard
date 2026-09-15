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
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageQueryByOffsetInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

@ExtendWith(MockitoExtension.class)
class MessageQueryByOffsetToolHandlerTest {

    @Mock
    private MessageService messageService;

    @InjectMocks
    private MessageQueryByOffsetToolHandler handler;

    @Test
    void nameMatchesCatalogToolTest() {
        assertThat(handler.name()).isEqualTo("rmq.message.query_by_offset");
        assertThat(handler.inputType()).isEqualTo(MessageQueryByOffsetInput.class);
    }

    @Test
    void returnsSingleItemWhenOffsetHoldsMessageTest() {
        MessageRecordVO message = MessageRecordVO.builder()
                .msgId("msg-1")
                .topic("TopicA")
                .brokerName("broker-a")
                .queueId(2)
                .queueOffset(17L)
                .storeTime(1000L)
                .size(5)
                .build();
        when(messageService.pullMessageAtOffset("instance-a", "TopicA", "broker-a", 2, 17L))
                .thenReturn(message);

        ListOutput<MessageItem> result = handler.execute(
                new MessageQueryByOffsetInput("instance-a", "TopicA", "broker-a", 2, 17L),
                context("instance-a"));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().msgId()).isEqualTo("msg-1");
        verify(messageService).pullMessageAtOffset("instance-a", "TopicA", "broker-a", 2, 17L);
    }

    @Test
    void returnsEmptyItemsWhenOffsetIsOutOfRangeTest() {
        when(messageService.pullMessageAtOffset("instance-a", "TopicA", "broker-a", 0, 999L))
                .thenReturn(null);

        ListOutput<MessageItem> result = handler.execute(
                new MessageQueryByOffsetInput("instance-a", "TopicA", "broker-a", 0, 999L),
                context("instance-a"));

        assertThat(result.items()).isEmpty();
    }
}
