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
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageQueryByIdInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

@ExtendWith(MockitoExtension.class)
class MessageQueryByIdToolHandlerTest {

    @Mock
    private MessageService messageService;

    @InjectMocks
    private MessageQueryByIdToolHandler handler;

    @Test
    void executeShouldDelegateToMessageServiceAndProject() {
        MessageRecordVO message = MessageRecordVO.builder()
                .msgId("msg-1")
                .topic("TopicA")
                .tag("tag1")
                .key("key1")
                .body("hello")
                .bodyEncoding("UTF-8")
                .bodyTruncated(false)
                .storeTime(1000L)
                .bornHost("10.0.0.1")
                .storeHost("10.0.0.2")
                .size(5)
                .build();
        when(messageService.queryMessages(
                eq("instance-a"), eq("TopicA"), eq("msg-1"),
                isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(message));

        ListOutput<MessageItem> result = handler.execute(
                new MessageQueryByIdInput("instance-a", "msg-1", "TopicA"),
                context("instance-a"));

        assertThat(result.items()).hasSize(1);
        MessageItem row = result.items().getFirst();
        assertThat(row.msgId()).isEqualTo("msg-1");
        assertThat(row.topic()).isEqualTo("TopicA");
        assertThat(row.tag()).isEqualTo("tag1");
        assertThat(row.storeTime()).isEqualTo(1000L);
        assertThat(row.body()).isEqualTo("hello");
        assertThat(row.size()).isEqualTo(5);
    }
}
