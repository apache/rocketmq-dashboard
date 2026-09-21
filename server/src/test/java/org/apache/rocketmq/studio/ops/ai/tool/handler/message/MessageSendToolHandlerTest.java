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

import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.instance.topic.SendMessageDTO;
import org.apache.rocketmq.studio.instance.topic.SendMessageVO;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageSendInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageSendOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageSendToolHandlerTest {

    private final MetadataService metadata = mock(MetadataService.class);
    private final MessageSendToolHandler handler = new MessageSendToolHandler(metadata);

    @ParameterizedTest
    @ValueSource(strings = {"{", "[]", "{\"nested\":{\"key\":\"value\"}}"})
    void malformedPropertiesFailDuringPreviewAndExecutionTest(String properties) {
        givenTopicType(TopicType.NORMAL);
        MessageSendInput request = new MessageSendInput("instance-a", "TopicA", "hello", null, null,
                null, null, properties);

        assertThatThrownBy(() -> handler.preview(request, context("instance-a")))
                .isInstanceOf(ToolExecutionException.class);
        assertThatThrownBy(() -> handler.execute(request, context("instance-a")))
                .isInstanceOf(ToolExecutionException.class);
        verify(metadata, never()).sendMessage(any(SendMessageDTO.class));
    }

    private void givenTopicType(TopicType type) {
        TopicVO topic = new TopicVO();
        topic.setName("TopicA");
        topic.setType(type);
        when(metadata.findTopic("instance-a", null, "TopicA")).thenReturn(Optional.of(topic));
    }

    private void givenSendSucceeds() {
        when(metadata.sendMessage(any(SendMessageDTO.class))).thenReturn(SendMessageVO.builder()
                .msgId("msg-1")
                .offsetMsgId("offset-1")
                .sendTime(1234L)
                .build());
    }

    private static MessageSendInput input(String messageGroup, Long deliveryTimestamp) {
        return new MessageSendInput("instance-a", "TopicA", "hello", messageGroup, deliveryTimestamp,
                "tagA", "keyA", "{\"biz\":\"v\"}");
    }

    @Test
    void nameMatchesCatalogToolTest() {
        assertThat(handler.name()).isEqualTo("rmq.message.send");
    }

    @Test
    void normalTopicSendsDtoWithTypedFieldsTest() {
        givenTopicType(TopicType.NORMAL);
        givenSendSucceeds();

        MessageSendOutput output = handler.execute(input(null, null), context("instance-a"));

        assertThat(output).isEqualTo(new MessageSendOutput("msg-1", "offset-1", 1234L));
        ArgumentCaptor<SendMessageDTO> captor = ArgumentCaptor.forClass(SendMessageDTO.class);
        verify(metadata).sendMessage(captor.capture());
        SendMessageDTO request = captor.getValue();
        assertThat(request.getInstanceId()).isEqualTo("instance-a");
        assertThat(request.getTopic()).isEqualTo("TopicA");
        assertThat(request.getTag()).isEqualTo("tagA");
        assertThat(request.getKey()).isEqualTo("keyA");
        assertThat(request.getBody()).isEqualTo("hello");
        assertThat(request.getProperties()).containsExactly(Map.entry("biz", "v"));
        assertThat(request.getMessageGroup()).isNull();
        assertThat(request.getDeliveryTimestamp()).isNull();
    }

    @Test
    void unregisteredTopicKeepsPlainSendBehaviorTest() {
        when(metadata.findTopic("instance-a", null, "TopicA")).thenReturn(Optional.empty());
        givenSendSucceeds();

        handler.execute(input(null, null), context("instance-a"));

        verify(metadata).sendMessage(any(SendMessageDTO.class));
    }

    @Test
    void previewDescribesSendInInstanceTermsTest() {
        givenTopicType(TopicType.NORMAL);

        ToolPlan plan = handler.preview(input("group-x", null), context("instance-a"));

        assertThat(plan.summary()).isEqualTo("send a message to topic 'TopicA' in instance 'instance-a'.");
        assertThat(plan.after()).containsEntry("topic", "TopicA")
                .containsEntry("messageGroup", "group-x")
                .containsEntry("bodySize", 5);
    }

    @Test
    void fifoTopicWithoutMessageGroupIsRejectedTest() {
        givenTopicType(TopicType.FIFO);

        assertThatThrownBy(() -> handler.execute(input(null, null), context("instance-a")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("messageGroup is required for FIFO topic")
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> handler.preview(input("  ", null), context("instance-a")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("messageGroup is required for FIFO topic");

        verify(metadata, never()).sendMessage(any(SendMessageDTO.class));
    }

    @Test
    void fifoTopicWithMessageGroupIsForwardedTest() {
        givenTopicType(TopicType.FIFO);
        givenSendSucceeds();

        handler.execute(input("group-x", null), context("instance-a"));

        ArgumentCaptor<SendMessageDTO> captor = ArgumentCaptor.forClass(SendMessageDTO.class);
        verify(metadata).sendMessage(captor.capture());
        assertThat(captor.getValue().getMessageGroup()).isEqualTo("group-x");
    }

    @Test
    void delayTopicWithoutDeliveryTimestampIsRejectedTest() {
        givenTopicType(TopicType.DELAY);

        assertThatThrownBy(() -> handler.execute(input(null, null), context("instance-a")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("deliveryTimestamp is required for DELAY topic")
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode()).isEqualTo(400));
    }

    @Test
    void delayTopicWithPastDeliveryTimestampIsRejectedTest() {
        givenTopicType(TopicType.DELAY);

        assertThatThrownBy(() -> handler.execute(input(null, System.currentTimeMillis() - 1000L),
                context("instance-a")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("deliveryTimestamp must be in the future for DELAY topic")
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode()).isEqualTo(400));
    }

    @Test
    void delayTopicWithFutureDeliveryTimestampIsForwardedTest() {
        givenTopicType(TopicType.DELAY);
        givenSendSucceeds();
        long deliveryTimestamp = System.currentTimeMillis() + 60_000L;

        handler.execute(input(null, deliveryTimestamp), context("instance-a"));

        ArgumentCaptor<SendMessageDTO> captor = ArgumentCaptor.forClass(SendMessageDTO.class);
        verify(metadata).sendMessage(captor.capture());
        assertThat(captor.getValue().getDeliveryTimestamp()).isEqualTo(deliveryTimestamp);
    }

    @Test
    void transactionTopicIsRejectedTest() {
        givenTopicType(TopicType.TRANSACTION);

        assertThatThrownBy(() -> handler.execute(input("group-x", 1L), context("instance-a")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("sending transaction messages is not supported")
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> handler.preview(input(null, null), context("instance-a")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("sending transaction messages is not supported");
    }

    @Test
    void liteTopicKeepsPlainSendBehaviorTest() {
        givenTopicType(TopicType.LITE);
        givenSendSucceeds();

        handler.execute(input(null, null), context("instance-a"));

        verify(metadata).sendMessage(any(SendMessageDTO.class));
    }
}
