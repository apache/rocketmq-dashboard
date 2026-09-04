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
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.ConsumeMessageDirectlyResult;
import org.apache.rocketmq.remoting.protocol.body.CMResult;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.MqClientPool;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.domain.enums.DeliveryStatus;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.message.DirectConsumeMessageDTO;
import org.apache.rocketmq.studio.instance.message.TraceNodeVO;
import org.apache.rocketmq.studio.instance.message.TraceRecordVO;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExtImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RocketMQMessageProviderTest {

    private static final long TOPIC_TAIL_SCAN_BUDGET = 32L * 32;

    @Mock
    private DefaultMQAdminExt adminExt;

    @Mock
    private RuntimeAdminClientResolver runtimeAdminClientResolver;

    @Mock
    private DefaultMQPullConsumer pullConsumer;

    private RocketMQMessageProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(runtimeAdminClientResolver.resolveEndpoint("instance-a")).thenReturn("namesrv-a:9876");
        lenient().when(runtimeAdminClientResolver.execute(anyString(), any())).thenAnswer(invocation -> {
            MqAdminExtFactory.AdminAction<Object> action = invocation.getArgument(1);
            if (action == null) {
                return null;
            }
            try {
                return action.apply(adminExt);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw exception;
            }
        });
        lenient().when(adminExt.examineBrokerClusterInfo())
                .thenReturn(clusterInfoWithBrokerAddresses("172.30.10.100:10911"));
        lenient().when(runtimeAdminClientResolver.executePullConsumer(anyString(), any()))
                .thenAnswer(invocation -> {
                    MqClientPool.ClientAction<DefaultMQPullConsumer, Object> action =
                            invocation.getArgument(1);
                    return action.apply(pullConsumer);
                });
        provider = new RocketMQMessageProvider(runtimeAdminClientResolver);
    }

    @Test
    void queryByTopicReturnsEmptyListWhenQueueSetIsNull() throws Exception {
        when(pullConsumer.fetchSubscribeMessageQueues("TopicA")).thenReturn(null);

        List<MessageRecordVO> messages = provider.queryMessages("instance-a", "TopicA", null, null, null, 100L, 200L);

        assertThat(messages).isEmpty();
        verify(pullConsumer).fetchSubscribeMessageQueues("TopicA");
        verify(pullConsumer, never()).pull(any(MessageQueue.class), anyString(), anyLong(), anyInt());
        verify(runtimeAdminClientResolver).executePullConsumer(eq("instance-a"), any());
        verify(runtimeAdminClientResolver).execute(eq("instance-a"), any());
    }

    @Test
    void queryByTopicReturnsEmptyListWhenNoQueuesExist() throws Exception {
        when(pullConsumer.fetchSubscribeMessageQueues("TopicA")).thenReturn(Set.of());

        List<MessageRecordVO> messages = provider.queryMessages("instance-a", "TopicA", null, null, null, 100L, 200L);

        assertThat(messages).isEmpty();
        verify(runtimeAdminClientResolver).executePullConsumer(eq("instance-a"), any());
    }

    @Test
    void directlyConsumesMessageForTheExplicitGroupAndClientTest() throws Exception {
        ConsumeMessageDirectlyResult brokerResult = new ConsumeMessageDirectlyResult();
        brokerResult.setConsumeResult(CMResult.CR_SUCCESS);
        brokerResult.setRemark("consumed");
        brokerResult.setSpentTimeMills(12);
        when(adminExt.consumeMessageDirectly("billing", "client-a", "orders", "msg-1"))
                .thenReturn(brokerResult);
        DirectConsumeMessageDTO request = new DirectConsumeMessageDTO();
        request.setInstanceId("instance-a");
        request.setTopic("orders");
        request.setMsgId("msg-1");
        request.setConsumerGroup("billing");
        request.setClientId("client-a");

        org.apache.rocketmq.studio.instance.message.DirectConsumeMessageResultVO result =
                provider.consumeMessageDirectly(request);

        assertThat(result.getConsumeResult()).isEqualTo("CR_SUCCESS");
        assertThat(result.getRemark()).isEqualTo("consumed");
        assertThat(result.getSpentTimeMillis()).isEqualTo(12);
        verify(adminExt).consumeMessageDirectly("billing", "client-a", "orders", "msg-1");
    }

    @Test
    void queryMessagesShouldRejectInvertedTimeRangeBeforeAdminLookup() throws Exception {
        assertThatThrownBy(() -> provider.queryMessages(
                "instance-a", "TopicA", null, null, null, 200L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Message query start time must be before end time")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));

        verify(runtimeAdminClientResolver).execute(eq("instance-a"), any());
        verify(adminExt, never()).queryMessage(anyString(), anyString(), anyInt(), anyLong(), anyLong());
    }

    @Test
    void queryMessagesShouldRejectEqualTimeRangeBeforeAdminLookup() throws Exception {
        assertThatThrownBy(() -> provider.queryMessages(
                "instance-a", "TopicA", null, null, null, 100L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Message query start time must be before end time")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));

        verify(runtimeAdminClientResolver).execute(eq("instance-a"), any());
        verify(adminExt, never()).queryMessage(anyString(), anyString(), anyInt(), anyLong(), anyLong());
    }

    @Test
    void queryMessagesShouldRejectTopicRangesLongerThanSevenDays() throws Exception {
        assertThatThrownBy(() -> provider.queryMessages(
                "instance-a", "TopicA", null, null, null, 0L, 8L * 24 * 60 * 60 * 1000))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Topic message query time range must not exceed 7 days")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));
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
    void queryByMsgIdRejectsDecodedBrokerOutsideKnownTopology() throws Exception {
        String msgId = MessageDecoder.createMessageId(new InetSocketAddress("10.2.3.4", 10911), 12345L);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfoWithBrokerAddresses("172.30.10.100:10911"));
        when(adminExt.viewMessage("TopicA", msgId))
                .thenThrow(new IllegalStateException("primary lookup failed"));

        List<MessageRecordVO> result = provider.queryMessages(
                "instance-a", "TopicA", msgId, null, null, 100L, 200L);

        assertThat(result).isEmpty();
        verify(adminExt, never()).getDefaultMQAdminExtImpl();
    }

    @Test
    void queryByTopicSurfacesPullConsumerFailure() throws Exception {
        when(pullConsumer.fetchSubscribeMessageQueues("TopicA"))
                .thenThrow(new IllegalStateException("nameserver unavailable"));

        assertThatThrownBy(() -> provider.queryMessages(
                "instance-a", "TopicA", null, null, null, 100L, 200L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Failed to query messages by topic: nameserver unavailable")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(502));
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void queryByTopicStopsWhenPullOffsetDoesNotAdvance() throws Exception {
        MessageQueue queue = new MessageQueue("TopicA", "broker-a", 0);
        PullResult stalledResult = new PullResult(PullStatus.FOUND, 10, 0, 10, List.of());
        when(pullConsumer.fetchSubscribeMessageQueues("TopicA")).thenReturn(Set.of(queue));
        mockQueueWindow(pullConsumer, queue, 100L, 200L, 10L, 10L, 11L, 11L);
        when(pullConsumer.pull(eq(queue), eq("*"), eq(10L), eq(32))).thenReturn(stalledResult);

        List<MessageRecordVO> messages = provider.queryMessages(
                "instance-a", "TopicA", null, null, null, 100L, 200L);

        assertThat(messages).isEmpty();
        verify(pullConsumer, times(1)).pull(queue, "*", 10L, 32);
    }

    @Test
    void queryByTopicSortsMessagesAcrossQueuesNewestFirst() throws Exception {
        MessageQueue olderQueue = new MessageQueue("TopicA", "broker-a", 0);
        MessageQueue newerQueue = new MessageQueue("TopicA", "broker-a", 1);
        PullResult olderPullResult = new PullResult(PullStatus.FOUND, 11L, 10L, 10L,
                List.of(topicMessage("older", 150L)));
        PullResult newerPullResult = new PullResult(PullStatus.FOUND, 11L, 10L, 10L,
                List.of(topicMessage("newer", 250L)));
        when(pullConsumer.fetchSubscribeMessageQueues("TopicA"))
                .thenReturn(new LinkedHashSet<>(List.of(olderQueue, newerQueue)));
        mockQueueWindow(pullConsumer, olderQueue, 100L, 300L, 10L, 10L, 11L, 11L);
        mockQueueWindow(pullConsumer, newerQueue, 100L, 300L, 10L, 10L, 11L, 11L);
        when(pullConsumer.pull(olderQueue, "*", 10L, 32)).thenReturn(olderPullResult);
        when(pullConsumer.pull(newerQueue, "*", 10L, 32)).thenReturn(newerPullResult);

        List<MessageRecordVO> messages = provider.queryMessages(
                "instance-a", "TopicA", null, null, null, 100L, 300L);

        assertThat(messages).extracting(MessageRecordVO::getMsgId)
                .containsExactly("newer", "older");
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
        when(pullConsumer.fetchSubscribeMessageQueues("TopicA")).thenReturn(Set.of(queue));
        mockQueueWindow(pullConsumer, queue, 100L, 200L, 10L, 10L, 50L, 50L);
        when(pullConsumer.pull(eq(queue), eq("*"), eq(10L), eq(32))).thenReturn(illegalOffset);
        when(pullConsumer.pull(eq(queue), eq("*"), eq(20L), eq(32))).thenReturn(foundAfterCorrection);
        when(pullConsumer.pull(eq(queue), eq("*"), eq(40L), eq(32))).thenReturn(endOfQueue);

        List<MessageRecordVO> messages = provider.queryMessages(
                "instance-a", "TopicA", null, null, null, 100L, 200L);

        assertThat(messages).extracting(MessageRecordVO::getMsgId).containsExactly("msg-after-correction");
        verify(pullConsumer).pull(queue, "*", 10L, 32);
        verify(pullConsumer).pull(queue, "*", 20L, 32);
        verify(pullConsumer).pull(queue, "*", 40L, 32);
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
        when(pullConsumer.fetchSubscribeMessageQueues("TopicA"))
                .thenReturn(new LinkedHashSet<>(List.of(olderQueue, newerQueue)));
        mockQueueWindow(pullConsumer, olderQueue, 100L, 300L, 10L, 10L, 11L, 11L);
        mockQueueWindow(pullConsumer, newerQueue, 100L, 300L, 10L, 10L, 11L, 11L);
        when(pullConsumer.pull(olderQueue, "*", 10L, 32)).thenReturn(olderPullResult);
        when(pullConsumer.pull(newerQueue, "*", 10L, 32)).thenReturn(newerPullResult);

        List<MessageRecordVO> messages = provider.queryMessages(
                "instance-a", "TopicA", null, null, null, 100L, 300L);

        assertThat(messages).hasSize(200);
        assertThat(messages.get(0).getMsgId()).isEqualTo("newer");
        assertThat(messages).extracting(MessageRecordVO::getMsgId)
                .contains("newer");
    }

    @Test
    void queryByTopicUsesMessageIdAsStableTieBreakerForEqualTimestamps() throws Exception {
        MessageQueue firstQueue = new MessageQueue("TopicA", "broker-a", 0);
        MessageQueue secondQueue = new MessageQueue("TopicA", "broker-a", 1);
        PullResult firstPullResult = new PullResult(PullStatus.FOUND, 11L, 10L, 10L,
                List.of(topicMessage("message-a", 250L)));
        PullResult secondPullResult = new PullResult(PullStatus.FOUND, 11L, 10L, 10L,
                List.of(topicMessage("message-b", 250L)));
        when(pullConsumer.fetchSubscribeMessageQueues("TopicA"))
                .thenReturn(new LinkedHashSet<>(List.of(firstQueue, secondQueue)));
        mockQueueWindow(pullConsumer, firstQueue, 100L, 300L, 10L, 10L, 11L, 11L);
        mockQueueWindow(pullConsumer, secondQueue, 100L, 300L, 10L, 10L, 11L, 11L);
        when(pullConsumer.pull(firstQueue, "*", 10L, 32)).thenReturn(firstPullResult);
        when(pullConsumer.pull(secondQueue, "*", 10L, 32)).thenReturn(secondPullResult);

        List<MessageRecordVO> messages = provider.queryMessages(
                "instance-a", "TopicA", null, null, null, 100L, 300L);

        assertThat(messages).extracting(MessageRecordVO::getMsgId)
                .containsExactly("message-b", "message-a");
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

    }

    @Test
    void getMessageTraceQueriesCustomTraceTopicWhenProvided() throws Exception {
        String pub = traceContext("Pub", "1000", "cn", "prod-group", "TopicA", "msg-custom",
                "tag1", "key1", "broker:10911", "15", "50", "0", "offset-1", "true");
        MessageExt traceMessage = new MessageExt();
        traceMessage.setBody(traceBody(pub).getBytes(StandardCharsets.UTF_8));
        when(adminExt.queryMessage(anyString(), anyString(), anyInt(), anyLong(), anyLong()))
                .thenReturn(new QueryResult(0L, List.of(traceMessage)));

        TraceRecordVO record =
                provider.getMessageTrace("instance-a", "msg-custom", "orders", "MY_TRACE_TOPIC");

        assertThat(record.getNodes()).hasSize(1);
        assertThat(record.getNodes().get(0).getTitle()).isEqualTo("produce");
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(adminExt).queryMessage(topicCaptor.capture(), eq("msg-custom"), anyInt(), anyLong(), anyLong());
        assertThat(topicCaptor.getValue()).isEqualTo("MY_TRACE_TOPIC");
    }

    @Test
    void getMessageTraceByKeyParsesContextsOfDifferentMessagesSharingTheKey() throws Exception {
        // Two trace contexts belong to different message ids but share the same business key;
        // the key lookup must surface both instead of filtering on a single message id.
        String pubA = traceContext("Pub", "1000", "cn", "prod-group", "TopicA", "msg-a",
                "tag1", "shared-key", "broker:10911", "15", "50", "0", "offset-1", "true");
        String subB = traceContext("SubAfter", "req-b", "msg-b", "20", "true", "shared-key",
                "3", "3000", "cons-group");
        MessageExt traceMessage = new MessageExt();
        traceMessage.setBody(traceBody(pubA, subB).getBytes(StandardCharsets.UTF_8));
        when(adminExt.queryMessage(anyString(), anyString(), anyInt(), anyLong(), anyLong()))
                .thenReturn(new QueryResult(0L, List.of(traceMessage)));

        TraceRecordVO record =
                provider.getMessageTraceByKey("instance-a", "shared-key", "orders", "CUSTOM_TRACE");

        assertThat(record.getNodes()).hasSize(2);
        assertThat(record.getConsumerStatus()).hasSize(1);
        assertThat(record.getConsumerStatus().get(0).getGroup()).isEqualTo("cons-group");
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(adminExt).queryMessage(topicCaptor.capture(), keyCaptor.capture(), anyInt(), anyLong(), anyLong());
        assertThat(topicCaptor.getValue()).isEqualTo("CUSTOM_TRACE");
        assertThat(keyCaptor.getValue()).isEqualTo("shared-key");
    }

    @Test
    void getMessageTraceByKeyUsesDefaultTraceTopicWhenNotSpecified() throws Exception {
        when(adminExt.queryMessage(anyString(), anyString(), anyInt(), anyLong(), anyLong()))
                .thenReturn(new QueryResult(0L, List.of()));

        TraceRecordVO record = provider.getMessageTraceByKey("instance-a", "shared-key", null, null);

        assertThat(record.getNodes()).isEmpty();
        verify(adminExt).queryMessage(eq("RMQ_SYS_TRACE_TOPIC"), eq("shared-key"), anyInt(), anyLong(), anyLong());
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

    @Test

    void getMessageTraceDoesNotUseDecodedBrokerOutsideKnownTopology() throws Exception {
        String msgId = MessageDecoder.createMessageId(new InetSocketAddress("10.2.3.4", 10911), 12345L);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfoWithBrokerAddresses("172.30.10.100:10911"));
        when(adminExt.viewMessage("TopicA", msgId))
                .thenThrow(new IllegalStateException("topic lookup failed"));
        when(adminExt.queryMessage(anyString(), anyString(), anyInt(), anyLong(), anyLong()))
                .thenReturn(new QueryResult(0L, List.of()));

        provider.getMessageTrace("instance-a", msgId, "TopicA");

        verify(adminExt, never()).getDefaultMQAdminExtImpl();
        ArgumentCaptor<Long> beginCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Long> endCaptor = ArgumentCaptor.forClass(Long.class);
        verify(adminExt).queryMessage(eq("RMQ_SYS_TRACE_TOPIC"), eq(msgId), eq(64),
                beginCaptor.capture(), endCaptor.capture());
        assertThat(endCaptor.getValue() - beginCaptor.getValue()).isBetween(3_660_000L, 3_670_000L);
    }

    @Test
    void queryByTopicReturnsLatestMessagesWhenWindowExceedsLegacyPullLimit() throws Exception {
        MessageQueue queue = new MessageQueue("TopicA", "broker-a", 0);
        long begin = 10_000L;
        long end = 49_999L;
        long maxOffsetExclusive = 40_000L;
        when(pullConsumer.fetchSubscribeMessageQueues("TopicA")).thenReturn(Set.of(queue));
        mockQueueWindow(pullConsumer, queue, begin, end, 0L, 0L, maxOffsetExclusive, maxOffsetExclusive);
        when(pullConsumer.pull(eq(queue), eq("*"), anyLong(), eq(32)))
                .thenAnswer(invocation -> topicPullBatch(invocation.getArgument(2), maxOffsetExclusive, begin));

        List<MessageRecordVO> messages = provider.queryMessages(
                "instance-a", "TopicA", null, null, null, begin, end);

        assertThat(messages).hasSize(200);
        assertThat(messages.get(0).getMsgId()).isEqualTo("msg-39999");
        assertThat(messages.get(199).getMsgId()).isEqualTo("msg-39800");
        verify(pullConsumer).pull(queue, "*", maxOffsetExclusive - TOPIC_TAIL_SCAN_BUDGET, 32);
        verify(pullConsumer, never()).pull(queue, "*", 0L, 32);
    }

    @Test
    void queryByTopicKeepsPullOffsetsInsideTailBudgetWhenWindowIsLargerThanCap() throws Exception {
        MessageQueue queue = new MessageQueue("TopicA", "broker-a", 0);
        long begin = 20_000L;
        long end = 69_999L;
        long maxOffsetExclusive = 50_000L;
        List<Long> pulledOffsets = new ArrayList<>();
        when(pullConsumer.fetchSubscribeMessageQueues("TopicA")).thenReturn(Set.of(queue));
        mockQueueWindow(pullConsumer, queue, begin, end, 0L, 0L, maxOffsetExclusive, maxOffsetExclusive);
        when(pullConsumer.pull(eq(queue), eq("*"), anyLong(), eq(32)))
                .thenAnswer(invocation -> {
                    long offset = invocation.getArgument(2);
                    pulledOffsets.add(offset);
                    return topicPullBatch(offset, maxOffsetExclusive, begin);
                });

        List<MessageRecordVO> messages = provider.queryMessages(
                "instance-a", "TopicA", null, null, null, begin, end);

        assertThat(messages).hasSize(200);
        assertThat(pulledOffsets).isNotEmpty();
        long expectedFirstOffset = maxOffsetExclusive - TOPIC_TAIL_SCAN_BUDGET;
        assertThat(pulledOffsets.get(0)).isEqualTo(expectedFirstOffset);
        assertThat(pulledOffsets).allMatch(offset -> offset >= expectedFirstOffset);
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


    private static ClusterInfo clusterInfoWithBrokerAddresses(String... brokerAddresses) {
        ClusterInfo clusterInfo = new ClusterInfo();
        Map<String, BrokerData> brokerAddrTable = new HashMap<>();
        for (int index = 0; index < brokerAddresses.length; index++) {
            BrokerData brokerData = new BrokerData();
            brokerData.setBrokerName("broker-" + index);
            brokerData.setCluster("cluster-a");
            HashMap<Long, String> brokerAddrs = new HashMap<>();
            brokerAddrs.put(0L, brokerAddresses[index]);
            brokerData.setBrokerAddrs(brokerAddrs);
            brokerAddrTable.put(brokerData.getBrokerName(), brokerData);
        }
        clusterInfo.setBrokerAddrTable(brokerAddrTable);
        return clusterInfo;
    }

    private static void mockQueueWindow(DefaultMQPullConsumer consumer, MessageQueue queue, long begin, long end,
                                        long minOffset, long startOffset, long endOffsetExclusive,
                                        long maxOffsetExclusive) throws Exception {
        when(consumer.minOffset(queue)).thenReturn(minOffset);
        when(consumer.maxOffset(queue)).thenReturn(maxOffsetExclusive);
        when(consumer.searchOffset(queue, begin)).thenReturn(startOffset);
        when(consumer.searchOffset(queue, end + 1)).thenReturn(endOffsetExclusive);
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

    private static PullResult topicPullBatch(long offset, long endOffsetExclusive, long storeTimeBase) {
        long batchEnd = Math.min(offset + 32, endOffsetExclusive);
        List<MessageExt> messages = LongStream.range(offset, batchEnd)
                .mapToObj(index -> topicMessage("msg-" + index, storeTimeBase + index))
                .toList();
        return new PullResult(PullStatus.FOUND, batchEnd, 0L, endOffsetExclusive, messages);
    }

    @Test
    void normalizesTopicInputBeforeLookup() {
        assertThat(RocketMQMessageProvider.normalizeTopic(null)).isNull();
        assertThat(RocketMQMessageProvider.normalizeTopic("   ")).isNull();
        assertThat(RocketMQMessageProvider.normalizeTopic(" topic-a ")).isEqualTo("topic-a");
        assertThat(RocketMQMessageProvider.normalizeTopic("topic-a")).isEqualTo("topic-a");
    }
}
