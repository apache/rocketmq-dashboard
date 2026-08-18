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
import org.apache.rocketmq.client.impl.MQClientAPIImpl;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.client.trace.TraceConstants;
import org.apache.rocketmq.common.message.MessageDecoder;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageId;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.domain.enums.DeliveryStatus;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.message.TraceNodeVO;
import org.apache.rocketmq.studio.instance.message.TraceRecordVO;
import org.apache.rocketmq.studio.instance.message.QueryHistoryService;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExtImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

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
import static org.mockito.Mockito.mock;
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
        List<List<?>> constructorArguments = new ArrayList<>();
        try (MockedConstruction<DefaultMQPullConsumer> mockedConsumers =
                     mockConstruction(DefaultMQPullConsumer.class, (consumer, context) -> {
                         constructorArguments.add(context.arguments());
                         doNothing().when(consumer).start();
                         when(consumer.fetchSubscribeMessageQueues("TopicA")).thenReturn(null);
                         doNothing().when(consumer).shutdown();
                     })) {
            List<MessageRecordVO> messages = provider.queryMessages("instance-a", "TopicA", null, null, null, 100L, 200L);

            assertThat(messages).isEmpty();
            assertThat(mockedConsumers.constructed()).hasSize(1);
            DefaultMQPullConsumer consumer = mockedConsumers.constructed().get(0);
            assertThat(constructorArguments).singleElement();
            assertThat(constructorArguments.get(0)).hasSize(2);
            assertThat(constructorArguments.get(0).get(0)).isEqualTo("studio-msg-query-group");
            assertThat(constructorArguments.get(0).get(1)).isNull();
            verify(consumer).setNamesrvAddr("namesrv-a:9876");
            verify(consumer).start();
            verify(consumer).fetchSubscribeMessageQueues("TopicA");
            verify(consumer, never()).pull(any(MessageQueue.class), anyString(), anyLong(), anyInt());
            verify(consumer).shutdown();
        }
        verify(runtimeAdminClientResolver).resolveEndpoint("instance-a");
        verify(runtimeAdminClientResolver).resolveCredentialHook("instance-a");
        verify(runtimeAdminClientResolver).execute(eq("instance-a"), any());
        verify(queryHistoryService).recordMessageQuery("instance-a", "TOPIC", "TopicA", null, null, null,
                100L, 200L, 0);
    }

    @Test
    void queryByTopicUsesSelectedInstanceCredentialHook() throws Exception {
        RPCHook credentialHook = mock(RPCHook.class);
        List<List<?>> constructorArguments = new ArrayList<>();
        when(runtimeAdminClientResolver.resolveCredentialHook("instance-a")).thenReturn(credentialHook);

        try (MockedConstruction<DefaultMQPullConsumer> mockedConsumers =
                     mockConstruction(DefaultMQPullConsumer.class, (consumer, context) -> {
                         constructorArguments.add(context.arguments());
                         doNothing().when(consumer).start();
                         when(consumer.fetchSubscribeMessageQueues("TopicA")).thenReturn(Set.of());
                         doNothing().when(consumer).shutdown();
                     })) {
            provider.queryMessages("instance-a", "TopicA", null, null, null, 100L, 200L);

            assertThat(mockedConsumers.constructed()).singleElement();
            assertThat(constructorArguments).singleElement();
            assertThat(constructorArguments.get(0)).hasSize(2);
            assertThat(constructorArguments.get(0).get(0)).isEqualTo("studio-msg-query-group");
            assertThat(constructorArguments.get(0).get(1)).isSameAs(credentialHook);
        }
        verify(runtimeAdminClientResolver).resolveCredentialHook("instance-a");
    }

    @Test
    void queryMessagesShouldRejectInvertedTimeRangeBeforeAdminLookup() throws Exception {
        assertThatThrownBy(() -> provider.queryMessages(
                "instance-a", "TopicA", null, null, null, 200L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Message query start time must be before end time")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));

        verify(runtimeAdminClientResolver).resolveEndpoint("instance-a");
        verify(runtimeAdminClientResolver).execute(eq("instance-a"), any());
        verify(adminExt, never()).queryMessage(anyString(), anyString(), anyInt(), anyLong(), anyLong());
        verify(queryHistoryService, never()).recordMessageQuery(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any(), any(), anyInt());
    }

    @Test
    void queryMessagesShouldRejectEqualTimeRangeBeforeAdminLookup() throws Exception {
        assertThatThrownBy(() -> provider.queryMessages(
                "instance-a", "TopicA", null, null, null, 100L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Message query start time must be before end time")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));

        verify(runtimeAdminClientResolver).resolveEndpoint("instance-a");
        verify(runtimeAdminClientResolver).execute(eq("instance-a"), any());
        verify(adminExt, never()).queryMessage(anyString(), anyString(), anyInt(), anyLong(), anyLong());
        verify(queryHistoryService, never()).recordMessageQuery(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any(), any(), anyInt());
    }

    @Test
    void queryMessagesShouldRejectTopicRangesLongerThanSevenDays() throws Exception {
        assertThatThrownBy(() -> provider.queryMessages(
                "instance-a", "TopicA", null, null, null, 0L, 8L * 24 * 60 * 60 * 1000))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Topic message query time range must not exceed 7 days")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));

        verify(queryHistoryService, never()).recordMessageQuery(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any(), any(), anyInt());
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
    void queryByMsgIdUsesDecodedPhysicalOffsetForFallback() throws Exception {
        String msgId = "AC1E0A6400002A9F0000000001A3F2B1";
        MQClientAPIImpl clientApi = mockOffsetLookupClient();
        MessageExt message = new MessageExt();
        message.setMsgId(msgId);
        message.setTopic("TopicA");
        when(adminExt.viewMessage("TopicA", msgId))
                .thenThrow(new IllegalStateException("primary lookup failed"));
        when(clientApi.viewMessage("172.30.10.100:10911", "TopicA", 27521713L, 3000L))
                .thenReturn(message);

        List<MessageRecordVO> result = provider.queryMessages(
                "instance-a", "TopicA", msgId, null, null, 100L, 200L);

        assertThat(result).singleElement().extracting(MessageRecordVO::getMsgId).isEqualTo(msgId);
        verify(clientApi).viewMessage("172.30.10.100:10911", "TopicA", 27521713L, 3000L);
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
    void queryByTopicSortsMessagesAcrossQueuesNewestFirst() throws Exception {
        MessageQueue olderQueue = new MessageQueue("TopicA", "broker-a", 0);
        MessageQueue newerQueue = new MessageQueue("TopicA", "broker-a", 1);
        PullResult olderPullResult = new PullResult(PullStatus.FOUND, 11L, 10L, 10L,
                List.of(topicMessage("older", 150L)));
        PullResult newerPullResult = new PullResult(PullStatus.FOUND, 11L, 10L, 10L,
                List.of(topicMessage("newer", 250L)));
        try (MockedConstruction<DefaultMQPullConsumer> ignored =
                     mockConstruction(DefaultMQPullConsumer.class, (consumer, context) -> {
                         doNothing().when(consumer).start();
                         when(consumer.fetchSubscribeMessageQueues("TopicA"))
                                 .thenReturn(new LinkedHashSet<>(List.of(olderQueue, newerQueue)));
                         when(consumer.searchOffset(any(MessageQueue.class), anyLong())).thenReturn(10L);
                         when(consumer.pull(olderQueue, "*", 10L, 32)).thenReturn(olderPullResult);
                         when(consumer.pull(newerQueue, "*", 10L, 32)).thenReturn(newerPullResult);
                         doNothing().when(consumer).shutdown();
                     })) {
            List<MessageRecordVO> messages = provider.queryMessages(
                    "instance-a", "TopicA", null, null, null, 100L, 300L);

            assertThat(messages).extracting(MessageRecordVO::getMsgId)
                    .containsExactly("newer", "older");
        }
    }

    @Test
    void queryByTopicRetriesFromCorrectedOffsetAfterOffsetIllegal() throws Exception {
        MessageQueue queue = new MessageQueue("TopicA", "broker-a", 0);
        MessageExt message = new MessageExt();
        message.setMsgId("msg-after-correction");
        message.setTopic("TopicA");
        message.setBody("payload".getBytes(StandardCharsets.UTF_8));
        message.setStoreTimestamp(150L);
        PullResult illegalOffset = new PullResult(PullStatus.OFFSET_ILLEGAL, 20L, 0L, 30L, null);
        PullResult foundAfterCorrection = new PullResult(PullStatus.FOUND, 40L, 20L, 30L, List.of(message));
        PullResult endOfQueue = new PullResult(PullStatus.NO_NEW_MSG, 50L, 40L, 40L, List.of());
        try (MockedConstruction<DefaultMQPullConsumer> mockedConsumers =
                     mockConstruction(DefaultMQPullConsumer.class, (consumer, context) -> {
                         doNothing().when(consumer).start();
                         when(consumer.fetchSubscribeMessageQueues("TopicA")).thenReturn(Set.of(queue));
                         when(consumer.searchOffset(queue, 100L)).thenReturn(10L);
                         when(consumer.searchOffset(queue, 200L)).thenReturn(50L);
                         when(consumer.pull(eq(queue), eq("*"), eq(10L), eq(32))).thenReturn(illegalOffset);
                         when(consumer.pull(eq(queue), eq("*"), eq(20L), eq(32))).thenReturn(foundAfterCorrection);
                         when(consumer.pull(eq(queue), eq("*"), eq(40L), eq(32))).thenReturn(endOfQueue);
                         doNothing().when(consumer).shutdown();
                     })) {
            List<MessageRecordVO> messages = provider.queryMessages(
                    "instance-a", "TopicA", null, null, null, 100L, 200L);

            assertThat(messages).extracting(MessageRecordVO::getMsgId).containsExactly("msg-after-correction");
            DefaultMQPullConsumer consumer = mockedConsumers.constructed().get(0);
            verify(consumer).pull(queue, "*", 10L, 32);
            verify(consumer).pull(queue, "*", 20L, 32);
            verify(consumer).pull(queue, "*", 40L, 32);
        }
        verify(queryHistoryService).recordMessageQuery("instance-a", "TOPIC", "TopicA", null, null, null,
                100L, 200L, 1);
    }

    @Test
    void queryByTopicKeepsNewestMessagesWhenEarlierQueuesFillTheDefaultLimit() throws Exception {
        MessageQueue olderQueue = new MessageQueue("TopicA", "broker-a", 0);
        MessageQueue newerQueue = new MessageQueue("TopicA", "broker-a", 1);
        PullResult olderPullResult = new PullResult(PullStatus.FOUND, 11L, 10L, 10L,
                IntStream.range(0, 200)
                        .mapToObj(index -> topicMessage("older-" + index, 150L))
                        .toList());
        PullResult newerPullResult = new PullResult(PullStatus.FOUND, 11L, 10L, 10L,
                List.of(topicMessage("newer", 250L)));
        try (MockedConstruction<DefaultMQPullConsumer> ignored =
                     mockConstruction(DefaultMQPullConsumer.class, (consumer, context) -> {
                         doNothing().when(consumer).start();
                         when(consumer.fetchSubscribeMessageQueues("TopicA"))
                                 .thenReturn(new LinkedHashSet<>(List.of(olderQueue, newerQueue)));
                         when(consumer.searchOffset(any(MessageQueue.class), anyLong())).thenReturn(10L);
                         when(consumer.pull(olderQueue, "*", 10L, 32)).thenReturn(olderPullResult);
                         when(consumer.pull(newerQueue, "*", 10L, 32)).thenReturn(newerPullResult);
                         doNothing().when(consumer).shutdown();
                     })) {
            List<MessageRecordVO> messages = provider.queryMessages(
                    "instance-a", "TopicA", null, null, null, 100L, 300L);

            assertThat(messages).hasSize(200);
            assertThat(messages.get(0).getMsgId()).isEqualTo("newer");
            assertThat(messages).extracting(MessageRecordVO::getMsgId)
                    .contains("newer");
        }
    }

    @Test
    void queryByTopicUsesMessageIdAsStableTieBreakerForEqualTimestamps() throws Exception {
        MessageQueue firstQueue = new MessageQueue("TopicA", "broker-a", 0);
        MessageQueue secondQueue = new MessageQueue("TopicA", "broker-a", 1);
        PullResult firstPullResult = new PullResult(PullStatus.FOUND, 11L, 10L, 10L,
                List.of(topicMessage("message-a", 250L)));
        PullResult secondPullResult = new PullResult(PullStatus.FOUND, 11L, 10L, 10L,
                List.of(topicMessage("message-b", 250L)));
        try (MockedConstruction<DefaultMQPullConsumer> ignored =
                     mockConstruction(DefaultMQPullConsumer.class, (consumer, context) -> {
                         doNothing().when(consumer).start();
                         when(consumer.fetchSubscribeMessageQueues("TopicA"))
                                 .thenReturn(new LinkedHashSet<>(List.of(firstQueue, secondQueue)));
                         when(consumer.searchOffset(any(MessageQueue.class), anyLong())).thenReturn(10L);
                         when(consumer.pull(firstQueue, "*", 10L, 32)).thenReturn(firstPullResult);
                         when(consumer.pull(secondQueue, "*", 10L, 32)).thenReturn(secondPullResult);
                         doNothing().when(consumer).shutdown();
                     })) {
            List<MessageRecordVO> messages = provider.queryMessages(
                    "instance-a", "TopicA", null, null, null, 100L, 300L);

            assertThat(messages).extracting(MessageRecordVO::getMsgId)
                    .containsExactly("message-b", "message-a");
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

        TraceRecordVO record = provider.getMessageTrace("instance-a", "msg-123", "orders");

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

        TraceRecordVO record = provider.getMessageTrace("instance-a", "msg-tx", "orders");

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
    void getMessageTraceShouldDeriveWindowFromTopicMessageStoreTimestamp() throws Exception {
        MessageExt original = new MessageExt();
        original.setMsgId("msg-with-topic");
        original.setStoreTimestamp(10_000_000L);
        when(adminExt.viewMessage("orders", "msg-with-topic")).thenReturn(original);
        when(adminExt.queryMessage(anyString(), anyString(), anyInt(), anyLong(), anyLong()))
                .thenReturn(new QueryResult(0L, List.of()));

        provider.getMessageTrace("instance-a", "msg-with-topic", "orders");

        ArgumentCaptor<Long> beginCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> endCaptor = ArgumentCaptor.forClass(Long.class);
        verify(adminExt).queryMessage(eq("RMQ_SYS_TRACE_TOPIC"), eq("msg-with-topic"), eq(64),
                beginCaptor.capture(), endCaptor.capture());
        assertThat(beginCaptor.getValue()).isEqualTo(10_000_000L - 5 * 60_000L);
        assertThat(endCaptor.getValue()).isGreaterThanOrEqualTo(10_000_000L + 24 * 3600_000L);
        verify(queryHistoryService).recordTraceQuery(eq("instance-a"), eq("msg-with-topic"), eq(null), eq(0), eq(0));
    }

    @Test
    void getMessageTraceShouldUseFallbackOneHourWindowWhenMessageTimestampCannotBeResolved() throws Exception {
        when(adminExt.queryMessage(anyString(), anyString(), anyInt(), anyLong(), anyLong()))
                .thenReturn(new QueryResult(0L, List.of()));

        provider.getMessageTrace("instance-a", "invalid-offset-id", "orders");

        ArgumentCaptor<Long> beginCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> endCaptor = ArgumentCaptor.forClass(Long.class);
        verify(adminExt).queryMessage(eq("RMQ_SYS_TRACE_TOPIC"), eq("invalid-offset-id"), eq(64),
                beginCaptor.capture(), endCaptor.capture());
        assertThat(endCaptor.getValue() - beginCaptor.getValue()).isBetween(3_660_000L, 3_670_000L);
        verify(queryHistoryService).recordTraceQuery(eq("instance-a"), eq("invalid-offset-id"), eq(null), eq(0), eq(0));
    }

    @Test
    void offsetMessageIdFixtureShouldDecodeToBrokerAddressForTraceWindowTests() throws Exception {
        String offsetMsgId = MessageDecoder.createMessageId(
                new InetSocketAddress("127.0.0.1", 10911), 12345L);

        MessageId decoded = MessageDecoder.decodeMessageId(offsetMsgId);

        assertThat(decoded.getAddress()).isInstanceOf(InetSocketAddress.class);
        InetSocketAddress address = (InetSocketAddress) decoded.getAddress();
        assertThat(address.getHostString()).isEqualTo("127.0.0.1");
        assertThat(address.getPort()).isEqualTo(10911);
    }

    @Test
    void getMessageTraceParsesRecallState() throws Exception {
        String body = traceBody(traceContext("Recall", "2500", "cn", "producer-group", "TopicA",
                "msg-recall", "false"));
        MessageExt traceMessage = new MessageExt();
        traceMessage.setBody(body.getBytes(StandardCharsets.UTF_8));
        QueryResult queryResult = new QueryResult(0L, List.of(traceMessage));
        when(adminExt.queryMessage(anyString(), anyString(), anyInt(), anyLong(), anyLong()))
                .thenReturn(queryResult);

        TraceRecordVO record = provider.getMessageTrace("instance-a", "msg-recall", "TopicA");

        assertThat(record.getNodes()).hasSize(1);
        TraceNodeVO recall = record.getNodes().get(0);
        assertThat(recall.getTitle()).isEqualTo("recall");
        assertThat(recall.getTimestamp()).isEqualTo(2500L);
        assertThat(recall.getStatus()).isEqualTo("failed");
        assertThat(recall.getCostTime()).isZero();
        assertThat(recall.getDescription()).contains("producer-group").contains("TopicA");
        assertThat(record.getConsumerStatus()).isEmpty();
    }

    @Test
    void getMessageTraceSurfacesAdminFailure() throws Exception {
        when(adminExt.queryMessage(anyString(), anyString(), anyInt(), anyLong(), anyLong()))
                .thenThrow(new IllegalStateException("broker unavailable"));

        assertThatThrownBy(() -> provider.getMessageTrace("instance-a", "msg-123", "orders"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Failed to query message trace: broker unavailable")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(502));

        verify(queryHistoryService, never()).recordTraceQuery(anyString(), anyString(), any(), anyInt(), anyInt());
    }

    @Test
    void getMessageTraceUsesDecodedOffsetToResolveQueryWindow() throws Exception {
        String msgId = "AC1E0A6400002A9F0000000001A3F2B1";
        long storeTimestamp = System.currentTimeMillis() - 2 * 60 * 60 * 1000L;
        MQClientAPIImpl clientApi = mockOffsetLookupClient();
        MessageExt message = new MessageExt();
        message.setMsgId(msgId);
        message.setTopic("TopicA");
        message.setStoreTimestamp(storeTimestamp);
        when(clientApi.viewMessage("172.30.10.100:10911", "TopicA", 27521713L, 3000L))
                .thenReturn(message);
        when(adminExt.queryMessage(
                "RMQ_SYS_TRACE_TOPIC", msgId, 64, storeTimestamp - 5 * 60_000L,
                storeTimestamp + 24 * 60 * 60 * 1000L))
                .thenReturn(new QueryResult(0L, List.of()));

        TraceRecordVO result = provider.getMessageTrace("instance-a", msgId, "TopicA");

        assertThat(result.getNodes()).isEmpty();
        assertThat(result.getConsumerStatus()).isEmpty();
        verify(clientApi).viewMessage("172.30.10.100:10911", "TopicA", 27521713L, 3000L);
        verify(adminExt).queryMessage(
                "RMQ_SYS_TRACE_TOPIC", msgId, 64, storeTimestamp - 5 * 60_000L,
                storeTimestamp + 24 * 60 * 60 * 1000L);
    }

    private MQClientAPIImpl mockOffsetLookupClient() {
        DefaultMQAdminExtImpl adminExtImpl = mock(DefaultMQAdminExtImpl.class);
        MQClientInstance clientInstance = mock(MQClientInstance.class);
        MQClientAPIImpl clientApi = mock(MQClientAPIImpl.class);
        when(adminExt.getDefaultMQAdminExtImpl()).thenReturn(adminExtImpl);
        when(adminExtImpl.getMqClientInstance()).thenReturn(clientInstance);
        when(clientInstance.getMQClientAPIImpl()).thenReturn(clientApi);
        return clientApi;
    }

    private static String traceContext(String... fields) {
        return String.join(String.valueOf(TraceConstants.CONTENT_SPLITOR), fields);
    }

    private static String traceBody(String... contexts) {
        return String.join(String.valueOf(TraceConstants.FIELD_SPLITOR), contexts)
                + TraceConstants.FIELD_SPLITOR;
    }

    private static MessageExt topicMessage(String msgId, long storeTimestamp) {
        MessageExt message = new MessageExt();
        message.setMsgId(msgId);
        message.setTopic("TopicA");
        message.setStoreTimestamp(storeTimestamp);
        message.setBody(("body-" + msgId).getBytes(StandardCharsets.UTF_8));
        return message;
    }
}
