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
package org.apache.rocketmq.studio.rocketmq;

import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.PullStatus;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RocketMQDLQProviderTest {

    @Mock
    private RuntimeAdminClientResolver runtimeAdminClientResolver;

    @Mock
    private AuditService auditService;

    private RocketMQDLQProvider provider;

    @BeforeEach
    void setUp() {
        lenient().when(runtimeAdminClientResolver.resolveEndpoint("instance-a")).thenReturn("namesrv-a:9876");
        provider = new RocketMQDLQProvider(runtimeAdminClientResolver, auditService);
    }

    @Test
    void resendMessagesDoesNotPullWhenDlqQueueSetIsNull() throws Exception {
        String dlqTopic = MixAll.DLQ_GROUP_TOPIC_PREFIX + "group-a";
        try (MockedConstruction<DefaultMQPullConsumer> mockedConsumers =
                     mockConstruction(DefaultMQPullConsumer.class, (consumer, context) -> {
                         doNothing().when(consumer).start();
                         when(consumer.fetchSubscribeMessageQueues(dlqTopic)).thenReturn(null);
                         doNothing().when(consumer).shutdown();
                     });
             MockedConstruction<DefaultMQProducer> mockedProducers =
                     mockConstruction(DefaultMQProducer.class)) {
            provider.resendMessages("instance-a", "group-a", 100L, 200L, "target-topic");

            assertThat(mockedConsumers.constructed()).hasSize(1);
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
                eq("SUCCESS"));
        verify(runtimeAdminClientResolver).resolveEndpoint("instance-a");
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
                    .containsExactly(1, 0, 1, "PARTIAL");

            assertThat(mockedConsumers.constructed()).hasSize(1);
            assertThat(mockedProducers.constructed()).hasSize(1);
            verify(mockedProducers.constructed().get(0)).send(any(Message.class));
        }
        verify(auditService).record(
                eq("RESEND_DLQ"),
                eq("group-a"),
                contains("matched=1, resent=0, failed=1"),
                eq("PARTIAL"));
    }

    @Test
    void createsUniqueProducerGroupsForConcurrentDlqResends() {
        String first = RocketMQDLQProvider.nextResendProducerGroup();
        String second = RocketMQDLQProvider.nextResendProducerGroup();

        assertThat(first).startsWith("studio-dlq-resend-");
        assertThat(second).startsWith("studio-dlq-resend-").isNotEqualTo(first);
    }
}
