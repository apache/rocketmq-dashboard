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

import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.message.MessageQueryPageVO;
import org.apache.rocketmq.studio.instance.message.MessageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageQueryToolHandlerTest {

    @Mock
    private MessageService messageService;

    @InjectMocks
    private MessageQueryToolHandler handler;

    @Test
    void executeShouldForwardPagingAndProjectIncludedBodies() {
        MessageRecordVO message = message("hello", "UTF-8", false);
        when(messageService.queryMessagesPage(
                        eq("instance-a"), eq("TopicA"), any(), any(), any(), any(), any(), eq(2), eq(5)))
                .thenReturn(page(List.of(message), 2, 5));

        Object result = handler.execute(Map.of(
                "cluster", "instance-a",
                "topic", "TopicA",
                "page", 2,
                "pageSize", 5,
                "includeBody", true));

        assertThat(result).isInstanceOf(Map.class);
        List<?> rows = (List<?>) asMap(result).get("items");
        assertThat(rows).hasSize(1);
        Map<?, ?> row = (Map<?, ?>) rows.get(0);
        assertThat(row.get("msgId")).isEqualTo("msg-1");
        assertThat(row.get("topic")).isEqualTo("TopicA");
        assertThat(row.get("tag")).isEqualTo("tag1");
        assertThat(row.get("storeTime")).isEqualTo(1000L);
        assertThat(row.get("body")).isEqualTo("hello");
        assertThat(row.get("size")).isEqualTo(5);
        verify(messageService).queryMessagesPage(
                eq("instance-a"), eq("TopicA"), any(), any(), any(), any(), any(), eq(2), eq(5));
    }

    @Test
    void executeShouldUseDefaultPageAndOmitBody() {
        MessageRecordVO message = message("hello", "UTF-8", false);
        MessageQueryPageVO page = MessageQueryPageVO.builder()
                .items(List.of(message))
                .total(42)
                .page(1)
                .size(20)
                .resultMayBeTruncated(true)
                .build();
        when(messageService.queryMessagesPage(
                        eq("instance-a"), eq("TopicA"), any(), any(), any(), any(), any(), eq(1), eq(20)))
                .thenReturn(page);

        Object result = handler.execute(Map.of("cluster", "instance-a", "topic", "TopicA"));

        assertThat(result).isInstanceOf(Map.class);
        Map<String, Object> envelope = asMap(result);
        assertThat(envelope).containsEntry("total", 42L)
                .containsEntry("page", 1)
                .containsEntry("size", 20)
                .containsEntry("resultMayBeTruncated", true);
        List<?> items = (List<?>) envelope.get("items");
        assertThat(items).hasSize(1);
        assertThat(asMap(items.get(0)))
                .doesNotContainKeys("body", "bodyEncoding", "bodyTruncated");
        verify(messageService).queryMessagesPage(
                eq("instance-a"), eq("TopicA"), any(), any(), any(), any(), any(), eq(1), eq(20));
    }

    @Test
    void executeShouldConvertNumericTimeArguments() {
        when(messageService.queryMessagesPage(
                        any(), any(), any(), any(), any(), any(), any(), eq(1), eq(20)))
                .thenReturn(page(List.of(), 1, 20));

        handler.execute(Map.of("cluster", "instance-a", "topic", "TopicA",
                "startTime", 1000L, "endTime", 2000L));

        verify(messageService)
                .queryMessagesPage(
                        eq("instance-a"), any(), any(), any(), any(), eq(1000L), eq(2000L), eq(1), eq(20));
    }

    @Test
    void executeShouldRejectTimestampAboveLongRangeInsteadOfSilentlyWrapping() {
        java.math.BigInteger overflow =
                java.math.BigInteger.valueOf(Long.MAX_VALUE).add(java.math.BigInteger.ONE);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> handler.execute(Map.of(
                                "cluster", "instance-a", "topic", "TopicA", "startTime", overflow)))
                .isInstanceOf(org.apache.rocketmq.studio.common.exception.BusinessException.class)
                .hasMessageContaining("epoch-milliseconds");
    }

    @Test
    void executeShouldRejectNonFiniteTimestampInsteadOfWrapping() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> handler.execute(Map.of(
                                "cluster", "instance-a", "topic", "TopicA",
                                "startTime", Double.POSITIVE_INFINITY)))
                .isInstanceOf(org.apache.rocketmq.studio.common.exception.BusinessException.class)
                .hasMessageContaining("epoch-milliseconds");
    }

    @Test
    void executeShouldRejectNonNumericTimestamp() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> handler.execute(Map.of(
                                "cluster", "instance-a", "topic", "TopicA",
                                "startTime", "not-a-number")))
                .isInstanceOf(org.apache.rocketmq.studio.common.exception.BusinessException.class)
                .hasMessageContaining("epoch-milliseconds");
    }

    @Test
    void executeShouldConvertInBigIntegerTimestampExactly() {
        when(messageService.queryMessagesPage(
                        any(), any(), any(), any(), any(), any(), any(), eq(1), eq(20)))
                .thenReturn(page(List.of(), 1, 20));

        handler.execute(Map.of("cluster", "instance-a", "topic", "TopicA",
                "startTime", java.math.BigInteger.valueOf(123456789L)));

        verify(messageService)
                .queryMessagesPage(eq("instance-a"), any(), any(), any(), any(),
                        eq(123456789L), any(), eq(1), eq(20));
    }

    private static MessageRecordVO message(String body, String bodyEncoding, boolean bodyTruncated) {
        return MessageRecordVO.builder()
                .msgId("msg-1")
                .topic("TopicA")
                .tag("tag1")
                .key("key1")
                .body(body)
                .bodyEncoding(bodyEncoding)
                .bodyTruncated(bodyTruncated)
                .storeTime(1000L)
                .bornHost("10.0.0.1")
                .storeHost("10.0.0.2")
                .size(5)
                .build();
    }

    private static MessageQueryPageVO page(List<MessageRecordVO> items, int page, int size) {
        return MessageQueryPageVO.builder()
                .items(items)
                .total(items.size())
                .page(page)
                .size(size)
                .resultMayBeTruncated(false)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }
}
