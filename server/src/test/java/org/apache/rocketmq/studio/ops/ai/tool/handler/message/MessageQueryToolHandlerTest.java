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
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.config.LegacyJackson2Config;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

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
    void msgIdWinsOverUniqueKeyAndKeyTest() {
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
        when(messageService.queryMessagesPage(
                eq("instance-a"), eq("TopicA"), eq("msg-1"),
                isNull(), isNull(), isNull(), isNull(), eq(1), eq(20)))
                .thenReturn(MessageQueryPageVO.builder().items(List.of(message))
                        .total(1).page(1).size(20).build());

        var result = handler.execute(
                new MessageQueryInput("instance-a", "TopicA", "msg-1", "uniq-1", "order-123", 10L, 20L),
                context("instance-a"));

        assertThat(result.items()).hasSize(1);
        var row = result.items().getFirst();
        assertThat(row.msgId()).isEqualTo("msg-1");
        assertThat(row.topic()).isEqualTo("TopicA");
        assertThat(row.tag()).isEqualTo("tag1");
        assertThat(row.storeTime()).isEqualTo(1000L);
        assertThat(row.body()).isNull();
        assertThat(row.size()).isEqualTo(5);
        var mapper = new LegacyJackson2Config().jackson2ObjectMapper();
        Map<String, Object> serialized = mapper.convertValue(result, new TypeReference<>() { });
        assertThat(serialized).containsKeys("items", "total", "page", "size", "resultMayBeTruncated");
        Map<String, Object> item = mapper.convertValue(((List<?>) serialized.get("items")).getFirst(),
                new TypeReference<>() { });
        assertThat(item)
                .doesNotContainKeys("body", "bodyEncoding", "bodyTruncated");
        // The msgId path is a direct read: the time window never reaches the service.
        verify(messageService).queryMessagesPage(
                eq("instance-a"), eq("TopicA"), eq("msg-1"), isNull(), isNull(), isNull(), isNull(),
                eq(1), eq(20));
    }

    @Test
    void uniqueKeyPathPassesTimeWindowTest() {
        MessageRecordVO message = MessageRecordVO.builder()
                .msgId("msg-2")
                .topic("TopicA")
                .body("private-body")
                .storeTime(2000L)
                .size(7)
                .build();
        when(messageService.queryMessageByUniqueKey("instance-a", "TopicA", "uniq-1", 1000L, 2000L))
                .thenReturn(List.of(message));

        var result = handler.execute(
                new MessageQueryInput("instance-a", "TopicA", null, "uniq-1", "order-123", 1000L, 2000L),
                context("instance-a"));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().msgId()).isEqualTo("msg-2");
        Map<String, Object> serialized = new LegacyJackson2Config().jackson2ObjectMapper()
                .convertValue(result.items().getFirst(), new TypeReference<>() { });
        assertThat(serialized).doesNotContainKeys("body", "bodyEncoding", "bodyTruncated");
        verify(messageService).queryMessageByUniqueKey("instance-a", "TopicA", "uniq-1", 1000L, 2000L);
    }

    @Test
    void uniqueKeySecondPageIsEmptyButRetainsTheFirstPageTotalTest() {
        MessageRecordVO message = MessageRecordVO.builder()
                .msgId("msg-2").topic("TopicA").body("private-body").build();
        when(messageService.queryMessageByUniqueKey("instance-a", "TopicA", "uniq-1", null, null))
                .thenReturn(List.of(message));

        var result = handler.execute(new MessageQueryInput(
                "instance-a", "TopicA", null, "uniq-1", null, null, null, 2, 20, null),
                context("instance-a"));

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.resultMayBeTruncated()).isFalse();
    }

    @Test
    void keyPathPassesTimeWindowTest() {
        MessageRecordVO message = MessageRecordVO.builder()
                .msgId("msg-3")
                .topic("TopicA")
                .key("order-123")
                .storeTime(1000L)
                .size(5)
                .build();
        when(messageService.queryMessagesPage(
                eq("instance-a"), eq("TopicA"), isNull(), isNull(),
                eq("order-123"), eq(1000L), eq(2000L), eq(1), eq(20)))
                .thenReturn(MessageQueryPageVO.builder().items(List.of(message))
                        .total(1).page(1).size(20).build());

        var result = handler.execute(
                new MessageQueryInput("instance-a", "TopicA", null, null, "order-123", 1000L, 2000L),
                context("instance-a"));

        assertThat(result.items()).hasSize(1);
        var row = result.items().getFirst();
        assertThat(row.msgId()).isEqualTo("msg-3");
        assertThat(row.key()).isEqualTo("order-123");
    }

    @Test
    void keyQueryUsesRequestedPageAndPreservesTruncationMetadataTest() {
        MessageRecordVO message = MessageRecordVO.builder()
                .msgId("msg-22").topic("TopicA").body("hello").bodyEncoding("UTF-8")
                .bodyTruncated(true).build();
        when(messageService.queryMessagesPage("instance-a", "TopicA", null, null,
                "order-123", 1000L, 2000L, 2, 10))
                .thenReturn(MessageQueryPageVO.builder().items(List.of(message))
                        .total(21).page(2).size(10).resultMayBeTruncated(true).build());
        ToolExecutionContext request = ToolExecutionContext.of("instance-a", null, Map.of(
                "instanceId", "instance-a", "topicName", "TopicA", "key", "order-123",
                "startTime", 1000L, "endTime", 2000L,
                "page", 2, "pageSize", 10, "includeBody", true));

        Object result = handler.execute(request.convertInput(handler.inputType()), request);
        var mapper = new LegacyJackson2Config().jackson2ObjectMapper();
        Map<String, Object> serialized = mapper.convertValue(result, new TypeReference<>() { });

        assertThat(serialized).containsEntry("total", 21L).containsEntry("page", 2)
                .containsEntry("size", 10).containsEntry("resultMayBeTruncated", true);
        Map<String, Object> item = mapper.convertValue(((List<?>) serialized.get("items")).getFirst(),
                new TypeReference<>() { });
        assertThat(item)
                .containsEntry("body", "hello").containsEntry("bodyEncoding", "UTF-8")
                .containsEntry("bodyTruncated", true);
        verify(messageService).queryMessagesPage("instance-a", "TopicA", null, null,
                "order-123", 1000L, 2000L, 2, 10);
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
}
