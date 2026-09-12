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
package org.apache.rocketmq.studio.ops.ai.tool;

import org.apache.rocketmq.studio.ops.ai.tool.handler.message.MessageTraceToolHandler;

import org.apache.rocketmq.studio.instance.message.ConsumerStatusVO;
import org.apache.rocketmq.studio.instance.message.MessageService;
import org.apache.rocketmq.studio.instance.message.TraceNodeVO;
import org.apache.rocketmq.studio.instance.message.TraceRecordVO;
import org.apache.rocketmq.studio.common.domain.enums.DeliveryStatus;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageQueryByIdInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageTraceOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageTraceToolHandlerTest {

    @Mock
    private MessageService messageService;

    @InjectMocks
    private MessageTraceToolHandler handler;

    @Test
    void executeShouldDelegateToMessageServiceAndProject() {
        TraceRecordVO trace = TraceRecordVO.builder()
                .nodes(List.of(TraceNodeVO.builder()
                        .title("Send message")
                        .timestamp(1000L)
                        .status("SUCCESS")
                        .costTime(5L)
                        .description("msg-1 sent")
                        .build()))
                .consumerStatus(List.of(ConsumerStatusVO.builder()
                        .group("group-a")
                        .deliveryStatus(DeliveryStatus.success)
                        .consumeTime(2000L)
                        .retryCount(0)
                        .build()))
                .build();
        when(messageService.getMessageTrace(eq("instance-a"), eq("msg-1"), eq("TopicA")))
                .thenReturn(trace);

        MessageTraceOutput result = handler.execute(
                new MessageQueryByIdInput("instance-a", "msg-1", "TopicA"),
                context("instance-a"));

        assertThat(result.msgId()).isEqualTo("msg-1");
        assertThat(result.nodes()).singleElement().satisfies(node -> {
            assertThat(node.title()).isEqualTo("Send message");
            assertThat(node.status()).isEqualTo("SUCCESS");
        });
        assertThat(result.consumerStatus()).singleElement().satisfies(status -> {
            assertThat(status.group()).isEqualTo("group-a");
            assertThat(status.deliveryStatus()).isEqualTo("success");
        });

        verify(messageService).getMessageTrace("instance-a", "msg-1", "TopicA");
    }
}
