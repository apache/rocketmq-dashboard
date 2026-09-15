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
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupTopicInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupClientsOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupDescribeInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupDescribeOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupProgressOutput;
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
        group.setNamespace("ns-a");
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
    void describeUsesBoundInstanceAndReturnsHealthAndSubscriptions() {
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

        GroupDescribeOutput output = new GroupDescribeToolHandler(metadataService)
                .execute(
                        new GroupDescribeInput("cluster-a", "group-a", null),
                        context());

        assertThat(output.cluster()).isEqualTo("instance-a");
        assertThat(output.group()).isEqualTo("group-a");
        assertThat(output.subscriptions()).extracting(GroupDescribeOutput.Subscription::topic)
                .containsExactly("TopicA");
        assertThat(output.health().status()).isEqualTo("WARNING");
        verify(metadataService).consumerGroupRuntimeView("instance-a", "group-a");
    }

    @Test
    void progressUsesBoundInstanceAndCalculatesTotalLag() {
        when(metadataService.getGroupProgress("instance-a", "group-a"))
                .thenReturn(List.of(
                        QueueProgressVO.builder()
                                .broker("broker-a")
                                .queueId(0)
                                .brokerOffset(20)
                                .consumerOffset(8)
                                .diffTotal(12)
                                .build()));

        GroupProgressOutput output = new GroupProgressToolHandler(metadataService)
                .execute(new GroupTopicInput("cluster-a", "group-a", null), context());

        assertThat(output.totalLag()).isEqualTo(12L);
        assertThat(output.queues()).singleElement()
                .extracting(GroupProgressOutput.QueueProgress::broker)
                .isEqualTo("broker-a");
        verify(metadataService).getGroupProgress("instance-a", "group-a");
    }

    @Test
    void clientsUsesBoundInstanceAndFiltersByTopic() {
        when(metadataService.consumerGroupRuntimeView("instance-a", "group-a")).thenReturn(group);

        GroupClientsOutput output = new GroupClientsToolHandler(metadataService)
                .execute(
                        new GroupTopicInput("cluster-a", "group-a", "TopicA"),
                        context());

        assertThat(output.totalClients()).isEqualTo(1);
        assertThat(output.clients()).singleElement()
                .extracting(client -> client.clientId())
                .isEqualTo("client-1");
        verify(metadataService).consumerGroupRuntimeView("instance-a", "group-a");
    }

    private static ToolExecutionContext context() {
        return ToolExecutionContext.of(
                "instance-a", null, Map.of("cluster", "cluster-a"));
    }
}
