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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.body.SubscriptionGroupWrapper;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.remoting.protocol.body.TopicConfigSerializeWrapper;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.apache.rocketmq.studio.common.domain.enums.ClusterType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.ops.dashboard.DashboardDataVO;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RocketMQDashboardProviderTest {

    @Test
    void dashboardClusterOverviewShouldUseBrokerVersionDesc() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        when(adminExt.fetchAllTopicList()).thenReturn(topicList());
        when(adminExt.getAllTopicConfig("10.0.0.11:10911", 5000)).thenReturn(topicConfig("order-topic"));
        when(adminExt.getAllSubscriptionGroup("10.0.0.11:10911", 5000)).thenReturn(subscriptionGroups());
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911")).thenReturn(runtimeStats());

        RocketMQDashboardProvider provider = newProvider(adminExt);

        DashboardDataVO dashboard = provider.getDashboardData();

        assertThat(dashboard.getClusters()).hasSize(1);
        assertThat(dashboard.getClusters().get(0).getVersion()).isEqualTo("V5_3_3");
        assertThat(dashboard.getClusters().get(0).getType()).isEqualTo(ClusterType.V5_PROXY_CLUSTER);
        assertThat(dashboard.getStats().getHealthyClusters()).isEqualTo(1);
        verify(adminExt, times(1)).fetchBrokerRuntimeStats("10.0.0.11:10911");
    }

    @Test
    void dashboardShouldCountTopicsFromBrokerConfigWithoutPerTopicRouteRequests() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        when(adminExt.getAllTopicConfig("10.0.0.11:10911", 5000))
                .thenReturn(topicConfig("order-topic", "payments", "SCHEDULE_TOPIC_XXXX"));
        when(adminExt.getAllSubscriptionGroup("10.0.0.11:10911", 5000)).thenReturn(subscriptionGroups());
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911")).thenReturn(runtimeStats());

        DashboardDataVO dashboard = newProvider(adminExt).getDashboardData();

        assertThat(dashboard.getStats().getTotalTopics()).isEqualTo(2);
        assertThat(dashboard.getClusters()).singleElement().satisfies(cluster -> {
            assertThat(cluster.getTopics()).isEqualTo(2);
            assertThat(cluster.getStatus()).isEqualTo(ClusterStatus.healthy);
        });
        verify(adminExt).getAllTopicConfig("10.0.0.11:10911", 5000);
        verify(adminExt, never()).examineTopicRouteInfo(anyString());
    }

    @Test
    void dashboardShouldExcludeBrokerNamedStatsTopics() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        // "broker-a" matches the broker name and is a self-named stats topic, not a user topic.
        when(adminExt.getAllTopicConfig("10.0.0.11:10911", 5000))
                .thenReturn(topicConfig("order-topic", "broker-a"));
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911")).thenReturn(runtimeStats());

        DashboardDataVO dashboard = newProvider(adminExt).getDashboardData();

        assertThat(dashboard.getStats().getTotalTopics()).isEqualTo(1);
        assertThat(dashboard.getClusters()).singleElement().satisfies(cluster ->
                assertThat(cluster.getTopics()).isEqualTo(1));
    }

    @Test
    void dashboardShouldDeduplicateTopicsReportedByMultipleClusterBrokers() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfoWithTwoMasters());
        when(adminExt.getAllTopicConfig("10.0.0.11:10911", 5000))
                .thenReturn(topicConfig("orders", "payments"));
        when(adminExt.getAllTopicConfig("10.0.0.12:10911", 5000))
                .thenReturn(topicConfig("orders", "inventory"));
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911")).thenReturn(runtimeStats());
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.12:10911")).thenReturn(runtimeStats());

        DashboardDataVO dashboard = newProvider(adminExt).getDashboardData();

        assertThat(dashboard.getStats().getTotalTopics()).isEqualTo(3);
        assertThat(dashboard.getClusters()).singleElement()
                .extracting(cluster -> cluster.getTopics())
                .isEqualTo(3);
        verify(adminExt, never()).examineTopicRouteInfo(anyString());
    }

    @Test
    void dashboardShouldDeduplicateGroupsReportedByMultipleClusterBrokers() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfoWithTwoMasters());
        when(adminExt.getAllSubscriptionGroup("10.0.0.11:10911", 5000))
                .thenReturn(subscriptionGroups("cg-orders", "cg-payments"));
        when(adminExt.getAllSubscriptionGroup("10.0.0.12:10911", 5000))
                .thenReturn(subscriptionGroups("cg-orders", "cg-inventory"));
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911")).thenReturn(runtimeStats());
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.12:10911")).thenReturn(runtimeStats());

        DashboardDataVO dashboard = newProvider(adminExt).getDashboardData();

        assertThat(dashboard.getStats().getTotalConsumerGroups()).isEqualTo(3);
        assertThat(dashboard.getClusters()).singleElement()
                .extracting(cluster -> cluster.getGroups())
                .isEqualTo(3);
    }

    @Test
    void dashboardShouldMarkClusterWarningWhenTopicCountsAreUnavailable() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        when(adminExt.getAllTopicConfig("10.0.0.11:10911", 5000))
                .thenThrow(new RuntimeException("broker unavailable"));
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911")).thenReturn(runtimeStats());

        DashboardDataVO dashboard = newProvider(adminExt).getDashboardData();

        assertThat(dashboard.getClusters()).singleElement()
                .extracting(cluster -> cluster.getStatus())
                .isEqualTo(ClusterStatus.warning);
        assertThat(dashboard.getStats().getHealthyClusters()).isZero();
    }

    @Test
    void dashboardShouldMarkClusterWarningWhenGroupCountsAreUnavailable() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        when(adminExt.getAllTopicConfig("10.0.0.11:10911", 5000)).thenReturn(topicConfig("orders"));
        when(adminExt.getAllSubscriptionGroup("10.0.0.11:10911", 5000))
                .thenThrow(new RuntimeException("broker unavailable"));
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911")).thenReturn(runtimeStats());

        DashboardDataVO dashboard = newProvider(adminExt).getDashboardData();

        assertThat(dashboard.getClusters()).singleElement()
                .extracting(cluster -> cluster.getStatus())
                .isEqualTo(ClusterStatus.warning);
        assertThat(dashboard.getStats().getHealthyClusters()).isZero();
    }

    @Test
    void dashboardShouldMarkClusterWarningWhenGroupResponseIsNull() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        when(adminExt.getAllTopicConfig("10.0.0.11:10911", 5000)).thenReturn(topicConfig("orders"));
        when(adminExt.getAllSubscriptionGroup("10.0.0.11:10911", 5000)).thenReturn(null);
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911")).thenReturn(runtimeStats());

        DashboardDataVO dashboard = newProvider(adminExt).getDashboardData();

        assertThat(dashboard.getStats().getTotalConsumerGroups()).isZero();
        assertThat(dashboard.getStats().getHealthyClusters()).isZero();
        assertThat(dashboard.getClusters()).singleElement().satisfies(cluster -> {
            assertThat(cluster.getGroups()).isZero();
            assertThat(cluster.getStatus()).isEqualTo(ClusterStatus.warning);
        });
    }

    @Test
    void dashboardShouldMarkClusterWarningWhenGroupTableIsNull() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        SubscriptionGroupWrapper nullTable = new SubscriptionGroupWrapper();
        nullTable.setSubscriptionGroupTable(null);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        when(adminExt.getAllTopicConfig("10.0.0.11:10911", 5000)).thenReturn(topicConfig("orders"));
        when(adminExt.getAllSubscriptionGroup("10.0.0.11:10911", 5000)).thenReturn(nullTable);
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911")).thenReturn(runtimeStats());

        DashboardDataVO dashboard = newProvider(adminExt).getDashboardData();

        assertThat(dashboard.getClusters()).singleElement()
                .extracting(cluster -> cluster.getStatus())
                .isEqualTo(ClusterStatus.warning);
        assertThat(dashboard.getStats().getHealthyClusters()).isZero();
    }

    @Test
    void dashboardShouldKeepClusterHealthyForValidEmptyGroupTable() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        when(adminExt.getAllTopicConfig("10.0.0.11:10911", 5000)).thenReturn(topicConfig("orders"));
        when(adminExt.getAllSubscriptionGroup("10.0.0.11:10911", 5000)).thenReturn(subscriptionGroups());
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911")).thenReturn(runtimeStats());

        DashboardDataVO dashboard = newProvider(adminExt).getDashboardData();

        assertThat(dashboard.getClusters()).singleElement().satisfies(cluster -> {
            assertThat(cluster.getGroups()).isZero();
            assertThat(cluster.getStatus()).isEqualTo(ClusterStatus.healthy);
        });
        assertThat(dashboard.getStats().getHealthyClusters()).isEqualTo(1);
    }

    @Test
    void dashboardShouldSurviveNullTopologyTables() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        ClusterInfo bare = new ClusterInfo();
        bare.setBrokerAddrTable(null);
        bare.setClusterAddrTable(null);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(bare);
        when(adminExt.fetchAllTopicList()).thenReturn(topicList());

        RocketMQDashboardProvider provider = newProvider(adminExt);

        DashboardDataVO dashboard = provider.getDashboardData();

        // A partial NameServer payload must not throw and zero the page.
        assertThat(dashboard.getStats()).isNotNull();
        assertThat(dashboard.getClusters()).isEmpty();
    }

    @Test
    void dashboardShouldCountTopicsFromOrphanBrokerWithoutNpe() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        // broker-a is a cluster member; broker-orphan appears in the broker table but in no
        // clusterAddrTable set, so its cluster name resolves to null (registration race).
        ClusterInfo info = new ClusterInfo();
        HashMap<String, BrokerData> brokerAddrTable = new HashMap<>();
        brokerAddrTable.put("broker-a", brokerData("broker-a", "10.0.0.11:10911"));
        brokerAddrTable.put("broker-orphan", brokerData("broker-orphan", "10.0.0.13:10911"));
        info.setBrokerAddrTable(brokerAddrTable);
        HashMap<String, Set<String>> clusterAddrTable = new HashMap<>();
        clusterAddrTable.put("DefaultCluster", Set.of("broker-a"));
        info.setClusterAddrTable(clusterAddrTable);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(info);
        when(adminExt.getAllTopicConfig("10.0.0.11:10911", 5000))
                .thenReturn(topicConfig("order-topic"));
        when(adminExt.getAllTopicConfig("10.0.0.13:10911", 5000))
                .thenReturn(topicConfig("orphan-topic"));
        when(adminExt.getAllSubscriptionGroup("10.0.0.11:10911", 5000))
                .thenReturn(subscriptionGroups());
        when(adminExt.getAllSubscriptionGroup("10.0.0.13:10911", 5000))
                .thenReturn(subscriptionGroups());
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911")).thenReturn(runtimeStats());
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.13:10911")).thenReturn(runtimeStats());

        DashboardDataVO dashboard = newProvider(adminExt).getDashboardData();

        // Topics from the orphan broker are counted globally without crashing.
        assertThat(dashboard.getStats().getTotalTopics()).isEqualTo(2);
    }

    @Test
    void dashboardShouldRejectAnUnavailableTopologyInsteadOfReturningAnEmptyOverview() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(null);

        RocketMQDashboardProvider provider = newProvider(adminExt);

        assertThatThrownBy(provider::getDashboardData)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(502))
                .hasMessageContaining("Failed to collect dashboard data");
    }

    @Test
    void dashboardShouldRejectAdminFailuresInsteadOfReturningAnEmptyOverview() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        when(adminExt.examineBrokerClusterInfo()).thenThrow(new IllegalStateException("access denied"));

        RocketMQDashboardProvider provider = newProvider(adminExt);

        assertThatThrownBy(provider::getDashboardData)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(502))
                .hasMessageContaining("access denied");
    }

    @Test
    void dashboardShouldSkipBrokerWithoutAddressTable() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        ClusterInfo info = new ClusterInfo();
        HashMap<String, BrokerData> brokerAddrTable = new HashMap<>();
        brokerAddrTable.put("broker-no-addr", new BrokerData("DefaultCluster", "broker-no-addr", null));
        info.setBrokerAddrTable(brokerAddrTable);
        HashMap<String, Set<String>> clusterAddrTable = new HashMap<>();
        clusterAddrTable.put("DefaultCluster", Set.of("broker-no-addr"));
        info.setClusterAddrTable(clusterAddrTable);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(info);
        when(adminExt.fetchAllTopicList()).thenReturn(topicList());

        RocketMQDashboardProvider provider = newProvider(adminExt);

        DashboardDataVO dashboard = provider.getDashboardData();

        // A broker without an address table is skipped instead of throwing.
        assertThat(dashboard.getStats()).isNotNull();
        assertThat(dashboard.getClusters()).hasSize(1);
        assertThat(dashboard.getClusters().get(0).getBrokers()).isZero();
        assertThat(dashboard.getClusters().get(0).getStatus()).isEqualTo(ClusterStatus.warning);
    }

    @Test
    void dashboardShouldNotFailWhenBrokerAddrTableIsNull() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        ClusterInfo info = new ClusterInfo();
        HashMap<String, Set<String>> clusterAddrTable = new HashMap<>();
        clusterAddrTable.put("cluster-1", new HashSet<>(List.of("broker-a")));
        info.setClusterAddrTable(clusterAddrTable);
        // brokerAddrTable intentionally left null
        when(adminExt.examineBrokerClusterInfo()).thenReturn(info);
        when(adminExt.fetchAllTopicList()).thenReturn(topicList());

        RocketMQDashboardProvider provider = newProvider(adminExt);

        DashboardDataVO dashboard = provider.getDashboardData();

        // The cluster is still surfaced (name, status) but no broker statistics exist.
        assertThat(dashboard.getClusters()).hasSize(1);
        assertThat(dashboard.getClusters().get(0).getBrokers()).isZero();
        assertThat(dashboard.getStats().getTotalClusters()).isEqualTo(1);
        assertThat(dashboard.getStats().getTotalBrokers()).isZero();
        assertThat(dashboard.getStats().getHealthyClusters()).isZero();
    }

    @Test
    void dashboardShouldUseSelectedDirectInstanceAndReportDirectType() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        RuntimeAdminClientResolver resolver = mock(RuntimeAdminClientResolver.class);
        InstanceVO instance = InstanceVO.builder()
                .type(InstanceType.DIRECT)
                .endpoint("namesrv-direct:9876")
                .build();
        instance.setId(1L);
        when(resolver.resolveInstance("instance-direct")).thenReturn(instance);
        when(resolver.execute(eq(instance), any())).thenAnswer(invocation ->
                invocation.<MqAdminExtFactory.AdminAction<DashboardDataVO>>getArgument(1).apply(adminExt));
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        when(adminExt.fetchAllTopicList()).thenReturn(topicList());
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911")).thenReturn(runtimeStats());

        DashboardDataVO dashboard = newProvider(adminExt, resolver).getDashboardData("instance-direct");

        assertThat(dashboard.getClusters()).hasSize(1);
        assertThat(dashboard.getClusters().get(0).getType()).isEqualTo(ClusterType.V4_DIRECT);
        assertThat(dashboard.getClusters().get(0).getProxies()).isZero();
        assertThat(dashboard.getStats().getTotalProxies()).isZero();
        assertThat(dashboard.getStats().getTotalNameServers()).isEqualTo(1);
        verify(resolver).execute(eq(instance), any());
    }

    @Test
    void dashboardShouldReportSelectedProxyLocalInstanceType() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        RuntimeAdminClientResolver resolver = mock(RuntimeAdminClientResolver.class);
        InstanceVO instance = InstanceVO.builder()
                .type(InstanceType.PROXY_LOCAL)
                .endpoint("local-proxy:8080")
                .build();
        instance.setId(1L);
        when(resolver.resolveInstance("instance-local")).thenReturn(instance);
        when(resolver.execute(eq(instance), any())).thenAnswer(invocation ->
                invocation.<MqAdminExtFactory.AdminAction<DashboardDataVO>>getArgument(1).apply(adminExt));
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        when(adminExt.fetchAllTopicList()).thenReturn(topicList());
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911")).thenReturn(runtimeStats());

        DashboardDataVO dashboard = newProvider(adminExt, resolver).getDashboardData("instance-local");

        assertThat(dashboard.getClusters()).singleElement()
                .extracting(cluster -> cluster.getType())
                .isEqualTo(ClusterType.V5_PROXY_LOCAL);
        assertThat(dashboard.getClusters().get(0).getProxies()).isNull();
        assertThat(dashboard.getStats().getTotalProxies()).isNull();
        assertThat(dashboard.getStats().getTotalNameServers()).isNull();
    }

    @Test
    void dashboardShouldCountConfiguredDirectNameServerEndpoints() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        RuntimeAdminClientResolver resolver = mock(RuntimeAdminClientResolver.class);
        InstanceVO instance = InstanceVO.builder()
                .type(InstanceType.DIRECT)
                .endpoint(" ns-a:9876 ; ns-b:9876,ns-a:9876 ;; ")
                .build();
        instance.setId(1L);
        when(resolver.resolveInstance("instance-direct")).thenReturn(instance);
        when(resolver.execute(eq(instance), any())).thenAnswer(invocation ->
                invocation.<MqAdminExtFactory.AdminAction<DashboardDataVO>>getArgument(1).apply(adminExt));
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        when(adminExt.fetchAllTopicList()).thenReturn(topicList());
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911")).thenReturn(runtimeStats());

        DashboardDataVO dashboard = newProvider(adminExt, resolver).getDashboardData("instance-direct");

        assertThat(dashboard.getStats().getTotalNameServers()).isEqualTo(2);
    }

    @Test
    void dashboardShouldReportUnconfiguredLegacyTopologyAsUnavailable() {
        RocketMQProperties properties = new RocketMQProperties();
        properties.setNamesrvAddr(" ");
        RocketMQDashboardProvider provider = new RocketMQDashboardProvider(
                mock(MqAdminExtFactory.class), properties, mock(RuntimeAdminClientResolver.class));

        DashboardDataVO dashboard = provider.getDashboardData();

        assertThat(dashboard.getStats().getTotalClusters()).isZero();
        assertThat(dashboard.getStats().getTotalProxies()).isNull();
        assertThat(dashboard.getStats().getTotalNameServers()).isNull();
        assertThat(dashboard.getClusters()).isEmpty();
    }

    @Test
    void dashboardShouldMarkClusterWarningWhenBrokerRuntimeStatsFail() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        when(adminExt.fetchAllTopicList()).thenReturn(topicList());
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911"))
                .thenThrow(new RuntimeException("broker unavailable"));

        DashboardDataVO dashboard = newProvider(adminExt).getDashboardData();

        assertThat(dashboard.getClusters()).singleElement()
                .extracting(cluster -> cluster.getStatus())
                .isEqualTo(ClusterStatus.warning);
        assertThat(dashboard.getStats().getTotalClusters()).isEqualTo(1);
        assertThat(dashboard.getStats().getHealthyClusters()).isZero();
    }

    @Test
    void dashboardShouldKeepPerClusterTpsAboveIntegerRange() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        when(adminExt.fetchAllTopicList()).thenReturn(topicList());
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911"))
                .thenReturn(runtimeStats("3000000000.0", "4000000000.0"));

        DashboardDataVO dashboard = newProvider(adminExt).getDashboardData();

        assertThat(dashboard.getClusters()).singleElement().satisfies(cluster -> {
            assertThat(cluster.getTpsIn()).isEqualTo(3_000_000_000L);
            assertThat(cluster.getTpsOut()).isEqualTo(4_000_000_000L);
        });
    }

    @Test
    void dashboardShouldReportMessagesProducedSinceTodayMorning() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfoWithTwoMasters());
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911"))
                .thenReturn(runtimeStats("2.0", "5.0", "1000", "1250"));
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.12:10911"))
                .thenReturn(runtimeStats("2.0", "5.0", "400", "475"));

        DashboardDataVO dashboard = newProvider(adminExt).getDashboardData();

        assertThat(dashboard.getStats().getTotalMessagesToday()).isEqualTo(325L);
    }

    @Test
    void dashboardShouldNotReportNegativeCountWhenBrokerCounterMovesBackwards() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911"))
                .thenReturn(runtimeStats("2.0", "5.0", "1000", "50"));

        DashboardDataVO dashboard = newProvider(adminExt).getDashboardData();

        assertThat(dashboard.getStats().getTotalMessagesToday()).isZero();
    }

    @Test
    void dashboardShouldIgnoreMalformedTodayMessageCounters() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfoWithTwoMasters());
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911"))
                .thenReturn(runtimeStats("2.0", "5.0", "invalid", "1250"));
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.12:10911"))
                .thenReturn(runtimeStats("2.0", "5.0", "400", "invalid"));

        DashboardDataVO dashboard = newProvider(adminExt).getDashboardData();

        assertThat(dashboard.getStats().getTotalMessagesToday()).isZero();
    }

    @Test
    void dashboardShouldTolerateMissingTopicListAndClusterMembership() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        ClusterInfo info = new ClusterInfo();
        info.setBrokerAddrTable(new HashMap<>());
        HashMap<String, Set<String>> clusterAddrTable = new HashMap<>();
        clusterAddrTable.put("cluster-without-members", null);
        info.setClusterAddrTable(clusterAddrTable);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(info);
        when(adminExt.fetchAllTopicList()).thenReturn(null);

        DashboardDataVO dashboard = newProvider(adminExt).getDashboardData();

        assertThat(dashboard.getStats().getTotalTopics()).isZero();
        assertThat(dashboard.getClusters()).singleElement().satisfies(cluster -> {
            assertThat(cluster.getName()).isEqualTo("cluster-without-members");
            assertThat(cluster.getBrokers()).isZero();
            assertThat(cluster.getStatus()).isEqualTo(ClusterStatus.warning);
        });
    }

    private RocketMQDashboardProvider newProvider(DefaultMQAdminExt adminExt) {
        return newProvider(adminExt, mock(RuntimeAdminClientResolver.class));
    }

    private RocketMQDashboardProvider newProvider(DefaultMQAdminExt adminExt, RuntimeAdminClientResolver resolver) {
        MqAdminExtFactory adminFactory = mock(MqAdminExtFactory.class);
        when(adminFactory.execute(anyString(), any(), any())).thenAnswer(invocation ->
                invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(2).apply(adminExt));
        RocketMQProperties properties = new RocketMQProperties();
        properties.setNamesrvAddr("10.0.0.1:9876");
        return new RocketMQDashboardProvider(adminFactory, properties, resolver);
    }
    private ClusterInfo clusterInfo() {
        ClusterInfo info = new ClusterInfo();
        HashMap<Long, String> addrs = new HashMap<>();
        addrs.put(0L, "10.0.0.11:10911");

        HashMap<String, BrokerData> brokerAddrTable = new HashMap<>();
        brokerAddrTable.put("broker-a", new BrokerData("DefaultCluster", "broker-a", new HashMap<>(addrs)));
        info.setBrokerAddrTable(brokerAddrTable);

        HashMap<String, Set<String>> clusterAddrTable = new HashMap<>();
        clusterAddrTable.put("DefaultCluster", Set.of("broker-a"));
        info.setClusterAddrTable(clusterAddrTable);
        return info;
    }

    private ClusterInfo clusterInfoWithTwoMasters() {
        ClusterInfo info = new ClusterInfo();
        HashMap<String, BrokerData> brokerAddrTable = new HashMap<>();
        brokerAddrTable.put("broker-a", brokerData("broker-a", "10.0.0.11:10911"));
        brokerAddrTable.put("broker-b", brokerData("broker-b", "10.0.0.12:10911"));
        info.setBrokerAddrTable(brokerAddrTable);

        HashMap<String, Set<String>> clusterAddrTable = new HashMap<>();
        clusterAddrTable.put("DefaultCluster", Set.of("broker-a", "broker-b"));
        info.setClusterAddrTable(clusterAddrTable);
        return info;
    }

    private BrokerData brokerData(String name, String address) {
        HashMap<Long, String> addresses = new HashMap<>();
        addresses.put(0L, address);
        return new BrokerData("DefaultCluster", name, addresses);
    }

    private TopicList topicList() {
        TopicList topicList = new TopicList();
        topicList.setTopicList(Set.of("order-topic"));
        return topicList;
    }

    private TopicConfigSerializeWrapper topicConfig(String... names) {
        TopicConfigSerializeWrapper wrapper = new TopicConfigSerializeWrapper();
        ConcurrentHashMap<String, TopicConfig> configs = new ConcurrentHashMap<>();
        for (String name : names) {
            configs.put(name, new TopicConfig(name));
        }
        wrapper.setTopicConfigTable(configs);
        return wrapper;
    }

    private SubscriptionGroupWrapper subscriptionGroups(String... names) {
        SubscriptionGroupWrapper wrapper = new SubscriptionGroupWrapper();
        ConcurrentHashMap<String, SubscriptionGroupConfig> groups = new ConcurrentHashMap<>();
        for (String name : names) {
            groups.put(name, new SubscriptionGroupConfig());
        }
        wrapper.setSubscriptionGroupTable(groups);
        return wrapper;
    }

    private KVTable runtimeStats() {
        return runtimeStats("2.0", "5.0");
    }

    private KVTable runtimeStats(String putTps, String getTransferredTps) {
        return runtimeStats(putTps, getTransferredTps, "42", "84");
    }

    private KVTable runtimeStats(String putTps, String getTransferredTps,
                                 String todayMorning, String todayNow) {
        HashMap<String, String> table = new HashMap<>();
        table.put("brokerVersionDesc", "  V5_3_3  ");
        table.put("putTps", "1.0 " + putTps + " 3.0");
        table.put("getTransferredTps", "4.0 " + getTransferredTps + " 6.0");
        table.put("msgPutTotalTodayMorning", todayMorning);
        table.put("msgPutTotalTodayNow", todayNow);

        KVTable kvTable = new KVTable();
        kvTable.setTable(table);
        return kvTable;
    }
}
