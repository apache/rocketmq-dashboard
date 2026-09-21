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
package org.apache.rocketmq.studio.ops.ai.tool.handler.litetopic;

import org.apache.rocketmq.studio.instance.topic.LiteTopicItemVO;
import org.apache.rocketmq.studio.instance.topic.LiteTopicService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.litetopic.LiteTopicListItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.litetopic.LiteTopicListInput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiteTopicListToolHandlerTest {

    @Mock
    private LiteTopicService liteTopicService;

    @InjectMocks
    private LiteTopicListToolHandler handler;

    @Test
    void passesFiltersThroughAndKeepsUnreportableStatisticsAbsentTest() {
        when(liteTopicService.listLiteTopics("chat/sess-", "ns-a")).thenReturn(List.of(
                LiteTopicItemVO.builder()
                        .topicPattern("chat/sess-")
                        .namespace("ns-a")
                        .topicCount(12)
                        .consumerCount(3)
                        .totalBacklog(120L)
                        .averageTTL(3600L)
                        .ttlStatus("ACTIVE")
                        .lastActiveTime(1789092600000L)
                        .sessionIds(List.of("sess-1", "sess-2"))
                        .build(),
                LiteTopicItemVO.builder()
                        .topicPattern("orders/")
                        .build()));

        ListOutput<LiteTopicListItem> result = handler.execute(
                new LiteTopicListInput("chat/sess-", "ns-a"), context());

        verify(liteTopicService).listLiteTopics("chat/sess-", "ns-a");
        assertThat(result.items()).containsExactly(
                new LiteTopicListItem("chat/sess-", "ns-a", 12, 3, 120L, 3600L, "ACTIVE",
                        1789092600000L, List.of("sess-1", "sess-2")),
                new LiteTopicListItem("orders/", null, null, null, null, null, null, null, null));
    }

    private static ToolExecutionContext context() {
        return ToolExecutionContext.of(null, null, Map.of());
    }
}
