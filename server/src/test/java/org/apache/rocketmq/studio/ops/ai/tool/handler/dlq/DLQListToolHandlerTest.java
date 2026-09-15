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
package org.apache.rocketmq.studio.ops.ai.tool.handler.dlq;

import org.apache.rocketmq.studio.ops.ai.tool.contract.common.PageRequest;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.instance.dlq.DLQGroupVO;
import org.apache.rocketmq.studio.instance.dlq.DLQMessageVO;
import org.apache.rocketmq.studio.instance.dlq.DLQService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.dlq.DLQListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.dlq.DLQListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.TimeRange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

@ExtendWith(MockitoExtension.class)
class DLQListToolHandlerTest {

    @Mock
    private DLQService dlqService;

    @InjectMocks
    private DLQListToolHandler handler;

    @Test
    void executeWithoutGroupShouldListDLQGroups() {
        assertThat(handler.name()).isEqualTo("rmq.dlq.list");
        DLQGroupVO group = DLQGroupVO.builder()
                .groupName("group-1")
                .dlqTopic("%DLQ%group-1")
                .messageCount(3_000_000_000L)
                .retryCount(3)
                .status("ACTIVE")
                .statsAvailable(true)
                .lastEnqueueTime(LocalDateTime.of(2024, 1, 1, 12, 0))
                .build();
        when(dlqService.listDLQGroups(eq("instance-a"), isNull(), eq(1), eq(20)))
                .thenReturn(PageResult.of(List.of(group), 1, 1, 20));

        DLQListOutput response = handler.execute(new DLQListInput(
                "instance-a", null, null, new PageRequest(1, 20), null), context("instance-a"));

        assertThat(response.cluster()).isEqualTo("instance-a");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(20);
        assertThat(response.total()).isEqualTo(1L);
        List<?> items = response.items();
        assertThat(items).hasSize(1);
        DLQListOutput.DLQGroupItem item = (DLQListOutput.DLQGroupItem) items.getFirst();
        assertThat(item.groupName()).isEqualTo("group-1");
        assertThat(item.dlqTopic()).isEqualTo("%DLQ%group-1");
        assertThat(item.messageCount()).isEqualTo(3_000_000_000L);
        assertThat(item.retryCount()).isEqualTo(3);
        assertThat(item.status()).isEqualTo("ACTIVE");
        assertThat(item.statsAvailable()).isEqualTo(true);
        verify(dlqService).listDLQGroups(eq("instance-a"), isNull(), eq(1), eq(20));
    }

    @Test
    void executeWithGroupShouldListDLQMessages() {
        DLQMessageVO message = DLQMessageVO.builder()
                .msgId("msg-1")
                .topic("TopicA")
                .queueId(0)
                .offset(10)
                .storeTime(1700000000000L)
                .keys("key1")
                .body("hello")
                .build();
        when(dlqService.listMessages(eq("instance-a"), eq("group-1"),
                eq(1000L), eq(2000L), eq(1), eq(20)))
                .thenReturn(PageResult.of(List.of(message), 1, 1, 20));

        DLQListOutput response = handler.execute(new DLQListInput(
                "instance-a", "group-1", new TimeRange(1000L, 2000L), new PageRequest(1, 20), null),
                context("instance-a"));

        assertThat(response.cluster()).isEqualTo("instance-a");
        assertThat(response.group()).isEqualTo("group-1");
        assertThat(response.total()).isEqualTo(1L);
        List<?> items = response.items();
        assertThat(items).hasSize(1);
        DLQListOutput.DLQMessageItem item = (DLQListOutput.DLQMessageItem) items.getFirst();
        assertThat(item.msgId()).isEqualTo("msg-1");
        assertThat(item.topic()).isEqualTo("TopicA");
        assertThat(item.queueId()).isEqualTo(0);
        assertThat(item.offset()).isEqualTo(10L);
        assertThat(item.storeTime()).isEqualTo(1700000000000L);
        assertThat(item.keys()).isEqualTo("key1");
        assertThat(item.body()).isEqualTo("hello");
        verify(dlqService).listMessages(eq("instance-a"), eq("group-1"),
                eq(1000L), eq(2000L), eq(1), eq(20));
    }
}
