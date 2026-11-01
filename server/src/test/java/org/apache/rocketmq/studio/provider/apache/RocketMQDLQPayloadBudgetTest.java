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

import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.PullStatus;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageAccessor;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.studio.cluster.broker.MqClientPool;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.dlq.DLQExportResultVO;
import org.apache.rocketmq.studio.instance.dlq.DLQMessageVO;
import org.apache.rocketmq.studio.instance.dlq.DLQResendResultVO;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RocketMQDLQPayloadBudgetTest {

    private static final String DLQ_TOPIC = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
    private static final int BODY_BUDGET = 10 * 1024 * 1024;
    private static final int HALF_BUDGET = BODY_BUDGET / 2;

    @Mock
    private RuntimeAdminClientResolver resolver;
    @Mock
    private DefaultMQPullConsumer consumer;
    @Mock
    private DefaultMQProducer producer;
    @Mock
    private AuditService auditService;

    private RocketMQDLQProvider provider;

    @BeforeEach
    void setUp() {
        when(resolver.executePullConsumer(eq("instance-a"), any())).thenAnswer(invocation -> {
            MqClientPool.ClientAction<DefaultMQPullConsumer, Object> action = invocation.getArgument(1);
            return action.apply(consumer);
        });
        provider = new RocketMQDLQProvider(resolver, auditService);
    }

    @ParameterizedTest
    @EnumSource(ReadOperation.class)
    void readRejectsAggregateBodyOverflowBeforeConvertingMessagesTest(ReadOperation operation) throws Exception {
        MessageExt first = spy(message("first", new byte[HALF_BUDGET]));
        MessageExt second = spy(message("second", new byte[HALF_BUDGET + 1]));
        stubMessages(List.of(first, second));

        assertReadTooLarge(operation);

        verify(first, never()).getProperties();
        verify(second, never()).getProperties();
        verifyNoInteractions(producer, auditService);
    }

    @Test
    void readCountsBodiesAcrossPullBatchesTest() throws Exception {
        MessageQueue queue = new MessageQueue(DLQ_TOPIC, "broker-a", 0);
        when(consumer.fetchSubscribeMessageQueues(DLQ_TOPIC)).thenReturn(Set.of(queue));
        stubWindow(queue, 3L);
        when(consumer.pull(queue, "*", 0L, 32)).thenReturn(found(1L,
                List.of(message("first", new byte[HALF_BUDGET]))));
        when(consumer.pull(queue, "*", 1L, 32)).thenReturn(found(2L,
                List.of(message("second", new byte[HALF_BUDGET + 1]))));

        assertReadTooLarge(ReadOperation.JSON);

        verify(consumer, never()).pull(queue, "*", 2L, 32);
    }

    @Test
    void readCountsBodiesAcrossQueuesAndDoesNotReturnPartialSuccessOnOverflowTest() throws Exception {
        MessageQueue first = new MessageQueue(DLQ_TOPIC, "broker-a", 0);
        MessageQueue unavailable = new MessageQueue(DLQ_TOPIC, "broker-b", 0);
        MessageQueue last = new MessageQueue(DLQ_TOPIC, "broker-c", 0);
        when(consumer.fetchSubscribeMessageQueues(DLQ_TOPIC))
                .thenReturn(new LinkedHashSet<>(List.of(first, unavailable, last)));
        stubWindow(first, 1L);
        when(consumer.searchOffset(unavailable, 100L)).thenThrow(new BusinessException(502, "Unavailable"));
        stubWindow(last, 1L);
        when(consumer.pull(first, "*", 0L, 32)).thenReturn(found(1L,
                List.of(message("first", new byte[HALF_BUDGET]))));
        when(consumer.pull(last, "*", 0L, 32)).thenReturn(found(1L,
                List.of(message("last", new byte[HALF_BUDGET + 1]))));

        assertReadTooLarge(ReadOperation.JSON);
    }

    @Test
    void exportPreservesCompleteBodiesAtExactBudgetTest() throws Exception {
        stubMessages(List.of(message("first", new byte[HALF_BUDGET]),
                message("second", new byte[HALF_BUDGET])));

        DLQExportResultVO result = provider.exportMessages("instance-a", "group-a", 100L, 200L, 1000);

        assertThat(result.isTruncated()).isFalse();
        assertThat(result.getFailedQueueCount()).isZero();
        assertThat(result.getMessages()).hasSize(2).allSatisfy(message -> {
            assertThat(message.getBody()).hasSize(HALF_BUDGET);
            assertThat(message.getBodyBase64()).hasSize(4 * ((HALF_BUDGET + 2) / 3));
        });
    }

    @Test
    void nullBodyDoesNotExceedAnExactlyExhaustedBudgetTest() throws Exception {
        stubMessages(List.of(message("first", new byte[HALF_BUDGET]),
                message("second", new byte[HALF_BUDGET]), message("empty", null)));

        PageResult<DLQMessageVO> result = provider.listMessages("instance-a", "group-a", 100L, 200L, 3, 1);

        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getItems()).singleElement().satisfies(message -> {
            assertThat(message.getMsgId()).isEqualTo("empty");
            assertThat(message.getBody()).isNull();
            assertThat(message.getBodyBase64()).isNull();
        });
    }

    @Test
    void listConvertsOnlyTheRequestedPageTest() throws Exception {
        MessageExt first = spy(message("first", new byte[] {1}));
        MessageExt second = spy(message("second", new byte[] {2}));
        MessageExt third = spy(message("third", new byte[] {3}));
        stubMessages(List.of(first, second, third));

        PageResult<DLQMessageVO> result = provider.listMessages("instance-a", "group-a", 100L, 200L, 2, 1);

        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(1);
        assertThat(result.getItems()).singleElement().satisfies(message -> {
            assertThat(message.getMsgId()).isEqualTo("second");
            assertThat(message.getBodyBase64()).isEqualTo("Ag==");
        });
        for (MessageExt outsidePage : List.of(first, third)) {
            verify(outsidePage).getBody(); // Budget accounting only, without UTF-8/Base64 conversion.
            verify(outsidePage, never()).getProperties();
            verify(outsidePage, never()).getMsgId();
        }
    }

    @Test
    void listBeyondTheLastPageDoesNotConvertMessagesTest() throws Exception {
        MessageExt message = spy(message("first", new byte[] {1}));
        stubMessages(List.of(message));

        PageResult<DLQMessageVO> result = provider.listMessages(
                "instance-a", "group-a", 100L, 200L, Integer.MAX_VALUE, 100);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getItems()).isEmpty();
        verify(message).getBody();
        verify(message, never()).getProperties();
    }

    @Test
    void readDoesNotChargeBodiesOutsideTheRequestedTimeWindowTest() throws Exception {
        MessageExt outsideWindow = message("outside", new byte[BODY_BUDGET + 1]);
        outsideWindow.setStoreTimestamp(99L);
        stubMessages(List.of(outsideWindow, message("inside", new byte[] {1})));

        DLQExportResultVO result = provider.exportMessages("instance-a", "group-a", 100L, 200L, 1000);

        assertThat(result.getMessages()).extracting(DLQMessageVO::getMsgId).containsExactly("inside");
        assertThat(result.isTruncated()).isFalse();
    }

    @Test
    void exportKeepsTheRequestedCountCapBeforeConsideringFurtherBodiesTest() throws Exception {
        stubMessages(List.of(message("first", new byte[] {1}),
                message("unneeded", new byte[BODY_BUDGET + 1])));

        DLQExportResultVO result = provider.exportMessages("instance-a", "group-a", 100L, 200L, 1);

        assertThat(result.getMessages()).extracting(DLQMessageVO::getMsgId).containsExactly("first");
        assertThat(result.getLimit()).isEqualTo(1);
        assertThat(result.isTruncated()).isTrue();
        assertThat(result.getFailedQueueCount()).isZero();
    }

    @Test
    void resendKeepsBodiesAboveTheReadBudgetUnchangedTest() throws Exception {
        MessageExt first = message("first", new byte[HALF_BUDGET]);
        MessageExt second = message("second", new byte[HALF_BUDGET + 1]);
        MessageAccessor.setProperties(first, Map.of(MessageConst.PROPERTY_RETRY_TOPIC, "orders"));
        MessageAccessor.setProperties(second, Map.of(MessageConst.PROPERTY_RETRY_TOPIC, "orders"));
        stubMessages(List.of(first, second));
        when(resolver.executeProducer(eq("instance-a"), any())).thenAnswer(invocation -> {
            MqClientPool.ClientAction<DefaultMQProducer, Object> action = invocation.getArgument(1);
            return action.apply(producer);
        });
        SendResult accepted = new SendResult();
        accepted.setSendStatus(SendStatus.SEND_OK);
        when(producer.send(any(Message.class))).thenReturn(accepted);

        DLQResendResultVO result = provider.resendMessages("instance-a", "group-a", 100L, 200L, null);

        assertThat(result.getMatched()).isEqualTo(2);
        assertThat(result.getResent()).isEqualTo(2);
        assertThat(result.getOutcome()).isEqualTo("SUCCESS");
        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(producer, times(2)).send(sent.capture());
        assertThat(sent.getAllValues().get(0).getBody()).isSameAs(first.getBody());
        assertThat(sent.getAllValues().get(1).getBody()).isSameAs(second.getBody());
    }

    private void assertReadTooLarge(ReadOperation operation) {
        assertThatThrownBy(() -> {
            switch (operation) {
                case LIST -> provider.listMessages("instance-a", "group-a", 100L, 200L, 1, 1);
                case JSON -> provider.exportMessages("instance-a", "group-a", 100L, 200L, 1000);
                case EXCEL -> provider.exportExcel("instance-a", "group-a", 100L, 200L, null);
                case SELECTED_EXCEL -> provider.exportExcel(
                        "instance-a", "group-a", 100L, 200L, List.of("first"));
            }
        }).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getCode()).isEqualTo(413);
            assertThat(exception.getMessage()).contains("10 MiB", "narrow the time range");
        });
    }

    private void stubMessages(List<MessageExt> messages) throws Exception {
        MessageQueue queue = new MessageQueue(DLQ_TOPIC, "broker-a", 0);
        when(consumer.fetchSubscribeMessageQueues(DLQ_TOPIC)).thenReturn(Set.of(queue));
        stubWindow(queue, messages.size());
        when(consumer.pull(queue, "*", 0L, 32)).thenReturn(found(messages.size(), messages));
    }

    private void stubWindow(MessageQueue queue, long endOffset) throws Exception {
        when(consumer.searchOffset(queue, 100L)).thenReturn(0L);
        when(consumer.searchOffset(queue, 201L)).thenReturn(endOffset);
    }

    private static PullResult found(long nextOffset, List<MessageExt> messages) {
        return new PullResult(PullStatus.FOUND, nextOffset, 0L, nextOffset, messages);
    }

    private static MessageExt message(String msgId, byte[] body) {
        MessageExt message = new MessageExt();
        message.setMsgId(msgId);
        message.setTopic(DLQ_TOPIC);
        message.setStoreTimestamp(150L);
        message.setBody(body);
        return message;
    }

    private enum ReadOperation {
        LIST, JSON, EXCEL, SELECTED_EXCEL
    }
}
