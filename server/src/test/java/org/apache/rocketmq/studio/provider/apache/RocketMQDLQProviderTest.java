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
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.protocol.admin.TopicStatsTable;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.instance.dlq.DLQGroupVO;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

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
import static org.mockito.Mockito.verify;
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
    void createsUniqueProducerGroupsForConcurrentDlqResends() {
        String first = RocketMQDLQProvider.nextResendProducerGroup();
        String second = RocketMQDLQProvider.nextResendProducerGroup();

        assertThat(first).startsWith("studio-dlq-resend-");
        assertThat(second).startsWith("studio-dlq-resend-").isNotEqualTo(first);
    }
}
