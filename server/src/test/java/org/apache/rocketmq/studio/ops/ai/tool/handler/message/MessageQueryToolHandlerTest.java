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
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.message.MessageQueryPageVO;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.message.MessageService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageQueryInput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageQueryToolHandlerTest {

    @Mock
    private MessageService messageService;

    @InjectMocks
    private MessageQueryToolHandler handler;

    @Test
    void nameMatchesCatalogToolTest() {
        assertThat(handler.name()).isEqualTo("rmq.message.query");
        assertThat(handler.inputType()).isEqualTo(MessageQueryInput.class);
    }

    @Test
    void msgIdWinsOverOtherIdentifiersAndUsesDefaultLimitTest() {
        MessageRecordVO message = message("msg-1", "hello", false);
        when(messageService.queryMessagesPage(
                eq("instance-a"), eq("TopicA"), eq("msg-1"),
                isNull(), isNull(), isNull(), isNull(), eq(1), eq(20)))
                .thenReturn(page(List.of(message), 1, 20, false));

        ToolExecutionContext request = ToolExecutionContext.of("instance-a", null, Map.of(
                "instanceId", "instance-a", "topicName", "TopicA", "msgId", "msg-1",
                "uniqueKey", "uniq-1", "key", "order-123", "startTime", 10L, "endTime", 20L));
        var result = handler.execute(request.convertInput(handler.inputType()), request);

        assertThat(result.items()).singleElement().satisfies(row -> {
            assertThat(row.msgId()).isEqualTo("msg-1");
            assertThat(row.topic()).isEqualTo("TopicA");
            assertThat(row.body()).isNull();
            assertThat(row.bodyEncoding()).isNull();
            assertThat(row.bodyTruncated()).isNull();
        });
        assertThat(result.skippedCount()).isZero();
        assertThat(result.resultMayBeTruncated()).isFalse();
        Map<String, Object> serialized = new LegacyJackson2Config().jackson2ObjectMapper()
                .convertValue(result, new TypeReference<>() { });
        assertThat(serialized)
                .containsOnlyKeys("items", "resultMayBeTruncated", "skippedCount");
        Map<String, Object> item = new LegacyJackson2Config().jackson2ObjectMapper()
                .convertValue(((List<?>) serialized.get("items")).getFirst(), new TypeReference<>() { });
        assertThat(item).doesNotContainKeys("body", "bodyEncoding", "bodyTruncated");
        verify(messageService).queryMessagesPage(
                eq("instance-a"), eq("TopicA"), eq("msg-1"), isNull(), isNull(), isNull(), isNull(),
                eq(1), eq(20));
    }

    @Test
    void uniqueKeyPathPassesTimeWindowTest() {
        when(messageService.queryMessageByUniqueKey("instance-a", "TopicA", "uniq-1", 1000L, 2000L))
                .thenReturn(List.of(message("msg-2", "private", false)));

        var result = handler.execute(
                new MessageQueryInput("instance-a", "TopicA", null, "uniq-1", "order-123",
                        1000L, 2000L),
                context("instance-a"));

        assertThat(result.items()).singleElement().satisfies(row -> {
            assertThat(row.msgId()).isEqualTo("msg-2");
            assertThat(row.body()).isNull();
        });
        assertThat(result.skippedCount()).isZero();
        assertThat(result.resultMayBeTruncated()).isFalse();
        verify(messageService).queryMessageByUniqueKey("instance-a", "TopicA", "uniq-1", 1000L, 2000L);
    }

    @Test
    void keyPathCapsLimitAndIncludesBodiesWhenRequestedTest() {
        MessageRecordVO message = message("msg-3", "hello", true);
        when(messageService.queryMessagesPage("instance-a", "TopicA", null, null,
                "order-123", 1000L, 2000L, 1, 100))
                .thenReturn(page(Collections.nCopies(100, message), 150, 100, false));
        ToolExecutionContext request = ToolExecutionContext.of("instance-a", null, Map.of(
                "instanceId", "instance-a", "topicName", "TopicA", "key", "order-123",
                "startTime", 1000L, "endTime", 2000L, "limit", 500, "includeBody", true));

        var result = handler.execute(request.convertInput(handler.inputType()), request);

        assertThat(result.items()).hasSize(100);
        assertThat(result.skippedCount()).isEqualTo(50);
        assertThat(result.resultMayBeTruncated()).isTrue();
        assertThat(result.items().getFirst()).satisfies(row -> {
            assertThat(row.body()).isEqualTo("hello");
            assertThat(row.bodyEncoding()).isEqualTo("UTF-8");
            assertThat(row.bodyTruncated()).isTrue();
        });
        verify(messageService).queryMessagesPage("instance-a", "TopicA", null, null,
                "order-123", 1000L, 2000L, 1, 100);
    }

    @Test
    void providerTruncationIsPreservedWithoutLocallySkippedRowsTest() {
        MessageRecordVO message = message("msg-4", null, false);
        when(messageService.queryMessagesPage("instance-a", "TopicA", null, null,
                "order-123", null, null, 1, 10))
                .thenReturn(page(List.of(message), 1, 10, true));

        var result = handler.execute(new MessageQueryInput(
                "instance-a", "TopicA", null, null, "order-123", null, null, 10, false),
                context("instance-a"));

        assertThat(result.items()).hasSize(1);
        assertThat(result.skippedCount()).isZero();
        assertThat(result.resultMayBeTruncated()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void missingEveryIdentifierIsRejectedWith400Test(String blank) {
        assertThatThrownBy(() -> handler.execute(
                new MessageQueryInput("instance-a", "TopicA", blank, blank, blank, null, null),
                context("instance-a")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("message query requires one of: msgId, uniqueKey, key")
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode()).isEqualTo(400));

        verifyNoInteractions(messageService);
    }

    private MessageRecordVO message(String msgId, String body, boolean bodyTruncated) {
        return MessageRecordVO.builder()
                .msgId(msgId)
                .topic("TopicA")
                .tag("tag1")
                .key("key1")
                .body(body)
                .bodyEncoding(body == null ? null : "UTF-8")
                .bodyTruncated(bodyTruncated)
                .storeTime(1000L)
                .bornHost("10.0.0.1")
                .storeHost("10.0.0.2")
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
