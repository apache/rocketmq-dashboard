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

import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.apache.rocketmq.studio.instance.group.SubscriptionEntryVO;
import org.apache.rocketmq.studio.instance.topic.BrokerRouteVO;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerVO;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.persistence.entity.RmqGroup;
import org.apache.rocketmq.studio.persistence.mapper.RmqGroupMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqTopicMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RocketMQMetadataProviderTest {

    @Mock
    private RmqTopicMapper topicMapper;

    @Mock
    private RmqGroupMapper groupMapper;

    @Mock
    private RuntimeAdminClientResolver runtimeAdminClientResolver;

    /**
     * Builds a provider without a configured NameServer, mirroring the former absent admin bean:
     * DB-backed listings work while live enrichment is skipped.
     */
    private RocketMQMetadataProvider newProvider() {
        return new RocketMQMetadataProvider(mock(MqAdminExtFactory.class), new RocketMQProperties(),
                topicMapper, groupMapper, runtimeAdminClientResolver);
    }

    @Test
    void listConsumerGroupsReadsConsumeTypeColumn() {
        RmqGroup entity = new RmqGroup();
        entity.setName("group-broadcast");
        entity.setClusterId("cluster-1");
        entity.setConsumeType("BROADCASTING");
        entity.setMessageModel("Push");
        when(groupMapper.selectList(any())).thenReturn(List.of(entity));

        RocketMQMetadataProvider provider = newProvider();

        List<ConsumerGroupVO> groups = provider.listConsumerGroups("cluster-1", null);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getConsumeType()).isEqualTo(ConsumeType.BROADCASTING);
    }

    @Test
    void listConsumerGroupsFallsBackToClusteringWhenConsumeTypeIsBlank() {
        RmqGroup entity = new RmqGroup();
        entity.setName("group-legacy");
        entity.setClusterId("cluster-1");
        entity.setConsumeType(null);
        entity.setMessageModel("Push");
        when(groupMapper.selectList(any())).thenReturn(List.of(entity));

        RocketMQMetadataProvider provider = newProvider();

        List<ConsumerGroupVO> groups = provider.listConsumerGroups("cluster-1", null);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getConsumeType()).isEqualTo(ConsumeType.CLUSTERING);
    }

    @Test
    void listConsumerGroupsShouldUseSelectedInstanceForRuntimeEnrichment() {
        RmqGroup entity = new RmqGroup();
        entity.setName("group-a");
        entity.setInstanceId("instance-a");
        entity.setClusterId("cluster-a");
        when(groupMapper.selectList(any())).thenReturn(List.of(entity));
        when(runtimeAdminClientResolver.execute(eq("instance-a"), any())).thenReturn(null);

        List<ConsumerGroupVO> groups = newProvider().listConsumerGroups("instance-a", null, null);

        assertThat(groups).singleElement().extracting(ConsumerGroupVO::getName).isEqualTo("group-a");
        verify(runtimeAdminClientResolver, times(2)).execute(eq("instance-a"), any());
    }

    @Test
    void getTopicRoutesShouldUseSelectedInstanceRuntimeClient() {
        List<BrokerRouteVO> routes = List.of(BrokerRouteVO.builder().brokerName("broker-a").build());
        when(runtimeAdminClientResolver.execute(eq("instance-a"), any())).thenReturn(routes);
        RocketMQMetadataProvider provider = newProvider();

        assertThat(provider.getTopicRoutes("instance-a", "orders")).containsExactlyElementsOf(routes);
        verify(runtimeAdminClientResolver).execute(eq("instance-a"), any());
    }

    @Test
    void getTopicConsumersShouldUseSelectedInstanceRuntimeClient() {
        List<TopicConsumerVO> consumers = List.of(TopicConsumerVO.builder().group("cg-orders").build());
        when(runtimeAdminClientResolver.execute(eq("instance-a"), any())).thenReturn(consumers);
        RocketMQMetadataProvider provider = newProvider();

        assertThat(provider.getTopicConsumers("instance-a", "orders")).containsExactlyElementsOf(consumers);
        verify(runtimeAdminClientResolver).execute(eq("instance-a"), any());
    }

    @Test
    void groupRuntimeDiagnosticsShouldUseSelectedInstanceRuntimeClient() {
        List<QueueProgressVO> progress = List.of(QueueProgressVO.builder().broker("broker-a").build());
        List<SubscriptionEntryVO> subscriptions = List.of(SubscriptionEntryVO.builder().topic("orders").build());
        when(runtimeAdminClientResolver.execute(eq("instance-a"), any()))
                .thenReturn(progress, subscriptions);
        RocketMQMetadataProvider provider = newProvider();

        assertThat(provider.getGroupProgress("instance-a", "cg-orders")).containsExactlyElementsOf(progress);
        assertThat(provider.getGroupSubscriptions("instance-a", "cg-orders"))
                .containsExactlyElementsOf(subscriptions);
        verify(runtimeAdminClientResolver, org.mockito.Mockito.times(2)).execute(eq("instance-a"), any());
    }

    @Test
    void getTopicRoutesSurfacesAdminFailure() throws Exception {
        DefaultMQAdminExt admin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        when(admin.examineTopicRouteInfo("TopicA")).thenThrow(new IllegalStateException("broker unavailable"));

        assertThatThrownBy(() -> newLiveProvider(admin).getTopicRoutes(null, "TopicA"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Failed to get routes for topic TopicA: broker unavailable")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(502));
    }

    @Test
    void getTopicConsumersSurfacesAdminFailure() throws Exception {
        DefaultMQAdminExt admin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        when(admin.queryTopicConsumeByWho("TopicA")).thenThrow(new IllegalStateException("broker unavailable"));

        assertThatThrownBy(() -> newLiveProvider(admin).getTopicConsumers(null, "TopicA"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Failed to get consumers for topic TopicA: broker unavailable")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(502));
    }

    @Test
    void getGroupProgressSurfacesAdminFailure() throws Exception {
        DefaultMQAdminExt admin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        when(admin.examineConsumeStats("group-a")).thenThrow(new IllegalStateException("broker unavailable"));

        assertThatThrownBy(() -> newLiveProvider(admin).getGroupProgress(null, "group-a"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Failed to get progress for group group-a: broker unavailable")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(502));
    }

    private RocketMQMetadataProvider newLiveProvider(MQAdminExt admin) throws Exception {
        MqAdminExtFactory factory = mock(MqAdminExtFactory.class);
        RocketMQProperties liveProperties = new RocketMQProperties();
        liveProperties.setNamesrvAddr("10.0.0.1:9876");
        lenient().when(factory.execute(anyString(), any(), any())).thenAnswer(invocation ->
                invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(2).apply(admin));
        return new RocketMQMetadataProvider(factory, liveProperties, topicMapper, groupMapper,
                runtimeAdminClientResolver);
    }
}
