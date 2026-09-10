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

import org.apache.rocketmq.studio.instance.message.ConsumerStatusVO;
import org.apache.rocketmq.studio.instance.message.MessageService;
import org.apache.rocketmq.studio.instance.message.TraceNodeVO;
import org.apache.rocketmq.studio.instance.message.TraceRecordVO;
import org.apache.rocketmq.studio.common.domain.enums.DeliveryStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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

        Object result = handler.execute(Map.of(
                "cluster", "instance-a", "msgId", "msg-1", "topic", "TopicA"));

        assertThat(result).isInstanceOf(Map.class);
        Map<?, ?> row = (Map<?, ?>) result;
        assertThat(row.get("msgId")).isEqualTo("msg-1");
        List<?> nodes = (List<?>) row.get("nodes");
        assertThat(nodes).hasSize(1);
        Map<?, ?> node = (Map<?, ?>) nodes.get(0);
        assertThat(node.get("title")).isEqualTo("Send message");
        assertThat(node.get("status")).isEqualTo("SUCCESS");
        List<?> statuses = (List<?>) row.get("consumerStatus");
        assertThat(statuses).hasSize(1);
        Map<?, ?> status = (Map<?, ?>) statuses.get(0);
        assertThat(status.get("group")).isEqualTo("group-a");
        assertThat(status.get("deliveryStatus")).isEqualTo("success");

        verify(messageService).getMessageTrace("instance-a", "msg-1", "TopicA");
    }

    @Test
    void handlerNameShouldBeRmqMessageTrace() {
        assertThat(handler.name()).isEqualTo("rmq.message.trace");
    }

    @Test
    void blankNodeAndStatusFieldsProjectAsBlankStrings() {
        TraceRecordVO trace = TraceRecordVO.builder()
                .nodes(List.of(TraceNodeVO.builder().timestamp(1000L).costTime(5L).build()))
                .consumerStatus(List.of(ConsumerStatusVO.builder()
                        .consumeTime(2000L)
                        .retryCount(0)
                        .build()))
                .build();
        when(messageService.getMessageTrace(eq("instance-a"), eq("msg-1"), eq("TopicA")))
                .thenReturn(trace);

        Map<?, ?> row = (Map<?, ?>) handler.execute(Map.of(
                "cluster", "instance-a", "msgId", "msg-1", "topic", "TopicA"));

        Map<?, ?> node = (Map<?, ?>) ((List<?>) row.get("nodes")).get(0);
        assertThat(node.get("title")).isEqualTo("");
        assertThat(node.get("status")).isEqualTo("");
        assertThat(node.get("description")).isEqualTo("");
        Map<?, ?> status = (Map<?, ?>) ((List<?>) row.get("consumerStatus")).get(0);
        assertThat(status.get("group")).isEqualTo("");
        assertThat(status.get("deliveryStatus")).isEqualTo("");
    }

    @Test
    void traceWithoutNodesOrStatusesProjectsEmptyCollections() {
        when(messageService.getMessageTrace(eq("instance-a"), eq("msg-1"), eq("TopicA")))
                .thenReturn(TraceRecordVO.builder()
                        .nodes(List.of())
                        .consumerStatus(List.of())
                        .build());

        Map<?, ?> row = (Map<?, ?>) handler.execute(Map.of(
                "cluster", "instance-a", "msgId", "msg-1", "topic", "TopicA"));

        assertThat((List<?>) row.get("nodes")).isEmpty();
        assertThat((List<?>) row.get("consumerStatus")).isEmpty();
    }
}
