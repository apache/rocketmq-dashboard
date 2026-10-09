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

import org.apache.rocketmq.studio.instance.topic.LiteTopicService;
import org.apache.rocketmq.studio.instance.topic.LiteTopicSessionVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.litetopic.LiteTopicSessionInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.litetopic.LiteTopicSessionOutput;
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
class LiteTopicSessionToolHandlerTest {

    @Mock
    private LiteTopicService liteTopicService;

    @InjectMocks
    private LiteTopicSessionToolHandler handler;

    @Test
    void mapsTheSessionAndItsEntriesTest() {
        LiteTopicSessionVO.SessionLiteTopic entry = new LiteTopicSessionVO.SessionLiteTopic();
        entry.setTopicName("chat/sess-1/0");
        entry.setStatus("ACTIVE");
        entry.setTtlRemaining(1800L);
        when(liteTopicService.getSession("sess-1")).thenReturn(LiteTopicSessionVO.builder()
                .sessionId("sess-1")
                .clientId("client-1")
                .clientAddress("10.0.0.1:5678")
                .parentTopic("chat")
                .consumerGroup("cg-chat")
                .createTime(1789092000000L)
                .lastActiveTime(1789092600000L)
                .ttl(3600L)
                .ttlRemaining(1800L)
                .status("ACTIVE")
                .totalMessages(10L)
                .consumedMessages(4L)
                .pendingMessages(6L)
                .popProgress(40)
                .liteTopicCreationCount(1)
                .liteTopics(List.of(entry))
                .build());

        LiteTopicSessionOutput output = handler.execute(new LiteTopicSessionInput("sess-1"), context());

        verify(liteTopicService).getSession("sess-1");
        assertThat(output).isEqualTo(new LiteTopicSessionOutput(
                "sess-1", "client-1", "10.0.0.1:5678", "chat", "cg-chat",
                1789092000000L, 1789092600000L, 3600L, 1800L, "ACTIVE",
                10L, 4L, 6L, 40, 1,
                List.of(new LiteTopicSessionOutput.Entry("chat/sess-1/0", "ACTIVE", 1800L))));
    }

    @Test
    void keepsSparseSessionsPartialInsteadOfInventingFieldsTest() {
        when(liteTopicService.getSession("sess-2")).thenReturn(LiteTopicSessionVO.builder()
                .sessionId("sess-2")
                .build());

        LiteTopicSessionOutput output = handler.execute(new LiteTopicSessionInput("sess-2"), context());

        assertThat(output.sessionId()).isEqualTo("sess-2");
        assertThat(output.clientId()).isNull();
        assertThat(output.liteTopics()).isNull();
    }

    private static ToolExecutionContext context() {
        return ToolExecutionContext.of(null, null, Map.of());
    }
}
