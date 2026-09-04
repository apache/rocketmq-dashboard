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
import org.apache.rocketmq.common.message.MessageDecoder;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.protocol.admin.TopicStatsTable;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.MqClientPool;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.dlq.DLQExportResultVO;
import org.apache.rocketmq.studio.instance.dlq.DLQGroupVO;
import org.apache.rocketmq.studio.instance.dlq.DLQMessageVO;
import org.apache.rocketmq.studio.instance.dlq.DLQResendResultVO;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RocketMQDLQProviderTest {

    @Mock
    private RuntimeAdminClientResolver runtimeAdminClientResolver;

    @Mock
    private AuditService auditService;

    @Mock
    private MQAdminExt adminExt;

    @Mock
    private DefaultMQPullConsumer pullConsumer;

    @Mock
    private DefaultMQProducer dlqProducer;

    private RocketMQDLQProvider provider;

    @BeforeEach
    void setUp() {
        lenient().when(runtimeAdminClientResolver.resolveEndpoint("instance-a")).thenReturn("namesrv-a:9876");
        lenient().when(runtimeAdminClientResolver.execute(anyString(), any())).thenAnswer(invocation -> {
            MqAdminExtFactory.AdminAction<Object> action = invocation.getArgument(1);
            return action.apply(adminExt);
        });
        lenient().when(runtimeAdminClientResolver.executePullConsumer(anyString(), any()))
                .thenAnswer(invocation -> {
                    MqClientPool.ClientAction<DefaultMQPullConsumer, Object> action =
                            invocation.getArgument(1);
                    return action.apply(pullConsumer);
                });
        lenient().when(runtimeAdminClientResolver.executeProducer(anyString(), any()))
                .thenAnswer(invocation -> {
                    MqClientPool.ClientAction<DefaultMQProducer, Object> action =
                            invocation.getArgument(1);
                    return action.apply(dlqProducer);
                });
        provider = new RocketMQDLQProvider(runtimeAdminClientResolver, auditService);
    }

    @Test
    void marksStatsUnavailableWhenTopicStatsCannotBeRead() throws Exception {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
        TopicList topicList = new TopicList();
        topicList.setTopicList(Set.of(dlqTopic));
        when(adminExt.fetchAllTopicList()).thenReturn(topicList);
        when(adminExt.examineTopicStats(dlqTopic)).thenThrow(new IllegalStateException("access denied"));

        List<DLQGroupVO> groups = provider.listDLQGroups("instance-a");

        assertThat(groups).singleElement().satisfies(group -> {
            assertThat(group.isStatsAvailable()).isFalse();
            assertThat(group.getStatus()).isEqualTo("UNAVAILABLE");
        });
    }

    @Test
    void keepsEmptyStatusWhenTopicStatsAreSuccessfullyRead() throws Exception {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
        TopicList topicList = new TopicList();
        topicList.setTopicList(Set.of(dlqTopic));
        when(adminExt.fetchAllTopicList()).thenReturn(topicList);
        when(adminExt.examineTopicStats(dlqTopic)).thenReturn(new TopicStatsTable());

        List<DLQGroupVO> groups = provider.listDLQGroups("instance-a");

        assertThat(groups).singleElement().satisfies(group -> {
            assertThat(group.isStatsAvailable()).isTrue();
            assertThat(group.getStatus()).isEqualTo("EMPTY");
        });
    }

    @Test
    void listDLQGroupsShouldPageAndFilterGroupsTest() throws Exception {
        TopicList topicList = new TopicList();
        topicList.setTopicList(Set.of(
                MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-c",
                MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a",
                MixAll.DLQ_GROUP_TOPIC_PREFIX + "order-b",
                "normal-topic"));
        when(adminExt.fetchAllTopicList()).thenReturn(topicList);
        when(adminExt.examineTopicStats(anyString())).thenReturn(new TopicStatsTable());

        PageResult<DLQGroupVO> firstPage = provider.listDLQGroups("instance-a", null, 1, 2);

        assertThat(firstPage.getTotal()).isEqualTo(3);
        assertThat(firstPage.getPage()).isEqualTo(1);
        assertThat(firstPage.getSize()).isEqualTo(2);
        assertThat(firstPage.getItems()).extracting(DLQGroupVO::getGroupName)
                .containsExactly("group-a", "group-c");

        PageResult<DLQGroupVO> secondPage = provider.listDLQGroups("instance-a", null, 2, 2);

        assertThat(secondPage.getItems()).extracting(DLQGroupVO::getGroupName)
                .containsExactly("order-b");

        PageResult<DLQGroupVO> filtered = provider.listDLQGroups("instance-a", "order", 1, 20);

        assertThat(filtered.getTotal()).isEqualTo(1);
        assertThat(filtered.getItems()).extracting(DLQGroupVO::getGroupName)
                .containsExactly("order-b");
    }

    @Test
    void listDLQGroupsShouldFilterCaseInsensitivelyTest() throws Exception {
        TopicList topicList = new TopicList();
        topicList.setTopicList(Set.of(
                MixAll.DLQ_GROUP_TOPIC_PREFIX + "Order-Consumer",
                MixAll.DLQ_GROUP_TOPIC_PREFIX + "payment-consumer"));
        when(adminExt.fetchAllTopicList()).thenReturn(topicList);
        when(adminExt.examineTopicStats(anyString())).thenReturn(new TopicStatsTable());

        PageResult<DLQGroupVO> filtered = provider.listDLQGroups("instance-a", "order", 1, 20);

        assertThat(filtered.getTotal()).isEqualTo(1);
        assertThat(filtered.getItems()).extracting(DLQGroupVO::getGroupName)
                .containsExactly("Order-Consumer");
    }


    @Test
    void resendMessagesShouldRejectInvertedTimeRangeBeforeCreatingConsumers() {
        assertThatThrownBy(() -> provider.resendMessages("instance-a", "group-a", 200L, 100L, "target-topic"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("DLQ resend start time must be before end time")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));

        verifyNoInteractions(runtimeAdminClientResolver);
        verify(auditService, never()).record(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void resendMessagesShouldRejectEqualStartAndEndTimeBeforeCreatingConsumers() {
        assertThatThrownBy(() -> provider.resendMessages("instance-a", "group-a", 100L, 100L, "target-topic"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("DLQ resend start time must be before end time")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));

        verifyNoInteractions(runtimeAdminClientResolver);
        verify(auditService, never()).record(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void resendMessagesShouldRejectBlankGroupNameBeforeResolvingEndpoint() {
        assertThatThrownBy(() -> provider.resendMessages("instance-a", " ", 100L, 200L, "target-topic"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("groupName is required for DLQ resend")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));

        verify(runtimeAdminClientResolver, never()).resolveEndpoint(anyString());
        verify(auditService, never()).record(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void resendMessagesShouldRejectNullGroupNameBeforeResolvingEndpoint() {
        assertThatThrownBy(() -> provider.resendMessages("instance-a", null, 100L, 200L, "target-topic"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("groupName is required for DLQ resend")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));

        verify(runtimeAdminClientResolver, never()).resolveEndpoint(anyString());
        verify(auditService, never()).record(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void resendMessagesRejectsRetryAndDlqTopicsAsTarget() throws Exception {
        assertThatThrownBy(() -> provider.resendMessages(
                "instance-a", "group-a", 100L, 200L, "%DLQ%group-a"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must not be a RocketMQ system, retry or DLQ topic")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));

        verify(runtimeAdminClientResolver, never()).executeProducer(anyString(), any());
        verify(adminExt, never()).fetchAllTopicList();
    }

    @Test
    void resendMessagesRejectsSystemTopicsAsTarget() {
        assertThatThrownBy(() -> provider.resendMessages(
                "instance-a", "group-a", 100L, 200L, "RMQ_SYS_TRACE_TOPIC"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must not be a RocketMQ system, retry or DLQ topic");

        verify(runtimeAdminClientResolver, never()).executeProducer(anyString(), any());
    }

    @Test
    void resendMessagesRejectsInvalidTargetTopicName() throws Exception {
        assertThatThrownBy(() -> provider.resendMessages(
                "instance-a", "group-a", 100L, 200L, "not a valid topic"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not a valid RocketMQ topic name");

        verify(runtimeAdminClientResolver, never()).executeProducer(anyString(), any());
        verify(adminExt, never()).fetchAllTopicList();
    }

    @Test
    void resendMessagesRejectsTargetTopicMissingFromInstance() throws Exception {
        TopicList otherTopics = new TopicList();
        otherTopics.setTopicList(Set.of("unrelated-topic"));
        when(adminExt.fetchAllTopicList()).thenReturn(otherTopics);

        assertThatThrownBy(() -> provider.resendMessages(
                "instance-a", "group-a", 100L, 200L, "target-topic"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("targetTopic does not exist on the selected instance");

        verify(runtimeAdminClientResolver, never()).executeProducer(anyString(), any());
        verify(pullConsumer, never()).pull(any(MessageQueue.class), anyString(), anyLong(), anyInt());
    }

    @Test
    void resendSelectedMessagesRejectsSystemTopicAsTarget() throws Exception {
        assertThatThrownBy(() -> provider.resendMessages(
                "instance-a", "group-a", List.of("msg-1"), "%DLQ%group-a"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must not be a RocketMQ system, retry or DLQ topic");

        verify(runtimeAdminClientResolver, never()).executeProducer(anyString(), any());
        verify(adminExt, never()).fetchAllTopicList();
        verify(pullConsumer, never()).pull(any(MessageQueue.class), anyString(), anyLong(), anyInt());
    }

    @Test
    void resendMessagesFailsGracefullyWhenTopicListCannotBeRead() throws Exception {
        when(adminExt.fetchAllTopicList()).thenThrow(new IllegalStateException("nameserver unreachable"));

        assertThatThrownBy(() -> provider.resendMessages(
                "instance-a", "group-a", 100L, 200L, "target-topic"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Failed to verify targetTopic on the selected instance")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(502));

        verify(runtimeAdminClientResolver, never()).executeProducer(anyString(), any());
        verify(pullConsumer, never()).pull(any(MessageQueue.class), anyString(), anyLong(), anyInt());
    }

    @Test
    void resendSelectedMessagesRejectsForgedMsgIdOutsideKnownTopology() throws Exception {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
        String forgedMsgId = MessageDecoder.createMessageId(new InetSocketAddress("10.2.3.4", 10911), 12345L);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfoWithBrokerAddresses("172.30.10.100:10911"));
        stubExistingTarget("target-topic");

        DLQResendResultVO result = provider.resendMessages(
                "instance-a", "group-a", List.of(forgedMsgId), "target-topic");

        assertThat(result.getMatched()).isZero();
        assertThat(result.getResent()).isZero();
        verify(adminExt, never()).viewMessage(anyString(), anyString());
        verify(runtimeAdminClientResolver, never()).executeProducer(anyString(), any());
    }

    @Test
    void resendSelectedMessagesResolvesInTopologyMsgIdNormally() throws Exception {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
        String msgId = MessageDecoder.createMessageId(new InetSocketAddress("172.30.10.100", 10911), 12345L);
        MessageExt deadLetter = new MessageExt();
        deadLetter.setMsgId(msgId);
        deadLetter.setTopic(dlqTopic);
        deadLetter.setBody(new byte[] {1, 2, 3});
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfoWithBrokerAddresses("172.30.10.100:10911"));
        when(adminExt.viewMessage(dlqTopic, msgId)).thenReturn(deadLetter);
        stubExistingTarget("target-topic");
        SendResult sendResult = new SendResult();
        sendResult.setSendStatus(SendStatus.SEND_OK);
        when(dlqProducer.send(any(Message.class))).thenReturn(sendResult);

        DLQResendResultVO result = provider.resendMessages(
                "instance-a", "group-a", List.of(msgId), "target-topic");

        assertThat(result.getMatched()).isEqualTo(1);
        assertThat(result.getResent()).isEqualTo(1);
        assertThat(result.getOutcome()).isEqualTo("SUCCESS");
        verify(adminExt).viewMessage(dlqTopic, msgId);
    }

    @Test
    void resendSelectedMessagesPassesNonOffsetIdsThroughToViewMessage() throws Exception {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
        when(adminExt.viewMessage(dlqTopic, "uniq-key-1"))
                .thenThrow(new IllegalStateException("unique key lookup handled by MQAdminImpl"));

        DLQResendResultVO result = provider.resendMessages(
                "instance-a", "group-a", List.of("uniq-key-1"), null);

        assertThat(result.getMatched()).isZero();
        verify(adminExt).viewMessage(dlqTopic, "uniq-key-1");
        verify(adminExt, never()).examineBrokerClusterInfo();
    }

    @Test
    void resendSelectedMessagesFailsClosedWhenTopologyCannotBeVerified() throws Exception {
        String msgId = MessageDecoder.createMessageId(new InetSocketAddress("172.30.10.100", 10911), 12345L);
        when(adminExt.examineBrokerClusterInfo()).thenThrow(new IllegalStateException("nameserver unreachable"));

        DLQResendResultVO result = provider.resendMessages(
                "instance-a", "group-a", List.of(msgId), null);

        assertThat(result.getMatched()).isZero();
        verify(adminExt, never()).viewMessage(anyString(), anyString());
        verify(runtimeAdminClientResolver, never()).executeProducer(anyString(), any());
    }

    @Test
    void resendMessagesShouldNormalizeGroupNameBeforeBuildingDlqTopicAndAuditing() throws Exception {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
        when(pullConsumer.fetchSubscribeMessageQueues(dlqTopic)).thenReturn(null);
        TopicList existingTargets = new TopicList();
        existingTargets.setTopicList(Set.of("target-topic"));
        when(adminExt.fetchAllTopicList()).thenReturn(existingTargets);
        provider.resendMessages("instance-a", " group-a ", 100L, 200L, "target-topic");

        verify(runtimeAdminClientResolver).executePullConsumer(eq("instance-a"), any());
        verify(pullConsumer).fetchSubscribeMessageQueues(dlqTopic);
        verify(runtimeAdminClientResolver, never()).executeProducer(anyString(), any());
        verify(auditService).record(eq("RESEND_DLQ"), eq("DLQ"), eq("group-a"), eq(null),
                contains("group=group-a, dlqTopic=%DLQ%group-a"), eq("NO_MESSAGES"));
    }

    @Test
    void resendMessagesDoesNotPullWhenDlqQueueSetIsNull() throws Exception {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
        when(pullConsumer.fetchSubscribeMessageQueues(dlqTopic)).thenReturn(null);
        TopicList existingTargets = new TopicList();
        existingTargets.setTopicList(Set.of("target-topic"));
        when(adminExt.fetchAllTopicList()).thenReturn(existingTargets);
        provider.resendMessages("instance-a", "group-a", 100L, 200L, "target-topic");

        verify(runtimeAdminClientResolver).executePullConsumer(eq("instance-a"), any());
        verify(pullConsumer).fetchSubscribeMessageQueues(dlqTopic);
        verify(pullConsumer, never()).pull(any(MessageQueue.class), anyString(), anyLong(), anyInt());
        verify(runtimeAdminClientResolver, never()).executeProducer(anyString(), any());
        verify(auditService).record(
                eq("RESEND_DLQ"),
                eq("DLQ"),
                eq("group-a"),
                isNull(),
                contains("matched=0, resent=0, failed=0"),
                eq("NO_MESSAGES"));
    }

    @Test
    void resendMessagesUsesPooledClientsForScanAndResend() throws Exception {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
        MessageQueue queue = new MessageQueue(dlqTopic, "broker-a", 0);
        MessageExt deadLetter = new MessageExt();
        deadLetter.setMsgId("acl-dlq-message");
        deadLetter.setTopic(dlqTopic);
        deadLetter.setBody(new byte[] {1});
        deadLetter.setStoreTimestamp(150L);
        PullResult pullResult = new PullResult(PullStatus.FOUND, 1L, 0L, 0L, List.of(deadLetter));
        SendResult sendResult = new SendResult();
        sendResult.setSendStatus(SendStatus.SEND_OK);
        when(pullConsumer.fetchSubscribeMessageQueues(dlqTopic)).thenReturn(Set.of(queue));
        when(pullConsumer.searchOffset(queue, 100L)).thenReturn(0L);
        when(pullConsumer.searchOffset(queue, 200L)).thenReturn(0L);
        when(pullConsumer.pull(queue, "*", 0L, 32)).thenReturn(pullResult);
        when(dlqProducer.send(any(Message.class))).thenReturn(sendResult);
        TopicList existingTargets = new TopicList();
        existingTargets.setTopicList(Set.of("target-topic"));
        when(adminExt.fetchAllTopicList()).thenReturn(existingTargets);
        provider.resendMessages("instance-a", "group-a", 100L, 200L, "target-topic");

        verify(runtimeAdminClientResolver).executePullConsumer(eq("instance-a"), any());
        verify(runtimeAdminClientResolver).executeProducer(eq("instance-a"), any());
    }

    @Test
    void resendMessagesRejectsAnAllFailedDlqScan() throws Exception {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
        when(pullConsumer.fetchSubscribeMessageQueues(dlqTopic))
                .thenThrow(new IllegalStateException("broker unavailable"));
        TopicList existingTargets = new TopicList();
        existingTargets.setTopicList(Set.of("target-topic"));
        when(adminExt.fetchAllTopicList()).thenReturn(existingTargets);
        assertThatThrownBy(() -> provider.resendMessages("instance-a", "group-a", 100L, 200L, "target-topic"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Failed to scan DLQ topic " + dlqTopic)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(502));

        verify(auditService).record(
                eq("RESEND_DLQ"),
                eq("DLQ"),
                eq("group-a"),
                isNull(),
                contains("scanFailedQueues=all"),
                eq("FAILED"));
    }

    @Test
    void resendMessagesMarksAResultPartialWhenOneDlqQueueCannotBeScanned() throws Exception {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
        MessageQueue unavailableQueue = new MessageQueue(dlqTopic, "broker-a", 0);
        MessageQueue emptyQueue = new MessageQueue(dlqTopic, "broker-b", 0);
        PullResult emptyResult = new PullResult(PullStatus.NO_NEW_MSG, 1L, 0L, 0L, List.of());
        when(pullConsumer.fetchSubscribeMessageQueues(dlqTopic))
                .thenReturn(Set.of(unavailableQueue, emptyQueue));
        when(pullConsumer.searchOffset(eq(unavailableQueue), anyLong()))
                .thenThrow(new IllegalStateException("broker unavailable"));
        when(pullConsumer.searchOffset(eq(emptyQueue), anyLong())).thenReturn(0L);
        when(pullConsumer.pull(eq(emptyQueue), eq("*"), eq(0L), eq(32))).thenReturn(emptyResult);
        TopicList existingTargets = new TopicList();
        existingTargets.setTopicList(Set.of("target-topic"));
        when(adminExt.fetchAllTopicList()).thenReturn(existingTargets);
        assertThat(provider.resendMessages("instance-a", "group-a", 100L, 200L, "target-topic"))
                .extracting("matched", "resent", "failed", "outcome", "scanIncomplete", "failedQueueCount")
                .containsExactly(0, 0, 0, "PARTIAL", true, 1);

        verify(runtimeAdminClientResolver, never()).executeProducer(anyString(), any());
        verify(auditService).record(
                eq("RESEND_DLQ"),
                eq("DLQ"),
                eq("group-a"),
                isNull(),
                contains("scanFailedQueues=1"),
                eq("PARTIAL"));
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void resendMessagesStopsWhenPullOffsetDoesNotAdvance() throws Exception {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
        MessageQueue queue = new MessageQueue(dlqTopic, "broker-a", 0);
        PullResult stalledResult = new PullResult(PullStatus.FOUND, 10, 0, 10, List.of());
        when(pullConsumer.fetchSubscribeMessageQueues(dlqTopic)).thenReturn(Set.of(queue));
        when(pullConsumer.searchOffset(eq(queue), anyLong())).thenReturn(10L);
        when(pullConsumer.pull(eq(queue), eq("*"), eq(10L), eq(32))).thenReturn(stalledResult);
        TopicList existingTargets = new TopicList();
        existingTargets.setTopicList(Set.of("target-topic"));
        when(adminExt.fetchAllTopicList()).thenReturn(existingTargets);
        provider.resendMessages("instance-a", "group-a", 100L, 200L, "target-topic");

        verify(pullConsumer, times(1)).pull(queue, "*", 10L, 32);
        verify(runtimeAdminClientResolver, never()).executeProducer(anyString(), any());
    }

    @Test
    void resendMessagesRetriesFromCorrectedOffsetAfterOffsetIllegal() throws Exception {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
        MessageQueue queue = new MessageQueue(dlqTopic, "broker-a", 0);
        MessageExt deadLetter = new MessageExt();
        deadLetter.setMsgId("dlq-after-correction");
        deadLetter.setTopic(dlqTopic);
        deadLetter.setBody(new byte[] {1, 2, 3});
        deadLetter.setStoreTimestamp(150L);
        PullResult illegalOffset = new PullResult(PullStatus.OFFSET_ILLEGAL, 20L, 0L, 30L, null);
        PullResult foundAfterCorrection = new PullResult(PullStatus.FOUND, 40L, 20L, 30L, List.of(deadLetter));
        PullResult endOfQueue = new PullResult(PullStatus.NO_NEW_MSG, 50L, 40L, 40L, List.of());
        SendResult sendResult = new SendResult();
        sendResult.setSendStatus(SendStatus.SEND_OK);

        when(pullConsumer.fetchSubscribeMessageQueues(dlqTopic)).thenReturn(Set.of(queue));
        when(pullConsumer.searchOffset(queue, 100L)).thenReturn(10L);
        when(pullConsumer.searchOffset(queue, 200L)).thenReturn(50L);
        when(pullConsumer.pull(queue, "*", 10L, 32)).thenReturn(illegalOffset);
        when(pullConsumer.pull(queue, "*", 20L, 32)).thenReturn(foundAfterCorrection);
        when(pullConsumer.pull(queue, "*", 40L, 32)).thenReturn(endOfQueue);
        when(dlqProducer.send(any(Message.class))).thenReturn(sendResult);
        TopicList existingTargets = new TopicList();
        existingTargets.setTopicList(Set.of("orders"));
        when(adminExt.fetchAllTopicList()).thenReturn(existingTargets);
        assertThat(provider.resendMessages("instance-a", "group-a", 100L, 200L, "orders"))
                .extracting("matched", "resent", "failed", "outcome")
                .containsExactly(1, 1, 0, "SUCCESS");

        DefaultMQPullConsumer consumer = pullConsumer;
        verify(consumer).pull(queue, "*", 10L, 32);
        verify(consumer).pull(queue, "*", 20L, 32);
        verify(consumer).pull(queue, "*", 40L, 32);
        verify(dlqProducer).send(any(Message.class));
        verify(auditService).record(
                eq("RESEND_DLQ"),
                eq("DLQ"),
                eq("group-a"),
                isNull(),
                contains("matched=1, resent=1, failed=0"),
                eq("SUCCESS"));
    }

    @Test
    void resendMessagesCountsNonSendOkResultsAsFailures() throws Exception {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
        MessageQueue queue = new MessageQueue(dlqTopic, "broker-a", 0);
        MessageExt deadLetter = new MessageExt();
        deadLetter.setMsgId("msg-1");
        deadLetter.setTopic(dlqTopic);
        deadLetter.setBody(new byte[] {1});
        deadLetter.setStoreTimestamp(150L);
        PullResult pullResult = new PullResult(PullStatus.FOUND, 1L, 0L, 0L, List.of(deadLetter));
        SendResult sendResult = new SendResult();
        sendResult.setSendStatus(SendStatus.FLUSH_DISK_TIMEOUT);

        when(pullConsumer.fetchSubscribeMessageQueues(dlqTopic)).thenReturn(Set.of(queue));
        when(pullConsumer.searchOffset(queue, 100L)).thenReturn(0L);
        when(pullConsumer.searchOffset(queue, 200L)).thenReturn(0L);
        when(pullConsumer.pull(queue, "*", 0L, 32)).thenReturn(pullResult);
        when(dlqProducer.send(any(Message.class))).thenReturn(sendResult);
        TopicList existingTargets = new TopicList();
        existingTargets.setTopicList(Set.of("target-topic"));
        when(adminExt.fetchAllTopicList()).thenReturn(existingTargets);
        assertThat(provider.resendMessages("instance-a", "group-a", 100L, 200L, "target-topic"))
                .extracting("matched", "resent", "failed", "outcome")
                .containsExactly(1, 0, 1, "FAILED");

        verify(runtimeAdminClientResolver).executePullConsumer(eq("instance-a"), any());
        verify(runtimeAdminClientResolver).executeProducer(eq("instance-a"), any());
        verify(dlqProducer).send(any(Message.class));
        verify(auditService).record(
                eq("RESEND_DLQ"),
                eq("DLQ"),
                eq("group-a"),
                isNull(),
                contains("matched=1, resent=0, failed=1"),
                eq("FAILED"));
    }

    @Test
    void resendMessagesShouldCopyUserPropertiesAndSkipSystemProperties() throws Exception {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
        MessageQueue queue = new MessageQueue(dlqTopic, "broker-a", 0);
        MessageExt deadLetter = new MessageExt();
        deadLetter.setMsgId("msg-with-properties");
        deadLetter.setTopic(dlqTopic);
        deadLetter.setBody(new byte[] {1});
        deadLetter.setStoreTimestamp(150L);
        deadLetter.putUserProperty("traceId", "trace-123");
        deadLetter.putUserProperty("tenantId", "tenant-a");
        deadLetter.getProperties().put(MessageConst.PROPERTY_REAL_TOPIC, "system-topic-should-not-copy");
        PullResult pullResult = new PullResult(PullStatus.FOUND, 1L, 0L, 0L, List.of(deadLetter));
        SendResult sendResult = new SendResult();
        sendResult.setSendStatus(SendStatus.SEND_OK);

        when(pullConsumer.fetchSubscribeMessageQueues(dlqTopic)).thenReturn(Set.of(queue));
        when(pullConsumer.searchOffset(queue, 100L)).thenReturn(0L);
        when(pullConsumer.searchOffset(queue, 200L)).thenReturn(0L);
        when(pullConsumer.pull(queue, "*", 0L, 32)).thenReturn(pullResult);
        when(dlqProducer.send(any(Message.class))).thenReturn(sendResult);
        TopicList existingTargets = new TopicList();
        existingTargets.setTopicList(Set.of("target-topic"));
        when(adminExt.fetchAllTopicList()).thenReturn(existingTargets);
        assertThat(provider.resendMessages("instance-a", "group-a", 100L, 200L, "target-topic"))
                .extracting("matched", "resent", "failed", "outcome")
                .containsExactly(1, 1, 0, "SUCCESS");

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(dlqProducer).send(messageCaptor.capture());
        Message resent = messageCaptor.getValue();
        assertThat(resent.getProperties()).containsEntry("traceId", "trace-123")
                .containsEntry("tenantId", "tenant-a")
                .containsEntry("studio_dlq_origin_message_id", "msg-with-properties")
                .containsEntry("studio_dlq_origin_topic", dlqTopic);
        assertThat(resent.getProperties())
                .doesNotContainEntry(MessageConst.PROPERTY_REAL_TOPIC, "system-topic-should-not-copy");
    }

    @Test
    void resendSelectedMessagesLooksUpIdsWithoutAStoreTimeWindow() throws Exception {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
        MessageExt oldDeadLetter = new MessageExt();
        oldDeadLetter.setMsgId("old-msg");
        oldDeadLetter.setTopic(dlqTopic);
        oldDeadLetter.setBody(new byte[] {1});
        oldDeadLetter.setStoreTimestamp(1L);
        SendResult sendResult = new SendResult();
        sendResult.setSendStatus(SendStatus.SEND_OK);

        when(adminExt.viewMessage(dlqTopic, "old-msg")).thenReturn(oldDeadLetter);
        when(dlqProducer.send(any(Message.class))).thenReturn(sendResult);
        TopicList existingTargets = new TopicList();
        existingTargets.setTopicList(Set.of("target-topic"));
        when(adminExt.fetchAllTopicList()).thenReturn(existingTargets);

        assertThat(provider.resendMessages(
                "instance-a", "group-a", List.of("old-msg"), "target-topic"))
                .extracting("matched", "resent", "failed", "outcome", "scanIncomplete")
                .containsExactly(1, 1, 0, "SUCCESS", false);

        verify(adminExt).viewMessage(dlqTopic, "old-msg");
        verifyNoInteractions(pullConsumer);
    }

    @Test
    void resendSelectedMessagesReportsMissingLookupsAsPartial() throws Exception {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
        MessageExt found = new MessageExt();
        found.setMsgId("found-msg");
        found.setTopic(dlqTopic);
        found.setBody(new byte[] {1});
        SendResult sendResult = new SendResult();
        sendResult.setSendStatus(SendStatus.SEND_OK);

        when(adminExt.viewMessage(dlqTopic, "missing-msg"))
                .thenThrow(new IllegalStateException("message not found"));
        when(adminExt.viewMessage(dlqTopic, "found-msg")).thenReturn(found);
        when(dlqProducer.send(any(Message.class))).thenReturn(sendResult);
        TopicList existingTargets = new TopicList();
        existingTargets.setTopicList(Set.of("target-topic"));
        when(adminExt.fetchAllTopicList()).thenReturn(existingTargets);

        assertThat(provider.resendMessages(
                "instance-a", "group-a", List.of("missing-msg", "found-msg"), "target-topic"))
                .extracting("matched", "resent", "failed", "outcome", "scanIncomplete")
                .containsExactly(1, 1, 0, "PARTIAL", true);

        verify(adminExt).viewMessage(dlqTopic, "missing-msg");
        verify(adminExt).viewMessage(dlqTopic, "found-msg");
    }

    @Test
    void resendMessagesMarksAResultPartialWhenScanReachesHardCap() throws Exception {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
        MessageQueue queue = new MessageQueue(dlqTopic, "broker-a", 0);
        List<MessageExt> deadLetters = IntStream.range(0, 5001)
                .mapToObj(index -> {
                    MessageExt deadLetter = new MessageExt();
                    deadLetter.setMsgId("msg-" + index);
                    deadLetter.setTopic(dlqTopic);
                    deadLetter.setBody(new byte[] {1});
                    deadLetter.setStoreTimestamp(150L);
                    return deadLetter;
                })
                .toList();
        PullResult pullResult = new PullResult(PullStatus.FOUND, 5001L, 0L, 5000L, deadLetters);
        SendResult sendResult = new SendResult();
        sendResult.setSendStatus(SendStatus.SEND_OK);

        when(pullConsumer.fetchSubscribeMessageQueues(dlqTopic)).thenReturn(Set.of(queue));
        when(pullConsumer.searchOffset(queue, 100L)).thenReturn(0L);
        when(pullConsumer.searchOffset(queue, 200L)).thenReturn(5001L);
        when(pullConsumer.pull(queue, "*", 0L, 32)).thenReturn(pullResult);
        when(dlqProducer.send(any(Message.class))).thenReturn(sendResult);
        TopicList existingTargets = new TopicList();
        existingTargets.setTopicList(Set.of("target-topic"));
        when(adminExt.fetchAllTopicList()).thenReturn(existingTargets);
        assertThat(provider.resendMessages("instance-a", "group-a", 100L, 200L, "target-topic"))
                .extracting("matched", "resent", "failed", "outcome", "scanIncomplete", "failedQueueCount")
                .containsExactly(5000, 5000, 0, "PARTIAL", true, 0);

        verify(dlqProducer, times(5000)).send(any(Message.class));
        verify(auditService).record(
                eq("RESEND_DLQ"),
                eq("DLQ"),
                eq("group-a"),
                eq(null),
                contains("scanTruncated=true"),
                eq("PARTIAL"));
    }

    @Test
    void exportMessagesReturnsMappedDeadLetterMessages() throws Exception {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
        MessageQueue queue = new MessageQueue(dlqTopic, "broker-a", 0);
        MessageExt deadLetter = new MessageExt();
        deadLetter.setMsgId("msg-1");
        deadLetter.setTopic(dlqTopic);
        deadLetter.setQueueId(0);
        deadLetter.setQueueOffset(5L);
        deadLetter.setStoreTimestamp(150L);
        deadLetter.setKeys("key-a,key-b");
        deadLetter.setBody("hello dlq".getBytes(StandardCharsets.UTF_8));
        PullResult pullResult = new PullResult(PullStatus.FOUND, 1L, 0L, 0L, List.of(deadLetter));
        when(pullConsumer.fetchSubscribeMessageQueues(dlqTopic)).thenReturn(Set.of(queue));
        when(pullConsumer.searchOffset(eq(queue), anyLong())).thenReturn(0L);
        when(pullConsumer.pull(eq(queue), eq("*"), eq(0L), eq(32))).thenReturn(pullResult);
        DLQExportResultVO exported =
                provider.exportMessages("instance-a", "group-a", 100L, 200L, 1000);

        assertThat(exported.isTruncated()).isFalse();
        assertThat(exported.getFailedQueueCount()).isZero();
        assertThat(exported.getLimit()).isEqualTo(1000);
        assertThat(exported.getMessages()).hasSize(1);
        DLQMessageVO vo = exported.getMessages().get(0);
        assertThat(vo.getMsgId()).isEqualTo("msg-1");
        assertThat(vo.getTopic()).isEqualTo(dlqTopic);
        assertThat(vo.getQueueId()).isEqualTo(0);
        assertThat(vo.getOffset()).isEqualTo(5L);
        assertThat(vo.getStoreTime()).isEqualTo(150L);
        assertThat(vo.getKeys()).isEqualTo("key-a,key-b");
        assertThat(vo.getBody()).isEqualTo("hello dlq");
        assertThat(vo.getBodyBase64())
                .isEqualTo(Base64.getEncoder().encodeToString("hello dlq".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void exportMessagesHonorsMaxCountCap() throws Exception {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
        MessageQueue queue = new MessageQueue(dlqTopic, "broker-a", 0);
        when(pullConsumer.fetchSubscribeMessageQueues(dlqTopic)).thenReturn(Set.of(queue));
        when(pullConsumer.searchOffset(eq(queue), anyLong())).thenReturn(0L);
        when(pullConsumer.pull(eq(queue), eq("*"), eq(0L), eq(32)))
                .thenReturn(new PullResult(PullStatus.NO_NEW_MSG, 1L, 0L, 0L, List.of()));
        // maxCount=0 falls back to the hard cap instead of failing; scan still completes.
        DLQExportResultVO exported =
                provider.exportMessages("instance-a", "group-a", 100L, 200L, 0);
        assertThat(exported.getMessages()).isEmpty();
        assertThat(exported.getLimit()).isEqualTo(5000);
    }

    private void stubExistingTarget(String topic) throws Exception {
        TopicList existingTargets = new TopicList();
        existingTargets.setTopicList(Set.of(topic));
        when(adminExt.fetchAllTopicList()).thenReturn(existingTargets);
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
}
