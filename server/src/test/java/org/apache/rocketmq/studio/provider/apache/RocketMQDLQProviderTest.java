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
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.protocol.admin.TopicStatsTable;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.dlq.DLQGroupVO;
import org.apache.rocketmq.studio.instance.dlq.DLQMessageVO;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
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

    private RocketMQDLQProvider provider;

    @BeforeEach
    void setUp() {
        lenient().when(runtimeAdminClientResolver.resolveEndpoint("instance-a")).thenReturn("namesrv-a:9876");
        lenient().when(runtimeAdminClientResolver.execute(anyString(), any())).thenAnswer(invocation -> {
            MqAdminExtFactory.AdminAction<Object> action = invocation.getArgument(1);
            return action.apply(adminExt);
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
    void resendMessagesShouldNormalizeGroupNameBeforeBuildingDlqTopicAndAuditing() throws Exception {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
        try (MockedConstruction<DefaultMQPullConsumer> mockedConsumers =
                     mockConstruction(DefaultMQPullConsumer.class, (consumer, context) -> {
                         doNothing().when(consumer).start();
                         when(consumer.fetchSubscribeMessageQueues(dlqTopic)).thenReturn(null);
                         doNothing().when(consumer).shutdown();
                     });
             MockedConstruction<DefaultMQProducer> mockedProducers =
                     mockConstruction(DefaultMQProducer.class)) {
            provider.resendMessages("instance-a", " group-a ", 100L, 200L, "target-topic");

            assertThat(mockedConsumers.constructed()).hasSize(1);
            verify(mockedConsumers.constructed().get(0)).fetchSubscribeMessageQueues(dlqTopic);
            assertThat(mockedProducers.constructed()).isEmpty();
        }
        verify(auditService).record(eq("RESEND_DLQ"), eq("group-a"),
                contains("group=group-a, dlqTopic=%DLQ%group-a"), eq("NO_MESSAGES"));
    }

    @Test
    void resendMessagesDoesNotPullWhenDlqQueueSetIsNull() throws Exception {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
        List<List<?>> consumerConstructorArguments = new ArrayList<>();
        try (MockedConstruction<DefaultMQPullConsumer> mockedConsumers =
                     mockConstruction(DefaultMQPullConsumer.class, (consumer, context) -> {
                         consumerConstructorArguments.add(context.arguments());
                         doNothing().when(consumer).start();
                         when(consumer.fetchSubscribeMessageQueues(dlqTopic)).thenReturn(null);
                         doNothing().when(consumer).shutdown();
                     });
             MockedConstruction<DefaultMQProducer> mockedProducers =
                     mockConstruction(DefaultMQProducer.class)) {
            provider.resendMessages("instance-a", "group-a", 100L, 200L, "target-topic");

            assertThat(mockedConsumers.constructed()).hasSize(1);
            assertThat(consumerConstructorArguments).singleElement();
            assertThat(consumerConstructorArguments.get(0)).hasSize(2);
            assertThat(consumerConstructorArguments.get(0).get(1)).isNull();
            DefaultMQPullConsumer consumer = mockedConsumers.constructed().get(0);
            verify(consumer).setNamesrvAddr("namesrv-a:9876");
            verify(consumer).start();
            verify(consumer).fetchSubscribeMessageQueues(dlqTopic);
            verify(consumer, never()).pull(any(MessageQueue.class), anyString(), anyLong(), anyInt());
            verify(consumer).shutdown();
            assertThat(mockedProducers.constructed()).isEmpty();
        }
        verify(auditService).record(
                eq("RESEND_DLQ"),
                eq("group-a"),
                contains("matched=0, resent=0, failed=0"),
                eq("NO_MESSAGES"));
        verify(runtimeAdminClientResolver).resolveEndpoint("instance-a");
        verify(runtimeAdminClientResolver).resolveCredentialHook("instance-a");
    }

    @Test
    void resendMessagesUsesSelectedInstanceCredentialHookForScanAndResend() throws Exception {
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
        RPCHook credentialHook = mock(RPCHook.class);
        List<List<?>> consumerConstructorArguments = new ArrayList<>();
        List<List<?>> producerConstructorArguments = new ArrayList<>();
        when(runtimeAdminClientResolver.resolveCredentialHook("instance-a")).thenReturn(credentialHook);

        try (MockedConstruction<DefaultMQPullConsumer> mockedConsumers =
                     mockConstruction(DefaultMQPullConsumer.class, (consumer, context) -> {
                         consumerConstructorArguments.add(context.arguments());
                         doNothing().when(consumer).start();
                         when(consumer.fetchSubscribeMessageQueues(dlqTopic)).thenReturn(Set.of(queue));
                         when(consumer.searchOffset(queue, 100L)).thenReturn(0L);
                         when(consumer.searchOffset(queue, 200L)).thenReturn(0L);
                         when(consumer.pull(queue, "*", 0L, 32)).thenReturn(pullResult);
                         doNothing().when(consumer).shutdown();
                     });
             MockedConstruction<DefaultMQProducer> mockedProducers =
                     mockConstruction(DefaultMQProducer.class, (producer, context) -> {
                         producerConstructorArguments.add(context.arguments());
                         doNothing().when(producer).start();
                         when(producer.send(any(Message.class))).thenReturn(sendResult);
                         doNothing().when(producer).shutdown();
                     })) {
            provider.resendMessages("instance-a", "group-a", 100L, 200L, "target-topic");

            assertThat(mockedConsumers.constructed()).singleElement();
            assertThat(mockedProducers.constructed()).singleElement();
        }

        assertThat(consumerConstructorArguments).singleElement();
        assertThat(consumerConstructorArguments.get(0).get(1)).isSameAs(credentialHook);
        assertThat(producerConstructorArguments).singleElement();
        assertThat(producerConstructorArguments.get(0).get(1)).isSameAs(credentialHook);
        verify(runtimeAdminClientResolver).resolveCredentialHook("instance-a");
    }

    @Test
    void resendMessagesRejectsAnAllFailedDlqScan() throws Exception {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
        try (MockedConstruction<DefaultMQPullConsumer> mockedConsumers =
                     mockConstruction(DefaultMQPullConsumer.class, (consumer, context) -> {
                         doThrow(new IllegalStateException("broker unavailable")).when(consumer).start();
                         doNothing().when(consumer).shutdown();
                     })) {
            assertThatThrownBy(() -> provider.resendMessages("instance-a", "group-a", 100L, 200L, "target-topic"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Failed to scan DLQ topic " + dlqTopic)
                    .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(502));

            verify(mockedConsumers.constructed().get(0)).shutdown();
        }
        verify(auditService).record(
                eq("RESEND_DLQ"),
                eq("group-a"),
                contains("scanFailedQueues=all"),
                eq("FAILED"));
    }

    @Test
    void resendMessagesMarksAResultPartialWhenOneDlqQueueCannotBeScanned() throws Exception {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
        MessageQueue unavailableQueue = new MessageQueue(dlqTopic, "broker-a", 0);
        MessageQueue emptyQueue = new MessageQueue(dlqTopic, "broker-b", 0);
        PullResult emptyResult = new PullResult(PullStatus.NO_NEW_MSG, 1L, 0L, 0L, List.of());
        try (MockedConstruction<DefaultMQPullConsumer> mockedConsumers =
                     mockConstruction(DefaultMQPullConsumer.class, (consumer, context) -> {
                         doNothing().when(consumer).start();
                         when(consumer.fetchSubscribeMessageQueues(dlqTopic))
                                 .thenReturn(Set.of(unavailableQueue, emptyQueue));
                         when(consumer.searchOffset(eq(unavailableQueue), anyLong()))
                                 .thenThrow(new IllegalStateException("broker unavailable"));
                         when(consumer.searchOffset(eq(emptyQueue), anyLong())).thenReturn(0L);
                         when(consumer.pull(eq(emptyQueue), eq("*"), eq(0L), eq(32))).thenReturn(emptyResult);
                         doNothing().when(consumer).shutdown();
                     });
             MockedConstruction<DefaultMQProducer> mockedProducers =
                     mockConstruction(DefaultMQProducer.class)) {
            assertThat(provider.resendMessages("instance-a", "group-a", 100L, 200L, "target-topic"))
                    .extracting("matched", "resent", "failed", "outcome", "scanIncomplete", "failedQueueCount")
                    .containsExactly(0, 0, 0, "PARTIAL", true, 1);

            assertThat(mockedProducers.constructed()).isEmpty();
        }
        verify(auditService).record(
                eq("RESEND_DLQ"),
                eq("group-a"),
                contains("scanFailedQueues=1"),
                eq("PARTIAL"));
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void resendMessagesStopsWhenPullOffsetDoesNotAdvance() throws Exception {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
        MessageQueue queue = new MessageQueue(dlqTopic, "broker-a", 0);
        PullResult stalledResult = new PullResult(PullStatus.FOUND, 10, 0, 10, List.of());
        try (MockedConstruction<DefaultMQPullConsumer> mockedConsumers =
                     mockConstruction(DefaultMQPullConsumer.class, (consumer, context) -> {
                         doNothing().when(consumer).start();
                         when(consumer.fetchSubscribeMessageQueues(dlqTopic)).thenReturn(Set.of(queue));
                         when(consumer.searchOffset(eq(queue), anyLong())).thenReturn(10L);
                         when(consumer.pull(eq(queue), eq("*"), eq(10L), eq(32))).thenReturn(stalledResult);
                         doNothing().when(consumer).shutdown();
                     });
             MockedConstruction<DefaultMQProducer> mockedProducers =
                     mockConstruction(DefaultMQProducer.class)) {
            provider.resendMessages("instance-a", "group-a", 100L, 200L, "target-topic");

            verify(mockedConsumers.constructed().get(0), times(1)).pull(queue, "*", 10L, 32);
            assertThat(mockedProducers.constructed()).isEmpty();
        }
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

        try (MockedConstruction<DefaultMQPullConsumer> mockedConsumers =
                     mockConstruction(DefaultMQPullConsumer.class, (consumer, context) -> {
                         doNothing().when(consumer).start();
                         when(consumer.fetchSubscribeMessageQueues(dlqTopic)).thenReturn(Set.of(queue));
                         when(consumer.searchOffset(queue, 100L)).thenReturn(10L);
                         when(consumer.searchOffset(queue, 200L)).thenReturn(50L);
                         when(consumer.pull(queue, "*", 10L, 32)).thenReturn(illegalOffset);
                         when(consumer.pull(queue, "*", 20L, 32)).thenReturn(foundAfterCorrection);
                         when(consumer.pull(queue, "*", 40L, 32)).thenReturn(endOfQueue);
                         doNothing().when(consumer).shutdown();
                     });
             MockedConstruction<DefaultMQProducer> mockedProducers =
                     mockConstruction(DefaultMQProducer.class, (producer, context) -> {
                         doNothing().when(producer).start();
                         when(producer.send(any(Message.class))).thenReturn(sendResult);
                         doNothing().when(producer).shutdown();
                     })) {
            assertThat(provider.resendMessages("instance-a", "group-a", 100L, 200L, "orders"))
                    .extracting("matched", "resent", "failed", "outcome")
                    .containsExactly(1, 1, 0, "SUCCESS");

            DefaultMQPullConsumer consumer = mockedConsumers.constructed().get(0);
            verify(consumer).pull(queue, "*", 10L, 32);
            verify(consumer).pull(queue, "*", 20L, 32);
            verify(consumer).pull(queue, "*", 40L, 32);
            verify(mockedProducers.constructed().get(0)).send(any(Message.class));
        }
        verify(auditService).record(
                eq("RESEND_DLQ"),
                eq("group-a"),
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

        try (MockedConstruction<DefaultMQPullConsumer> mockedConsumers =
                     mockConstruction(DefaultMQPullConsumer.class, (consumer, context) -> {
                         doNothing().when(consumer).start();
                         when(consumer.fetchSubscribeMessageQueues(dlqTopic)).thenReturn(Set.of(queue));
                         when(consumer.searchOffset(queue, 100L)).thenReturn(0L);
                         when(consumer.searchOffset(queue, 200L)).thenReturn(0L);
                         when(consumer.pull(queue, "*", 0L, 32)).thenReturn(pullResult);
                         doNothing().when(consumer).shutdown();
                     });
             MockedConstruction<DefaultMQProducer> mockedProducers =
                     mockConstruction(DefaultMQProducer.class, (producer, context) -> {
                         doNothing().when(producer).start();
                         when(producer.send(any(Message.class))).thenReturn(sendResult);
                         doNothing().when(producer).shutdown();
                     })) {
            assertThat(provider.resendMessages("instance-a", "group-a", 100L, 200L, "target-topic"))
                    .extracting("matched", "resent", "failed", "outcome")
                    .containsExactly(1, 0, 1, "FAILED");

            assertThat(mockedConsumers.constructed()).hasSize(1);
            assertThat(mockedProducers.constructed()).hasSize(1);
            verify(mockedProducers.constructed().get(0)).send(any(Message.class));
        }
        verify(auditService).record(
                eq("RESEND_DLQ"),
                eq("group-a"),
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

        try (MockedConstruction<DefaultMQPullConsumer> ignoredConsumers =
                     mockConstruction(DefaultMQPullConsumer.class, (consumer, context) -> {
                         doNothing().when(consumer).start();
                         when(consumer.fetchSubscribeMessageQueues(dlqTopic)).thenReturn(Set.of(queue));
                         when(consumer.searchOffset(queue, 100L)).thenReturn(0L);
                         when(consumer.searchOffset(queue, 200L)).thenReturn(0L);
                         when(consumer.pull(queue, "*", 0L, 32)).thenReturn(pullResult);
                         doNothing().when(consumer).shutdown();
                     });
             MockedConstruction<DefaultMQProducer> mockedProducers =
                     mockConstruction(DefaultMQProducer.class, (producer, context) -> {
                         doNothing().when(producer).start();
                         when(producer.send(any(Message.class))).thenReturn(sendResult);
                         doNothing().when(producer).shutdown();
                     })) {
            assertThat(provider.resendMessages("instance-a", "group-a", 100L, 200L, "target-topic"))
                    .extracting("matched", "resent", "failed", "outcome")
                    .containsExactly(1, 1, 0, "SUCCESS");

            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(mockedProducers.constructed().get(0)).send(messageCaptor.capture());
            Message resent = messageCaptor.getValue();
            assertThat(resent.getProperties()).containsEntry("traceId", "trace-123")
                    .containsEntry("tenantId", "tenant-a")
                    .containsEntry("studio_dlq_origin_message_id", "msg-with-properties")
                    .containsEntry("studio_dlq_origin_topic", dlqTopic);
            assertThat(resent.getProperties())
                    .doesNotContainEntry(MessageConst.PROPERTY_REAL_TOPIC, "system-topic-should-not-copy");
        }
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

        try (MockedConstruction<DefaultMQPullConsumer> mockedConsumers =
                     mockConstruction(DefaultMQPullConsumer.class, (consumer, context) -> {
                         doNothing().when(consumer).start();
                         when(consumer.fetchSubscribeMessageQueues(dlqTopic)).thenReturn(Set.of(queue));
                         when(consumer.searchOffset(queue, 100L)).thenReturn(0L);
                         when(consumer.searchOffset(queue, 200L)).thenReturn(5001L);
                         when(consumer.pull(queue, "*", 0L, 32)).thenReturn(pullResult);
                         doNothing().when(consumer).shutdown();
                     });
             MockedConstruction<DefaultMQProducer> mockedProducers =
                     mockConstruction(DefaultMQProducer.class, (producer, context) -> {
                         doNothing().when(producer).start();
                         when(producer.send(any(Message.class))).thenReturn(sendResult);
                         doNothing().when(producer).shutdown();
                     })) {
            assertThat(provider.resendMessages("instance-a", "group-a", 100L, 200L, "target-topic"))
                    .extracting("matched", "resent", "failed", "outcome", "scanIncomplete", "failedQueueCount")
                    .containsExactly(5000, 5000, 0, "PARTIAL", true, 0);

            verify(mockedProducers.constructed().get(0), times(5000)).send(any(Message.class));
        }
        verify(auditService).record(
                eq("RESEND_DLQ"),
                eq("group-a"),
                contains("scanTruncated=true"),
                eq("PARTIAL"));
    }

    @Test
    void createsUniqueProducerGroupsForConcurrentDlqResends() {
        String first = RocketMQDLQProvider.nextResendProducerGroup();
        String second = RocketMQDLQProvider.nextResendProducerGroup();

        assertThat(first).startsWith("studio-dlq-resend-");
        assertThat(second).startsWith("studio-dlq-resend-").isNotEqualTo(first);
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
        try (MockedConstruction<DefaultMQPullConsumer> mockedConsumers =
                     mockConstruction(DefaultMQPullConsumer.class, (consumer, context) -> {
                         doNothing().when(consumer).start();
                         when(consumer.fetchSubscribeMessageQueues(dlqTopic)).thenReturn(Set.of(queue));
                         when(consumer.searchOffset(eq(queue), anyLong())).thenReturn(0L);
                         when(consumer.pull(eq(queue), eq("*"), eq(0L), eq(32))).thenReturn(pullResult);
                         doNothing().when(consumer).shutdown();
                     })) {
            List<DLQMessageVO> exported =
                    provider.exportMessages("instance-a", "group-a", 100L, 200L, 1000);

            assertThat(exported).hasSize(1);
            DLQMessageVO vo = exported.get(0);
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
    }

    @Test
    void exportMessagesHonorsMaxCountCap() throws Exception {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
        MessageQueue queue = new MessageQueue(dlqTopic, "broker-a", 0);
        try (MockedConstruction<DefaultMQPullConsumer> mockedConsumers =
                     mockConstruction(DefaultMQPullConsumer.class, (consumer, context) -> {
                         doNothing().when(consumer).start();
                         when(consumer.fetchSubscribeMessageQueues(dlqTopic)).thenReturn(Set.of(queue));
                         when(consumer.searchOffset(eq(queue), anyLong())).thenReturn(0L);
                         when(consumer.pull(eq(queue), eq("*"), eq(0L), eq(32)))
                                 .thenReturn(new PullResult(PullStatus.NO_NEW_MSG, 1L, 0L, 0L, List.of()));
                         doNothing().when(consumer).shutdown();
                     })) {
            // maxCount=0 falls back to the hard cap instead of failing; scan still completes.
            List<DLQMessageVO> exported =
                    provider.exportMessages("instance-a", "group-a", 100L, 200L, 0);
            assertThat(exported).isEmpty();
        }
    }
}
