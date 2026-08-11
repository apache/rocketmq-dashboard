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

import org.apache.rocketmq.client.QueryResult;
import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.PullStatus;
import org.apache.rocketmq.client.trace.TraceConstants;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.domain.enums.DeliveryStatus;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.message.TraceNodeVO;
import org.apache.rocketmq.studio.instance.message.TraceRecordVO;
import org.apache.rocketmq.studio.instance.message.QueryHistoryService;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RocketMQMessageProviderTest {

    @Mock
    private DefaultMQAdminExt adminExt;

    @Mock
    private RuntimeAdminClientResolver runtimeAdminClientResolver;

    @Mock
    private QueryHistoryService queryHistoryService;

    private RocketMQMessageProvider provider;

    @BeforeEach
    void setUp() {
        lenient().when(runtimeAdminClientResolver.resolveEndpoint("instance-a")).thenReturn("namesrv-a:9876");
        lenient().when(runtimeAdminClientResolver.execute(anyString(), any())).thenAnswer(invocation -> {
            MqAdminExtFactory.AdminAction<Object> action = invocation.getArgument(1);
            return action == null ? null : action.apply(adminExt);
        });
        provider = new RocketMQMessageProvider(runtimeAdminClientResolver, queryHistoryService);
    }

    @Test
    void queryByTopicReturnsEmptyListWhenQueueSetIsNull() throws Exception {
        try (MockedConstruction<DefaultMQPullConsumer> mockedConsumers =
                     mockConstruction(DefaultMQPullConsumer.class, (consumer, context) -> {
                         doNothing().when(consumer).start();
                         when(consumer.fetchSubscribeMessageQueues("TopicA")).thenReturn(null);
                         doNothing().when(consumer).shutdown();
                     })) {
            List<MessageRecordVO> messages = provider.queryMessages("instance-a", "TopicA", null, null, null, 100L, 200L);

            assertThat(messages).isEmpty();
            assertThat(mockedConsumers.constructed()).hasSize(1);
            DefaultMQPullConsumer consumer = mockedConsumers.constructed().get(0);
            verify(consumer).setNamesrvAddr("namesrv-a:9876");
            verify(consumer).start();
            verify(consumer).fetchSubscribeMessageQueues("TopicA");
            verify(consumer, never()).pull(any(MessageQueue.class), anyString(), anyLong(), anyInt());
            verify(consumer).shutdown();
        }
        verify(runtimeAdminClientResolver).resolveEndpoint("instance-a");
        verify(runtimeAdminClientResolver).execute(eq("instance-a"), any());
        verify(queryHistoryService).recordMessageQuery("instance-a", "TOPIC", "TopicA", null, null, null,
                100L, 200L, 0);
    }

    @Test
    void queryByKeySurfacesAdminFailure() throws Exception {
        when(adminExt.queryMessage("TopicA", "order-1", 64, 100L, 200L))
                .thenThrow(new IllegalStateException("broker unavailable"));

        assertThatThrownBy(() -> provider.queryMessages(
                "instance-a", "TopicA", null, null, "order-1", 100L, 200L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Failed to query messages by key: broker unavailable")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(502));
    }

    @Test
    void queryByTopicSurfacesPullConsumerFailure() throws Exception {
        try (MockedConstruction<DefaultMQPullConsumer> ignored =
                     mockConstruction(DefaultMQPullConsumer.class, (consumer, context) -> {
                         doThrow(new IllegalStateException("nameserver unavailable")).when(consumer).start();
                         doNothing().when(consumer).shutdown();
                     })) {
            assertThatThrownBy(() -> provider.queryMessages(
                    "instance-a", "TopicA", null, null, null, 100L, 200L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Failed to query messages by topic: nameserver unavailable")
                    .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(502));
        }
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void queryByTopicStopsWhenPullOffsetDoesNotAdvance() throws Exception {
        MessageQueue queue = new MessageQueue("TopicA", "broker-a", 0);
        PullResult stalledResult = new PullResult(PullStatus.FOUND, 10, 0, 10, List.of());
        try (MockedConstruction<DefaultMQPullConsumer> mockedConsumers =
                     mockConstruction(DefaultMQPullConsumer.class, (consumer, context) -> {
                         doNothing().when(consumer).start();
                         when(consumer.fetchSubscribeMessageQueues("TopicA")).thenReturn(Set.of(queue));
                         when(consumer.searchOffset(eq(queue), anyLong())).thenReturn(10L);
                         when(consumer.pull(eq(queue), eq("*"), eq(10L), eq(32))).thenReturn(stalledResult);
                         doNothing().when(consumer).shutdown();
                     })) {
            List<MessageRecordVO> messages = provider.queryMessages(
                    "instance-a", "TopicA", null, null, null, 100L, 200L);

            assertThat(messages).isEmpty();
            verify(mockedConsumers.constructed().get(0), times(1)).pull(queue, "*", 10L, 32);
        }
    }

    @Test
    void toRecordVOBoundsMessageBodyAndProperties() {
        MessageExt message = new MessageExt();
        message.setMsgId("msg-1");
        message.setTopic("TopicA");
        message.setBody("x".repeat(70 * 1024).getBytes(StandardCharsets.UTF_8));
        message.putUserProperty("large", "v".repeat(2 * 1024));
        for (int index = 0; index < 70; index++) {
            message.putUserProperty("property-" + index, "value");
        }

        MessageRecordVO record = provider.toRecordVO(message);

        assertThat(record.isBodyTruncated()).isTrue();
        assertThat(record.getBody()).hasSize(64 * 1024);
        assertThat(record.getBodyEncoding()).isEqualTo("UTF-8");
        assertThat(record.isPropertiesTruncated()).isTrue();
        assertThat(record.getProperties()).hasSize(64);
        assertThat(record.getProperties().get("large")).endsWith("...");
    }

    @Test
    void toRecordVOBase64EncodesBinaryPayloads() {
        MessageExt message = new MessageExt();
        message.setMsgId("msg-binary");
        message.setTopic("TopicA");
        message.setBody(new byte[] {(byte) 0xC3, (byte) 0x28});

        MessageRecordVO record = provider.toRecordVO(message);

        assertThat(record.getBodyEncoding()).isEqualTo("BASE64");
        assertThat(record.getBody()).isEqualTo("wyg=");
        assertThat(record.isBodyTruncated()).isFalse();
    }

    @Test
    void toRecordVODoesNotSplitUtf8CharacterAtBodyLimit() {
        MessageExt message = new MessageExt();
        message.setMsgId("msg-utf8");
        message.setTopic("TopicA");
        message.setBody(("x".repeat(64 * 1024 - 1) + "\u4E2Dsuffix").getBytes(StandardCharsets.UTF_8));

        MessageRecordVO record = provider.toRecordVO(message);

        assertThat(record.getBodyEncoding()).isEqualTo("UTF-8");
        assertThat(record.getBody()).isEqualTo("x".repeat(64 * 1024 - 1));
        assertThat(record.isBodyTruncated()).isTrue();
    }

    void getMessageTraceParsesBatchedPubAndSubAfterContexts() throws Exception {
        // Field order follows RocketMQ 5.5.0 TraceDataEncoder:
        // Pub = type, time, region, group,
        // topic, msgId, tags, keys, storeHost, bodyLength, costTime, msgType, offsetMsgId, isSuccess.
        // SubAfter = type, requestId, msgId, costTime, isSuccess, keys, contextCode, timeStamp,
        // groupName.
        String pub = traceContext("Pub", "1000", "cn", "prod-group", "TopicA", "msg-123",
                "tag1", "key1", "broker:10911", "15", "50", "0", "offset-1", "true");
        String subAfter = traceContext("SubAfter", "req-1", "msg-123", "20", "true", "key1",
                "3", "3000", "cons-group");
        String otherMessage = traceContext("SubAfter", "req-2", "other-msg", "5", "false",
                "key-other", "0", "0", "other-group");
        MessageExt traceMessage = new MessageExt();
        traceMessage.setBody(traceBody(pub, subAfter, otherMessage).getBytes(StandardCharsets.UTF_8));
        QueryResult queryResult = new QueryResult(0L, List.of(traceMessage));
        when(adminExt.queryMessage(anyString(), anyString(), anyInt(), anyLong(), anyLong()))
                .thenReturn(queryResult);

        TraceRecordVO record = provider.getMessageTrace("instance-a", "msg-123");

        assertThat(record.getNodes()).hasSize(2);
        verify(queryHistoryService).recordTraceQuery(eq("instance-a"), eq("msg-123"), eq(null), eq(2), eq(1));
        TraceNodeVO produce = record.getNodes().get(0);
        assertThat(produce.getTitle()).isEqualTo("produce");
        assertThat(produce.getStatus()).isEqualTo("finish");
        assertThat(produce.getCostTime()).isEqualTo(50);
        assertThat(produce.getTimestamp()).isEqualTo(1000);
        assertThat(produce.getDescription()).contains("prod-group").contains("broker:10911");
        TraceNodeVO consume = record.getNodes().get(1);
        assertThat(consume.getTitle()).isEqualTo("consume");
        assertThat(consume.getStatus()).isEqualTo("finish");
        assertThat(consume.getCostTime()).isEqualTo(20);
        assertThat(consume.getTimestamp()).isEqualTo(3000);
        assertThat(consume.getDescription()).contains("cons-group");
        assertThat(record.getConsumerStatus()).hasSize(1);
        assertThat(record.getConsumerStatus().get(0).getGroup()).isEqualTo("cons-group");
        assertThat(record.getConsumerStatus().get(0).getDeliveryStatus())
                .isEqualTo(DeliveryStatus.success);
    }

    @Test
    void getMessageTraceParsesEndTransactionState() throws Exception {
        String body = traceBody(traceContext("EndTransaction", "2000", "cn", "tx-group", "TopicA",
                "msg-tx", "tag2", "key2", "broker:10911", "0", "tx-1", "COMMIT_MESSAGE", "false"));
        MessageExt traceMessage = new MessageExt();
        traceMessage.setBody(body.getBytes(StandardCharsets.UTF_8));
        QueryResult queryResult = new QueryResult(0L, List.of(traceMessage));
        when(adminExt.queryMessage(anyString(), anyString(), anyInt(), anyLong(), anyLong()))
                .thenReturn(queryResult);

        TraceRecordVO record = provider.getMessageTrace("instance-a", "msg-tx");

        assertThat(record.getNodes()).hasSize(1);
        TraceNodeVO transaction = record.getNodes().get(0);
        assertThat(transaction.getTitle()).isEqualTo("endTransaction");
        assertThat(transaction.getDescription())
                .contains("tx-group")
                .contains("transactionState=COMMIT_MESSAGE")
                .doesNotContain("transactionState=false");
        assertThat(record.getConsumerStatus()).isEmpty();
    }

    @Test
    void getMessageTraceSurfacesAdminFailure() throws Exception {
        when(adminExt.queryMessage(anyString(), anyString(), anyInt(), anyLong(), anyLong()))
                .thenThrow(new IllegalStateException("broker unavailable"));

        assertThatThrownBy(() -> provider.getMessageTrace("instance-a", "msg-123"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Failed to query message trace: broker unavailable")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(502));

        verify(queryHistoryService, never()).recordTraceQuery(anyString(), anyString(), any(), anyInt(), anyInt());
    }

    private static String traceContext(String... fields) {
        return String.join(String.valueOf(TraceConstants.CONTENT_SPLITOR), fields);
    }

    private static String traceBody(String... contexts) {
        return String.join(String.valueOf(TraceConstants.FIELD_SPLITOR), contexts)
                + TraceConstants.FIELD_SPLITOR;
    }
}
