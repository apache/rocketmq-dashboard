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
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageQueryByTopicInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageQueryByTopicToolHandlerTest {

    @Mock
    private MessageService messageService;

    @InjectMocks
    private MessageQueryByTopicToolHandler handler;

    @Test
    void executeUsesDefaultLimitAndReportsSkippedRowsTest() {
        MessageRecordVO message = message("msg-1", null, false);
        when(messageService.queryMessagesPage(eq("instance-a"), eq("TopicA"), isNull(),
                isNull(), isNull(), isNull(), isNull(), eq(1), eq(20)))
                .thenReturn(page(Collections.nCopies(20, message), 25, 20, false));

        var result = handler.execute(
                new MessageQueryByTopicInput("instance-a", "TopicA", null, null, null),
                context("instance-a"));

        assertThat(result.items()).hasSize(20);
        assertThat(result.skippedCount()).isEqualTo(5);
        assertThat(result.resultMayBeTruncated()).isTrue();
        assertThat(result.items().getFirst().body()).isNull();
        Map<String, Object> serialized = new LegacyJackson2Config().jackson2ObjectMapper()
                .convertValue(result, new TypeReference<>() { });
        assertThat(serialized)
                .containsOnlyKeys("items", "resultMayBeTruncated", "skippedCount");
    }

    @Test
    void executeUsesCustomLimitAndIncludesBodiesTest() {
        MessageRecordVO message = message("msg-2", "hello", false);
        when(messageService.queryMessagesPage("instance-a", "TopicA", null,
                "TagA", null, 1000L, 2000L, 1, 10))
                .thenReturn(page(List.of(message), 1, 10, false));

        var result = handler.execute(
                new MessageQueryByTopicInput("instance-a", "TopicA", "TagA", 1000L, 2000L,
                        10, true),
                context("instance-a"));

        verify(messageService).queryMessagesPage("instance-a", "TopicA", null,
                "TagA", null, 1000L, 2000L, 1, 10);
        assertThat(result.items()).singleElement().satisfies(row -> {
            assertThat(row.body()).isEqualTo("hello");
            assertThat(row.bodyEncoding()).isEqualTo("UTF-8");
            assertThat(row.bodyTruncated()).isFalse();
        });
        assertThat(result.skippedCount()).isZero();
        assertThat(result.resultMayBeTruncated()).isFalse();
    }

    @Test
    void executeCapsRequestedLimitAtOneHundredTest() {
        when(messageService.queryMessagesPage("instance-a", "TopicA", null,
                null, null, null, null, 1, 100))
                .thenReturn(page(List.of(), 0, 100, false));

        handler.execute(new MessageQueryByTopicInput(
                "instance-a", "TopicA", null, null, null, 500, false), context("instance-a"));

        verify(messageService).queryMessagesPage("instance-a", "TopicA", null,
                null, null, null, null, 1, 100);
    }

    private MessageRecordVO message(String msgId, String body, boolean bodyTruncated) {
        return MessageRecordVO.builder()
                .msgId(msgId)
                .topic("TopicA")
                .body(body)
                .bodyEncoding(body == null ? null : "UTF-8")
                .bodyTruncated(bodyTruncated)
                .storeTime(1000L)
                .size(body == null ? 0 : body.length())
                .build();
    }

    private MessageQueryPageVO page(
            List<MessageRecordVO> items, long total, int size, boolean resultMayBeTruncated) {
        return MessageQueryPageVO.builder()
                .items(items)
                .total(total)
                .page(1)
                .size(size)
                .resultMayBeTruncated(resultMayBeTruncated)
                .build();
    }
}
