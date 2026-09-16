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
package org.apache.rocketmq.studio.provider.apache;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MessageQueueSelector;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.attribute.TopicMessageType;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.MqClientPool;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.topic.SendMessageDTO;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.apache.rocketmq.studio.persistence.mapper.RmqGroupMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqTopicMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TypedMessageSendTest {
    private final RuntimeAdminClientResolver resolver = mock(RuntimeAdminClientResolver.class);
    private final DefaultMQProducer producer = mock(DefaultMQProducer.class);
    private final MqClientPool pool = mock(MqClientPool.class);
    private final AuditService audit = mock(AuditService.class);
    private final RocketMQAdminClientImpl client = new RocketMQAdminClientImpl(mock(MqAdminExtFactory.class),
            mock(RocketMQProperties.class), mock(RmqTopicMapper.class), mock(RmqGroupMapper.class), audit, resolver, pool);

    @BeforeEach
    void setUp() {
        when(resolver.executeProducer(eq("instance-a"), any())).thenAnswer(invocation ->
                invocation.<MqClientPool.ClientAction<DefaultMQProducer, Object>>getArgument(1).apply(producer));
    }

    @Test
    void sendsFifoWithBrokerRecognizedGroupAndStableQueueSelector() throws Exception {
        when(producer.send(any(Message.class), any(MessageQueueSelector.class), eq("order-123"))).thenReturn(success());
        SendMessageDTO request = request(TopicType.FIFO);
        request.setMessageGroup("order-123");

        assertThat(client.sendMessage(request).getMsgId()).isEqualTo("sent-message");
        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<MessageQueueSelector> selector = ArgumentCaptor.forClass(MessageQueueSelector.class);
        verify(producer).send(message.capture(), selector.capture(), eq("order-123"));
        assertThat(TopicMessageType.parseFromMessageProperty(message.getValue().getProperties())).isEqualTo(TopicMessageType.FIFO);
        assertThat(message.getValue().getProperty(MessageConst.PROPERTY_SHARDING_KEY)).isEqualTo("order-123");
        assertEnvelope(message.getValue());

        List<MessageQueue> queues = List.of(new MessageQueue("orders", "broker-a", 0),
                new MessageQueue("orders", "broker-a", 1), new MessageQueue("orders", "broker-b", 0));
        Message anotherBody = new Message("orders", "next-event".getBytes(StandardCharsets.UTF_8));
        assertThat(selector.getValue().select(queues, message.getValue(), "order-123"))
                .isEqualTo(selector.getValue().select(queues, anotherBody, "order-123"));
        verify(producer, never()).send(any(Message.class));
        verifyNoInteractions(pool);
    }

    @Test
    void sendsDelayUsingAnAbsoluteBrokerTimerProperty() throws Exception {
        when(producer.send(any(Message.class))).thenReturn(success());
        SendMessageDTO request = request(TopicType.DELAY);
        long delivery = System.currentTimeMillis() + 3_600_000;
        request.setDeliveryTimestamp(delivery);

        client.sendMessage(request);
        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(producer).send(message.capture());
        assertThat(TopicMessageType.parseFromMessageProperty(message.getValue().getProperties())).isEqualTo(TopicMessageType.DELAY);
        assertThat(message.getValue().getDeliverTimeMs()).isEqualTo(delivery);
        assertEnvelope(message.getValue());
        verify(audit).record(eq("SEND_MESSAGE"), eq("MESSAGE"), eq("orders"), isNull(),
                contains("deliveryTimestamp=" + delivery), eq("SUCCESS"));
    }

    @Test
    void preservesLegacyOrdinaryRequestsWithoutAType() throws Exception {
        when(producer.send(any(Message.class))).thenReturn(success());
        client.sendMessage(request(null));
        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(producer).send(message.capture());
        assertThat(TopicMessageType.parseFromMessageProperty(message.getValue().getProperties())).isEqualTo(TopicMessageType.NORMAL);
        assertEnvelope(message.getValue());
    }

    @ParameterizedTest
    @CsvSource({
        "FIFO, , , messageGroup is required",
        "FIFO, ' ', , messageGroup is required",
        "FIFO, order-123, 1, deliveryTimestamp is only supported",
        "DELAY, , , deliveryTimestamp must be in the future",
        "DELAY, , 0, deliveryTimestamp must be in the future",
        "DELAY, order-123, 1, messageGroup is only supported",
        "NORMAL, order-123, , messageGroup is only supported",
        "NORMAL, , 1, deliveryTimestamp is only supported",
        "TRANSACTION, , , Transaction messages require",
        "LITE, , , Lite messages require"
    })
    void rejectsIncompleteOrConflictingTypedOptionsBeforeAcquiringProducer(TopicType type, String group,
                                                                           Long delivery, String error) {
        SendMessageDTO request = request(type);
        request.setMessageGroup(group);
        request.setDeliveryTimestamp(delivery);
        assertThatThrownBy(() -> client.sendMessage(request)).isInstanceOf(BusinessException.class)
                .hasMessageContaining(error)
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode()).isEqualTo(400));
        verify(resolver, never()).executeProducer(any(), any());
        verifyNoInteractions(producer, pool);
        verify(audit).record(eq("SEND_MESSAGE"), eq("MESSAGE"), eq("orders"), isNull(), contains(error), eq("FAILED"));
    }

    @Test
    void reportsOrderedSendFailureWithoutFallingBackToRandomQueueSend() throws Exception {
        SendResult failure = success();
        failure.setSendStatus(SendStatus.FLUSH_DISK_TIMEOUT);
        when(producer.send(any(Message.class), any(MessageQueueSelector.class), any())).thenReturn(failure);
        SendMessageDTO request = request(TopicType.FIFO);
        request.setMessageGroup("order-123");
        assertThatThrownBy(() -> client.sendMessage(request)).isInstanceOf(BusinessException.class)
                .hasMessageContaining("FLUSH_DISK_TIMEOUT");
        verify(producer, never()).send(any(Message.class));
        verify(audit).record(eq("SEND_MESSAGE"), eq("MESSAGE"), eq("orders"), isNull(),
                contains("FLUSH_DISK_TIMEOUT"), eq("FAILED"));
    }

    @Test
    void doesNotReturnFailureAfterOrderedMessageWasDeliveredButAuditStorageFailed() throws Exception {
        when(producer.send(any(Message.class), any(MessageQueueSelector.class), any())).thenReturn(success());
        doThrow(new IllegalStateException("Audit unavailable")).when(audit)
                .record(anyString(), anyString(), anyString(), any(), anyString(), anyString());
        SendMessageDTO request = request(TopicType.FIFO);
        request.setMessageGroup("order-123");
        assertThat(client.sendMessage(request).getMsgId()).isEqualTo("sent-message");
    }

    private SendMessageDTO request(TopicType type) {
        return SendMessageDTO.builder().instanceId("instance-a").topic("orders").messageType(type)
                .tag("created").key("order-123").body("event payload").properties(Map.of("traceId", "trace-1")).build();
    }

    private void assertEnvelope(Message message) {
        assertThat(message.getTopic()).isEqualTo("orders");
        assertThat(message.getTags()).isEqualTo("created");
        assertThat(message.getKeys()).isEqualTo("order-123");
        assertThat(message.getUserProperty("traceId")).isEqualTo("trace-1");
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo("event payload");
    }

    private SendResult success() {
        SendResult result = new SendResult();
        result.setSendStatus(SendStatus.SEND_OK);
        result.setMsgId("sent-message");
        result.setOffsetMsgId("physical-message");
        return result;
    }
}
