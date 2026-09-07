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
        when(messageService.queryMessages(eq("instance-a"), eq("TopicA"), any(), any(), any(), any(), any()))
                .thenReturn(List.of(message));

        Object result = handler.execute(Map.of("cluster", "instance-a", "topic", "TopicA"));

        assertThat(result).isInstanceOf(List.class);
        List<?> rows = (List<?>) result;
        assertThat(rows).hasSize(1);
        Map<?, ?> row = (Map<?, ?>) rows.get(0);
        assertThat(row.get("msgId")).isEqualTo("msg-1");
        assertThat(row.get("topic")).isEqualTo("TopicA");
        assertThat(row.get("tag")).isEqualTo("tag1");
        assertThat(row.get("storeTime")).isEqualTo(1000L);
        assertThat(row.get("body")).isEqualTo("hello");
        assertThat(row.get("size")).isEqualTo(5);
    }

    @Test
    void executeShouldConvertNumericTimeArguments() {
        when(messageService.queryMessages(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        handler.execute(Map.of("cluster", "instance-a", "topic", "TopicA",
                "startTime", 1000L, "endTime", 2000L));

        verify(messageService)
                .queryMessages(eq("instance-a"), any(), any(), any(), any(), eq(1000L), eq(2000L));
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
        when(messageService.queryMessages(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        handler.execute(Map.of("cluster", "instance-a", "topic", "TopicA",
                "startTime", java.math.BigInteger.valueOf(123456789L)));

        verify(messageService)
                .queryMessages(eq("instance-a"), any(), any(), any(), any(),
                        eq(123456789L), any());
    }
}
