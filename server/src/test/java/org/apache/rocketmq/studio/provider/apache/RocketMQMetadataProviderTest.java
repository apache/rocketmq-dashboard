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

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.admin.OffsetWrapper;
import org.apache.rocketmq.remoting.protocol.body.GroupList;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.route.QueueData;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.domain.enums.SubscriptionMode;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.apache.rocketmq.studio.instance.group.SubscriptionEntryVO;
import org.apache.rocketmq.studio.instance.topic.BrokerRouteVO;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerVO;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerPageVO;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.persistence.entity.RmqGroup;
import org.apache.rocketmq.studio.persistence.entity.RmqTopic;
import org.apache.rocketmq.studio.persistence.mapper.RmqGroupMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqTopicMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
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
        assertThat(groups.get(0).getSubscriptionMode()).isEqualTo(SubscriptionMode.Push);
    }

    @Test
    void listConsumerGroupsReadsPopSubscriptionMode() {
        RmqGroup entity = new RmqGroup();
        entity.setName("group-pop");
        entity.setClusterId("cluster-1");
        entity.setConsumeType("CLUSTERING");
        entity.setMessageModel("Pop");
        when(groupMapper.selectList(any())).thenReturn(List.of(entity));

        RocketMQMetadataProvider provider = newProvider();

        List<ConsumerGroupVO> groups = provider.listConsumerGroups("cluster-1", null);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getSubscriptionMode()).isEqualTo(SubscriptionMode.Pop);
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
    void listConsumerGroupsShouldNotFetchLiveAdminInfoForEachGroup() {
        RmqGroup first = new RmqGroup();
        first.setName("group-a");
        first.setClusterId("cluster-1");
        first.setConsumeType("CLUSTERING");
        first.setMaxRetry(16);
        RmqGroup second = new RmqGroup();
        second.setName("group-b");
        second.setClusterId("cluster-1");
        second.setConsumeType("BROADCASTING");
        second.setMaxRetry(3);
        when(groupMapper.selectList(any())).thenReturn(List.of(first, second));

        RocketMQMetadataProvider provider = newProvider();

        List<ConsumerGroupVO> groups = provider.listConsumerGroups("cluster-1", null);

        assertThat(groups).extracting(ConsumerGroupVO::getName).containsExactly("group-a", "group-b");
        assertThat(groups).extracting(ConsumerGroupVO::getOnlineInstances).containsExactly(0, 0);
        assertThat(groups).extracting(ConsumerGroupVO::getTotalLag).containsExactly(0L, 0L);
        verify(groupMapper).selectList(any());
        verifyNoInteractions(runtimeAdminClientResolver);
    }

    @Test
    void listTopicsShouldScopeDatabaseQueryToSelectedInstance() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqTopic.class);
        when(topicMapper.selectList(any())).thenReturn(List.of());
        RocketMQMetadataProvider provider = newProvider();

        assertThat(provider.listTopics("instance-a", "cluster-1", "NORMAL", "orders")).isEmpty();

        org.mockito.ArgumentCaptor<LambdaQueryWrapper<org.apache.rocketmq.studio.persistence.entity.RmqTopic>> captor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(topicMapper).selectList(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("instance_id", "cluster_id");
    }

    @Test
    void listConsumerGroupsShouldScopeDatabaseQueryToSelectedInstance() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqGroup.class);
        when(groupMapper.selectList(any())).thenReturn(List.of());
        RocketMQMetadataProvider provider = newProvider();

        assertThat(provider.listConsumerGroups("instance-a", "cluster-1", "orders")).isEmpty();

        org.mockito.ArgumentCaptor<LambdaQueryWrapper<RmqGroup>> captor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(groupMapper, times(1)).selectList(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("instance_id", "cluster_id");
    }

    @Test
    void getTopicRoutesShouldUseSelectedInstanceRuntimeClient() {
        List<BrokerRouteVO> routes = List.of(BrokerRouteVO.builder().brokerName("broker-a").build());
        when(runtimeAdminClientResolver.execute(eq("instance-a"), any())).thenReturn(routes);
        RocketMQMetadataProvider provider = newProvider();

        assertThat(provider.getTopicRoutes("instance-a", "orders")).containsExactlyElementsOf(routes);
        verify(runtimeAdminClientResolver).execute(eq("instance-a"), any());
    }

    @ParameterizedTest
    @CsvSource({
        "0, RW",
        "2, WO",
        "3, WO",
        "4, RO",
        "5, RO",
        "6, RW",
        "7, RW"
    })
    void getTopicRoutesShouldInterpretPermissionBits(int permission, TopicPerm expected) throws Exception {
        DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
        QueueData queueData = new QueueData();
        queueData.setBrokerName("broker-a");
        queueData.setReadQueueNums(4);
        queueData.setWriteQueueNums(2);
        queueData.setPerm(permission);
        BrokerData brokerData = new BrokerData();
        brokerData.setBrokerName("broker-a");
        brokerData.setBrokerAddrs(new HashMap<>(Map.of(0L, "10.0.0.1:10911")));
        TopicRouteData routeData = new TopicRouteData();
        routeData.setQueueDatas(List.of(queueData));
        routeData.setBrokerDatas(List.of(brokerData));
        when(admin.examineTopicRouteInfo("TopicA")).thenReturn(routeData);

        List<BrokerRouteVO> routes = newLiveProvider(admin).getTopicRoutes(null, "TopicA");

        assertThat(routes).singleElement().satisfies(route -> {
            assertThat(route.getBrokerName()).isEqualTo("broker-a");
            assertThat(route.getBrokerAddr()).isEqualTo("10.0.0.1:10911");
            assertThat(route.getReadQueues()).isEqualTo(4);
            assertThat(route.getWriteQueues()).isEqualTo(2);
            assertThat(route.getPerm()).isEqualTo(expected);
        });
    }

    @Test
    void getTopicConsumersShouldUseSelectedInstanceRuntimeClient() {
        TopicConsumerPageVO consumers = TopicConsumerPageVO.builder()
                .items(List.of(TopicConsumerVO.builder().group("cg-orders").build()))
                .total(1).page(1).pageSize(Integer.MAX_VALUE).build();
        when(runtimeAdminClientResolver.execute(eq("instance-a"), any())).thenReturn(consumers);
        RocketMQMetadataProvider provider = newProvider();

        assertThat(provider.getTopicConsumers("instance-a", "orders"))
                .extracting(TopicConsumerVO::getGroup).containsExactly("cg-orders");
        verify(runtimeAdminClientResolver).execute(eq("instance-a"), any());
    }

    @Test
    void getTopicConsumersPageShouldOnlyFetchDiagnosticsForTheRequestedGroups() throws Exception {
        DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
        GroupList groups = new GroupList();
        groups.setGroupList(new HashSet<>(List.of("group-a", "group-b", "group-c")));
        when(admin.queryTopicConsumeByWho("TopicA")).thenReturn(groups);

        TopicConsumerPageVO result = newLiveProvider(admin).getTopicConsumersPage(null, "TopicA", 2, 2);

        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getItems()).extracting(TopicConsumerVO::getGroup).containsExactly("group-c");
        verify(admin).examineConsumeStats("group-c", "TopicA");
        verify(admin).examineConsumerConnectionInfo("group-c");
        verify(admin, never()).examineConsumeStats("group-a", "TopicA");
        verify(admin, never()).examineConsumeStats("group-b", "TopicA");
    }

    @Test
    void getTopicConsumersPageShouldReturnEmptyPageForLargePageNumberTest() throws Exception {
        DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
        GroupList groups = new GroupList();
        groups.setGroupList(new HashSet<>(List.of("group-a", "group-b")));
        when(admin.queryTopicConsumeByWho("TopicA")).thenReturn(groups);

        TopicConsumerPageVO result = newLiveProvider(admin)
                .getTopicConsumersPage(null, "TopicA", Integer.MAX_VALUE, 100);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getPage()).isEqualTo(Integer.MAX_VALUE);
        assertThat(result.getPageSize()).isEqualTo(100);
        verify(admin, never()).examineConsumeStats(anyString(), eq("TopicA"));
    }

    @Test
    void metadataProviderDefaultPageShouldReturnEmptyPageForLargePageNumberTest() {
        MetadataProvider provider = mock(MetadataProvider.class);
        when(provider.getTopicConsumers("instance-a", "orders")).thenReturn(List.of(
                TopicConsumerVO.builder().group("group-a").build(),
                TopicConsumerVO.builder().group("group-b").build()));
        when(provider.getTopicConsumersPage("instance-a", "orders", Integer.MAX_VALUE, 100))
                .thenCallRealMethod();

        TopicConsumerPageVO result = provider.getTopicConsumersPage(
                "instance-a", "orders", Integer.MAX_VALUE, 100);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getPage()).isEqualTo(Integer.MAX_VALUE);
        assertThat(result.getPageSize()).isEqualTo(100);
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
    void getTopicConsumersMarksMetricsUnavailableWhenGroupStatsCannotBeRead() throws Exception {
        DefaultMQAdminExt admin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        GroupList groupList = new GroupList();
        groupList.setGroupList(new HashSet<>(List.of("cg-orders")));
        when(admin.queryTopicConsumeByWho("TopicA")).thenReturn(groupList);
        when(admin.examineConsumeStats("cg-orders", "TopicA"))
                .thenThrow(new IllegalStateException("broker unavailable"));

        List<TopicConsumerVO> consumers = newLiveProvider(admin).getTopicConsumers(null, "TopicA");

        assertThat(consumers).singleElement().satisfies(consumer -> {
            assertThat(consumer.getGroup()).isEqualTo("cg-orders");
            assertThat(consumer.isMetricsAvailable()).isFalse();
        });
    }

    @Test
    void getTopicConsumersKeepsUnknownWhenAnyQueueLagIsUnknown() throws Exception {
        DefaultMQAdminExt admin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        mockTopicConsumeStats(admin, offset(20, 10), offset(0, 1));

        List<TopicConsumerVO> consumers = newLiveProvider(admin).getTopicConsumers(null, "TopicA");

        assertThat(consumers).singleElement()
                .extracting(TopicConsumerVO::getDiffTotal)
                .isEqualTo(ConsumerLagResolver.UNKNOWN);
    }

    @Test
    void getTopicConsumersStillSumsKnownQueueLags() throws Exception {
        DefaultMQAdminExt admin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        mockTopicConsumeStats(admin, offset(20, 10), offset(7, 4));

        List<TopicConsumerVO> consumers = newLiveProvider(admin).getTopicConsumers(null, "TopicA");

        assertThat(consumers).singleElement()
                .extracting(TopicConsumerVO::getDiffTotal)
                .isEqualTo(13L);
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

    @Test
    void getGroupProgressShouldReturnEmptyWhenConsumerNotOnlineTest() throws Exception {
        DefaultMQAdminExt admin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        when(admin.examineConsumeStats("group-offline")).thenThrow(
                new org.apache.rocketmq.client.exception.MQBrokerException(
                        org.apache.rocketmq.remoting.protocol.ResponseCode.CONSUMER_NOT_ONLINE,
                        "Not found the consumer group consume stats, because return offset table is empty, "
                                + "maybe the consumer not online"));

        assertThat(newLiveProvider(admin).getGroupProgress(null, "group-offline")).isEmpty();
    }

    @Test
    void getGroupSubscriptionsSurfacesAdminFailure() throws Exception {
        DefaultMQAdminExt admin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        when(admin.examineConsumerConnectionInfo("group-a"))
                .thenThrow(new IllegalStateException("broker unavailable"));

        assertThatThrownBy(() -> newLiveProvider(admin).getGroupSubscriptions(null, "group-a"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Failed to get subscriptions for group group-a: broker unavailable")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(502));
    }

    @Test
    void getGroupSubscriptionsShouldCreateMissingRetryTopicBeforeQueryingTest() throws Exception {
        DefaultMQAdminExt admin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        when(admin.examineTopicRouteInfo("%RETRY%group-pop"))
                .thenThrow(new IllegalStateException("route not found"));

        org.apache.rocketmq.remoting.protocol.body.ClusterInfo clusterInfo =
                new org.apache.rocketmq.remoting.protocol.body.ClusterInfo();
        java.util.HashMap<Long, String> brokerAddrs = new java.util.HashMap<>();
        brokerAddrs.put(0L, "10.0.0.11:10911");
        Map<String, org.apache.rocketmq.remoting.protocol.route.BrokerData> brokerAddrTable =
                new java.util.HashMap<>();
        brokerAddrTable.put("broker-a", new org.apache.rocketmq.remoting.protocol.route.BrokerData(
                "cluster-a", "broker-a", brokerAddrs));
        clusterInfo.setBrokerAddrTable(brokerAddrTable);
        when(admin.examineBrokerClusterInfo()).thenReturn(clusterInfo);

        org.apache.rocketmq.remoting.protocol.body.ConsumerConnection connection =
                new org.apache.rocketmq.remoting.protocol.body.ConsumerConnection();
        java.util.concurrent.ConcurrentHashMap<String, org.apache.rocketmq.remoting.protocol.heartbeat.SubscriptionData> table =
                new java.util.concurrent.ConcurrentHashMap<>();
        org.apache.rocketmq.remoting.protocol.heartbeat.SubscriptionData subscription =
                new org.apache.rocketmq.remoting.protocol.heartbeat.SubscriptionData();
        subscription.setTopic("TopicA");
        subscription.setSubString("*");
        subscription.setExpressionType("TAG");
        table.put("TopicA", subscription);
        connection.setSubscriptionTable(table);
        when(admin.examineConsumerConnectionInfo("group-pop")).thenReturn(connection);

        List<SubscriptionEntryVO> subscriptions =
                newLiveProvider(admin).getGroupSubscriptions(null, "group-pop");

        assertThat(subscriptions).extracting(SubscriptionEntryVO::getTopic).containsExactly("TopicA");
        org.mockito.ArgumentCaptor<org.apache.rocketmq.common.TopicConfig> captor =
                org.mockito.ArgumentCaptor.forClass(org.apache.rocketmq.common.TopicConfig.class);
        verify(admin).createAndUpdateTopicConfig(eq("10.0.0.11:10911"), captor.capture());
        assertThat(captor.getValue().getTopicName()).isEqualTo("%RETRY%group-pop");
        assertThat(captor.getValue().getReadQueueNums()).isEqualTo(1);
        assertThat(captor.getValue().getWriteQueueNums()).isEqualTo(1);
    }

    @Test
    void getGroupSubscriptionsShouldReturnEmptyWhenGroupOnlyConnectsViaProxyTest() throws Exception {
        DefaultMQAdminExt admin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        org.apache.rocketmq.remoting.protocol.route.TopicRouteData route =
                new org.apache.rocketmq.remoting.protocol.route.TopicRouteData();
        java.util.HashMap<Long, String> brokerAddrs = new java.util.HashMap<>();
        brokerAddrs.put(0L, "10.0.0.11:10911");
        route.setBrokerDatas(List.of(new org.apache.rocketmq.remoting.protocol.route.BrokerData(
                "cluster-a", "broker-a", brokerAddrs)));
        when(admin.examineTopicRouteInfo("%RETRY%group-proxy")).thenReturn(route);
        when(admin.examineConsumerConnectionInfo("group-proxy")).thenThrow(
                new org.apache.rocketmq.client.exception.MQBrokerException(
                        org.apache.rocketmq.remoting.protocol.ResponseCode.CONSUMER_NOT_ONLINE,
                        "the consumer group[group-proxy] not online BROKER: 10.0.0.11:10911"));

        assertThat(newLiveProvider(admin).getGroupSubscriptions(null, "group-proxy")).isEmpty();
    }

    @Test
    void getGroupSubscriptionsShouldFallBackToProxyConnectionsTest() throws Exception {
        DefaultMQAdminExt admin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        org.apache.rocketmq.remoting.protocol.route.TopicRouteData route =
                new org.apache.rocketmq.remoting.protocol.route.TopicRouteData();
        java.util.HashMap<Long, String> brokerAddrs = new java.util.HashMap<>();
        brokerAddrs.put(0L, "10.0.0.11:10911");
        route.setBrokerDatas(List.of(new org.apache.rocketmq.remoting.protocol.route.BrokerData(
                "cluster-a", "broker-a", brokerAddrs)));
        when(admin.examineTopicRouteInfo("%RETRY%group-proxy")).thenReturn(route);
        when(admin.examineConsumerConnectionInfo("group-proxy")).thenThrow(
                new org.apache.rocketmq.client.exception.MQBrokerException(
                        org.apache.rocketmq.remoting.protocol.ResponseCode.CONSUMER_NOT_ONLINE,
                        "the consumer group[group-proxy] not online BROKER: 10.0.0.11:10911"));

        ProxyConsumerResolver resolver = org.mockito.Mockito.mock(ProxyConsumerResolver.class);
        org.apache.rocketmq.remoting.protocol.body.ConsumerConnection viaProxy =
                new org.apache.rocketmq.remoting.protocol.body.ConsumerConnection();
        java.util.concurrent.ConcurrentHashMap<String,
                org.apache.rocketmq.remoting.protocol.heartbeat.SubscriptionData> table =
                new java.util.concurrent.ConcurrentHashMap<>();
        org.apache.rocketmq.remoting.protocol.heartbeat.SubscriptionData subscription =
                new org.apache.rocketmq.remoting.protocol.heartbeat.SubscriptionData();
        subscription.setTopic("studio-normal");
        subscription.setSubString("*");
        subscription.setExpressionType("TAG");
        table.put("studio-normal", subscription);
        viaProxy.setSubscriptionTable(table);
        java.util.HashSet<org.apache.rocketmq.remoting.protocol.body.Connection> proxyConnections =
                new java.util.HashSet<>();
        org.apache.rocketmq.remoting.protocol.body.Connection proxyConnection =
                new org.apache.rocketmq.remoting.protocol.body.Connection();
        proxyConnection.setClientId("client-1");
        proxyConnection.setClientAddr("10.0.3.104:50124");
        proxyConnections.add(proxyConnection);
        viaProxy.setConnectionSet(proxyConnections);
        when(resolver.resolveConsumerConnection(null, "group-proxy")).thenReturn(viaProxy);

        RocketMQMetadataProvider provider = newLiveProvider(admin);
        org.springframework.test.util.ReflectionTestUtils.setField(
                provider, "proxyConsumerResolver", resolver);

        List<SubscriptionEntryVO> subscriptions = provider.getGroupSubscriptions(null, "group-proxy");

        assertThat(subscriptions).extracting(SubscriptionEntryVO::getTopic)
                .containsExactly("studio-normal");
        assertThat(subscriptions).extracting(SubscriptionEntryVO::getConsistency)
                .containsExactly("consistent");
    }

    @Test
    void listConsumerGroupsShouldEnrichOnlineInstancesFromBrokerConnectionsTest() throws Exception {
        RmqGroup entity = new RmqGroup();
        entity.setName("cg-online");
        entity.setInstanceId("instance-a");
        when(groupMapper.selectList(any())).thenReturn(List.of(entity));

        DefaultMQAdminExt admin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        org.apache.rocketmq.remoting.protocol.body.ConsumerConnection connection =
                new org.apache.rocketmq.remoting.protocol.body.ConsumerConnection();
        java.util.HashSet<org.apache.rocketmq.remoting.protocol.body.Connection> connections =
                new java.util.HashSet<>();
        org.apache.rocketmq.remoting.protocol.body.Connection connA =
                new org.apache.rocketmq.remoting.protocol.body.Connection();
        connA.setClientId("client-a");
        connA.setClientAddr("10.0.0.20:10000");
        connections.add(connA);
        org.apache.rocketmq.remoting.protocol.body.Connection connB =
                new org.apache.rocketmq.remoting.protocol.body.Connection();
        connB.setClientId("client-b");
        connB.setClientAddr("10.0.0.21:10000");
        connections.add(connB);
        connection.setConnectionSet(connections);
        when(admin.examineConsumerConnectionInfo("cg-online")).thenReturn(connection);
        when(runtimeAdminClientResolver.execute(org.mockito.ArgumentMatchers.eq("instance-a"), any()))
                .thenAnswer(invocation ->
                        invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(1).apply(admin));

        RocketMQMetadataProvider provider = newLiveProvider(admin);

        List<ConsumerGroupVO> groups = provider.listConsumerGroups("instance-a", null, null);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getOnlineInstances()).isEqualTo(2);
        assertThat(groups.get(0).getInstances())
                .extracting(org.apache.rocketmq.studio.instance.group.ConsumerInstanceVO::getClientId)
                .containsExactlyInAnyOrder("client-a", "client-b");
    }

    @Test
    void listConsumerGroupsShouldEnrichOnlineInstancesViaProxyFallbackTest() throws Exception {
        RmqGroup entity = new RmqGroup();
        entity.setName("cg-proxy");
        entity.setInstanceId("instance-a");
        when(groupMapper.selectList(any())).thenReturn(List.of(entity));

        DefaultMQAdminExt admin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        when(admin.examineConsumerConnectionInfo("cg-proxy")).thenThrow(
                new org.apache.rocketmq.client.exception.MQBrokerException(
                        org.apache.rocketmq.remoting.protocol.ResponseCode.CONSUMER_NOT_ONLINE,
                        "the consumer group[cg-proxy] not online BROKER: 10.0.0.11:10911"));
        when(runtimeAdminClientResolver.execute(org.mockito.ArgumentMatchers.eq("instance-a"), any()))
                .thenAnswer(invocation ->
                        invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(1).apply(admin));

        ProxyConsumerResolver resolver = org.mockito.Mockito.mock(ProxyConsumerResolver.class);
        org.apache.rocketmq.remoting.protocol.body.ConsumerConnection viaProxy =
                new org.apache.rocketmq.remoting.protocol.body.ConsumerConnection();
        java.util.HashSet<org.apache.rocketmq.remoting.protocol.body.Connection> connections =
                new java.util.HashSet<>();
        org.apache.rocketmq.remoting.protocol.body.Connection conn =
                new org.apache.rocketmq.remoting.protocol.body.Connection();
        conn.setClientId("client-1");
        conn.setClientAddr("10.0.3.104:50124");
        connections.add(conn);
        viaProxy.setConnectionSet(connections);
        when(resolver.resolveConsumerConnection("instance-a", "cg-proxy")).thenReturn(viaProxy);

        RocketMQMetadataProvider provider = newLiveProvider(admin);
        org.springframework.test.util.ReflectionTestUtils.setField(
                provider, "proxyConsumerResolver", resolver);

        List<ConsumerGroupVO> groups = provider.listConsumerGroups("instance-a", null, null);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getOnlineInstances()).isEqualTo(1);
        assertThat(groups.get(0).getInstances())
                .extracting(org.apache.rocketmq.studio.instance.group.ConsumerInstanceVO::getAddress)
                .containsExactly("10.0.3.104:50124");
    }

    @Test
    void listConsumerGroupsShouldEnrichLagAndDelayFromConsumeStatsTest() throws Exception {
        RmqGroup entity = new RmqGroup();
        entity.setName("cg-lag");
        entity.setInstanceId("instance-a");
        when(groupMapper.selectList(any())).thenReturn(List.of(entity));

        DefaultMQAdminExt admin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        org.apache.rocketmq.remoting.protocol.body.ConsumerConnection connection =
                new org.apache.rocketmq.remoting.protocol.body.ConsumerConnection();
        connection.setConnectionSet(new java.util.HashSet<>());
        when(admin.examineConsumerConnectionInfo("cg-lag")).thenReturn(connection);

        org.apache.rocketmq.remoting.protocol.admin.ConsumeStats stats =
                new org.apache.rocketmq.remoting.protocol.admin.ConsumeStats();
        org.apache.rocketmq.common.message.MessageQueue queue =
                new org.apache.rocketmq.common.message.MessageQueue("studio-normal", "broker-a", 0);
        org.apache.rocketmq.remoting.protocol.admin.OffsetWrapper wrapper =
                new org.apache.rocketmq.remoting.protocol.admin.OffsetWrapper();
        wrapper.setBrokerOffset(100);
        wrapper.setConsumerOffset(60);
        wrapper.setLastTimestamp(System.currentTimeMillis() - 5_000);
        stats.getOffsetTable().put(queue, wrapper);
        when(admin.examineConsumeStats("cg-lag")).thenReturn(stats);
        when(runtimeAdminClientResolver.execute(org.mockito.ArgumentMatchers.eq("instance-a"), any()))
                .thenAnswer(invocation ->
                        invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(1).apply(admin));

        RocketMQMetadataProvider provider = newLiveProvider(admin);

        List<ConsumerGroupVO> groups = provider.listConsumerGroups("instance-a", null, null);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getTotalLag()).isEqualTo(40);
        assertThat(groups.get(0).getDelaySeconds()).isBetween(4, 30);
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

    private void mockTopicConsumeStats(DefaultMQAdminExt admin, OffsetWrapper... queueOffsets) throws Exception {
        mockTopicGroup(admin);
        Map<MessageQueue, OffsetWrapper> offsets = new LinkedHashMap<>();
        for (int queueId = 0; queueId < queueOffsets.length; queueId++) {
            offsets.put(new MessageQueue("TopicA", "broker-a", queueId), queueOffsets[queueId]);
        }
        ConsumeStats stats = new ConsumeStats();
        stats.setOffsetTable(offsets);
        when(admin.examineConsumeStats("group-a", "TopicA")).thenReturn(stats);
    }

    private void mockTopicGroup(DefaultMQAdminExt admin) throws Exception {
        GroupList groupList = new GroupList();
        groupList.setGroupList(new HashSet<>(List.of("group-a")));
        when(admin.queryTopicConsumeByWho("TopicA")).thenReturn(groupList);
    }

    private OffsetWrapper offset(long brokerOffset, long consumerOffset) {
        OffsetWrapper offset = new OffsetWrapper();
        offset.setBrokerOffset(brokerOffset);
        offset.setConsumerOffset(consumerOffset);
        return offset;
    }
}
