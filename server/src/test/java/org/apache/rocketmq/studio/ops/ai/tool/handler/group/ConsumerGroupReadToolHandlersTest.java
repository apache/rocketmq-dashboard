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
package org.apache.rocketmq.studio.ops.ai.tool.handler.group;

import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.domain.enums.Protocol;
import org.apache.rocketmq.studio.common.domain.enums.SubscriptionMode;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.ConsumerInstanceVO;
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.apache.rocketmq.studio.instance.group.SubscriptionEntryVO;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupDetailInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupDetailOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.provider.apache.ConsumerLagResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsumerGroupReadToolHandlersTest {

    @Mock
    private MetadataService metadataService;

    private ConsumerGroupVO group;

    @BeforeEach
    void setUp() {
        ConsumerInstanceVO instance = ConsumerInstanceVO.builder()
                .clientId("client-1")
                .protocol(Protocol.Remoting)
                .address("127.0.0.1:10911")
                .subscribedTopics(List.of("TopicA"))
                .topicLag(Map.of("TopicA", 12L))
                .build();
        group = new ConsumerGroupVO();
        group.setName("group-a");
        group.setInstanceId("instance-a");
        group.setSubscriptionMode(SubscriptionMode.Push);
        group.setConsumeType(ConsumeType.CLUSTERING);
        group.setOnlineInstances(1);
        group.setTotalLag(12L);
        group.setSubscribedTopics(List.of("TopicA"));
        group.setRetryMaxTimes(16);
        group.setDelaySeconds(3);
        group.setConsumeStatsAvailable(true);
        group.setInstances(List.of(instance));
    }

    @Test
    void detailAggregatesDescribeProgressAndClientsBlocks() {
        GroupDetailToolHandler handler = new GroupDetailToolHandler(metadataService);
        assertThat(handler.name()).isEqualTo("rmq.group.detail");
        SubscriptionEntryVO subscription = SubscriptionEntryVO.builder()
                .topic("TopicA")
                .expression("*")
                .type("TAG")
                .filterMode("TAG")
                .consistency("CONSISTENT")
                .build();
        when(metadataService.consumerGroupRuntimeView("instance-a", "group-a")).thenReturn(group);
        when(metadataService.consumerGroupConfigurations("instance-a", "group-a")).thenReturn(List.of(group));
        when(metadataService.getGroupSubscriptions("instance-a", "group-a"))
                .thenReturn(List.of(subscription));
        when(metadataService.getGroupProgress("instance-a", "group-a"))
                .thenReturn(List.of(
                        QueueProgressVO.builder()
                                .topic("TopicA")
                                .broker("broker-a")
                                .queueId(0)
                                .brokerOffset(20)
                                .consumerOffset(8)
                                .diffTotal(12)
                                .build()));

        GroupDetailOutput output = handler.execute(
                new GroupDetailInput("untrusted-instance", "group-a", null), context());

        assertThat(output.instanceId()).isEqualTo("instance-a");
        assertThat(output.group()).isEqualTo("group-a");
        assertThat(output.health().status()).isEqualTo("WARNING");
        assertThat(output.subscriptions()).extracting(GroupDetailOutput.Subscription::topic)
                .containsExactly("TopicA");
        assertThat(output.instances()).extracting(GroupDetailOutput.Instance::clientId)
                .containsExactly("client-1");
        assertThat(output.configurations()).extracting(item -> item.name())
                .containsExactly("group-a");
        assertThat(output.progress().totalLag()).isEqualTo(12L);
        assertThat(output.progress().queues()).singleElement()
                .extracting(GroupDetailOutput.QueueProgress::broker)
                .isEqualTo("broker-a");
        assertThat(output.clients().totalClients()).isEqualTo(1);
        assertThat(output.clients().clients()).singleElement()
                .extracting(GroupDetailOutput.Client::clientId)
                .isEqualTo("client-1");
        verify(metadataService).consumerGroupRuntimeView("instance-a", "group-a");
        verify(metadataService).getGroupProgress("instance-a", "group-a");
    }

    @Test
    void detailFiltersProgressAndClientsByTopicName() {
        when(metadataService.consumerGroupRuntimeView("instance-a", "group-a")).thenReturn(group);
        when(metadataService.consumerGroupConfigurations("instance-a", "group-a")).thenReturn(List.of(group));
        when(metadataService.getGroupSubscriptions("instance-a", "group-a")).thenReturn(List.of());
        when(metadataService.getGroupProgress("instance-a", "group-a"))
                .thenReturn(List.of(
                        QueueProgressVO.builder()
                                .topic("TopicA")
                                .broker("broker-a")
                                .queueId(0)
                                .brokerOffset(20)
                                .consumerOffset(8)
                                .diffTotal(12)
                                .build(),
                        QueueProgressVO.builder()
                                .topic("TopicB")
                                .broker("broker-a")
                                .queueId(1)
                                .brokerOffset(30)
                                .consumerOffset(25)
                                .diffTotal(5)
                                .build()));

        GroupDetailOutput output = new GroupDetailToolHandler(metadataService)
                .execute(new GroupDetailInput("untrusted-instance", "group-a", "TopicA"), context());

        assertThat(output.progress().queues()).singleElement()
                .extracting(GroupDetailOutput.QueueProgress::queueId)
                .isEqualTo(0);
        assertThat(output.progress().totalLag()).isEqualTo(12L);
        assertThat(output.clients().totalClients()).isEqualTo(1);
    }

    @Test
    void detailMarksHealthUnknownWhenConsumerConnectionsAreUnavailableTest() {
        group.setOnlineInstances(-1);
        group.setTotalLag(12L);
        when(metadataService.consumerGroupRuntimeView("instance-a", "group-a")).thenReturn(group);
        when(metadataService.consumerGroupConfigurations("instance-a", "group-a")).thenReturn(List.of(group));
        when(metadataService.getGroupSubscriptions("instance-a", "group-a")).thenReturn(List.of());
        when(metadataService.getGroupProgress("instance-a", "group-a")).thenReturn(List.of());

        GroupDetailOutput output = new GroupDetailToolHandler(metadataService)
                .execute(new GroupDetailInput("instance-a", "group-a", null), context());

        assertThat(output.health().status()).isEqualTo("UNKNOWN");
        assertThat(output.health().reasons()).contains("Consumer connection information is unavailable.");
        assertThat(output.onlineInstances()).isEqualTo(-1);
    }

    @Test
    void detailMarksHealthUnknownWhenTheConsumerLagIsUnavailableTest() {
        group.setOnlineInstances(1);
        group.setTotalLag(ConsumerLagResolver.UNKNOWN);
        when(metadataService.consumerGroupRuntimeView("instance-a", "group-a")).thenReturn(group);
        when(metadataService.consumerGroupConfigurations("instance-a", "group-a")).thenReturn(List.of(group));
        when(metadataService.getGroupSubscriptions("instance-a", "group-a")).thenReturn(List.of());
        when(metadataService.getGroupProgress("instance-a", "group-a")).thenReturn(List.of());

        GroupDetailOutput output = new GroupDetailToolHandler(metadataService)
                .execute(new GroupDetailInput("instance-a", "group-a", null), context());

        assertThat(output.health().status()).isEqualTo("UNKNOWN");
        assertThat(output.health().reasons()).contains("Consumer lag information is unavailable.");
        assertThat(output.totalLag()).isEqualTo(ConsumerLagResolver.UNKNOWN);
    }

    @Test
    void detailKeepsTheConnectionWarningWhenTheConsumerLagIsUnavailableTest() {
        group.setOnlineInstances(0);
        group.setTotalLag(ConsumerLagResolver.UNKNOWN);
        when(metadataService.consumerGroupRuntimeView("instance-a", "group-a")).thenReturn(group);
        when(metadataService.consumerGroupConfigurations("instance-a", "group-a")).thenReturn(List.of(group));
        when(metadataService.getGroupSubscriptions("instance-a", "group-a")).thenReturn(List.of());
        when(metadataService.getGroupProgress("instance-a", "group-a")).thenReturn(List.of());

        GroupDetailOutput output = new GroupDetailToolHandler(metadataService)
                .execute(new GroupDetailInput("instance-a", "group-a", null), context());

        assertThat(output.health().status()).isEqualTo("WARNING");
        assertThat(output.health().reasons()).contains("The group has no online consumer.");
    }

    @Test
    void detailWithoutOnlineConsumersKeepsProgressAndClientsEmpty() {
        ConsumerGroupVO offline = new ConsumerGroupVO();
        offline.setName("group-a");
        offline.setInstanceId("instance-a");
        offline.setOnlineInstances(0);
        offline.setTotalLag(0L);
        offline.setSubscribedTopics(List.of());
        offline.setConsumeStatsAvailable(false);
        offline.setInstances(List.of());
        when(metadataService.consumerGroupRuntimeView("instance-a", "group-a")).thenReturn(offline);
        when(metadataService.consumerGroupConfigurations("instance-a", "group-a")).thenReturn(List.of(offline));
        when(metadataService.getGroupSubscriptions("instance-a", "group-a")).thenReturn(List.of());
        when(metadataService.getGroupProgress("instance-a", "group-a")).thenReturn(List.of());

        GroupDetailOutput output = new GroupDetailToolHandler(metadataService)
                .execute(new GroupDetailInput("untrusted-instance", "group-a", null), context());

        assertThat(output.health().status()).isEqualTo("UNKNOWN");
        assertThat(output.progress().queues()).isEmpty();
        assertThat(output.progress().totalLag()).isZero();
        assertThat(output.clients().clients()).isEmpty();
        assertThat(output.clients().totalClients()).isZero();
        assertThat(output.instances()).isEmpty();
    }

    private static ToolExecutionContext context() {
        return ToolExecutionContext.of(
                "instance-a", null, Map.of("instanceId", "instance-a"));
    }
}
