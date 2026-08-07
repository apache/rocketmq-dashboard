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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.domain.enums.ClusterType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.ops.dashboard.DashboardDataVO;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RocketMQDashboardProviderTest {

    @Test
    void dashboardClusterOverviewShouldUseBrokerVersionDesc() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        when(adminExt.fetchAllTopicList()).thenReturn(topicList());
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911")).thenReturn(runtimeStats());

        RocketMQDashboardProvider provider = provider(adminExt);

        DashboardDataVO dashboard = provider.getDashboardData();

        assertThat(dashboard.getClusters()).hasSize(1);
        assertThat(dashboard.getClusters().get(0).getVersion()).isEqualTo("V5_3_3");
    }

    @Test
    void dashboardShouldSurviveNullTopologyTables() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        ClusterInfo bare = new ClusterInfo();
        bare.setBrokerAddrTable(null);
        bare.setClusterAddrTable(null);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(bare);
        when(adminExt.fetchAllTopicList()).thenReturn(topicList());

        RocketMQDashboardProvider provider = provider(adminExt);

        DashboardDataVO dashboard = provider.getDashboardData();

        // A partial NameServer payload must not throw and zero the page.
        assertThat(dashboard.getStats()).isNotNull();
        assertThat(dashboard.getClusters()).isEmpty();
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

        RocketMQDashboardProvider provider = provider(adminExt);

        DashboardDataVO dashboard = provider.getDashboardData();

        // A broker without an address table is skipped instead of throwing.
        assertThat(dashboard.getStats()).isNotNull();
        assertThat(dashboard.getClusters()).hasSize(1);
        assertThat(dashboard.getClusters().get(0).getBrokers()).isZero();
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

        RocketMQDashboardProvider provider = provider(adminExt);

        DashboardDataVO dashboard = provider.getDashboardData();

        // The cluster is still surfaced (name, status) but no broker statistics exist.
        assertThat(dashboard.getClusters()).hasSize(1);
        assertThat(dashboard.getClusters().get(0).getBrokers()).isZero();
        assertThat(dashboard.getStats().getTotalClusters()).isEqualTo(1);
        assertThat(dashboard.getStats().getTotalBrokers()).isZero();
    }

    @Test
    void dashboardShouldUseSelectedDirectInstanceAndReportDirectType() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        RuntimeAdminClientResolver resolver = mock(RuntimeAdminClientResolver.class);
        InstanceVO instance = InstanceVO.builder()
                .type(InstanceType.DIRECT)
                .endpoint("namesrv-direct:9876")
                .build();
        instance.setId("instance-direct");
        when(resolver.resolveInstance("instance-direct")).thenReturn(instance);
        when(resolver.execute(eq(instance), any())).thenAnswer(invocation -> {
            MqAdminExtFactory.AdminAction<DashboardDataVO> action = invocation.getArgument(1);
            return action.apply(adminExt);
        });
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        when(adminExt.fetchAllTopicList()).thenReturn(topicList());
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911")).thenReturn(runtimeStats());

        DashboardDataVO dashboard = new RocketMQDashboardProvider(null, resolver)
                .getDashboardData("instance-direct");

        assertThat(dashboard.getClusters()).hasSize(1);
        assertThat(dashboard.getClusters().get(0).getType()).isEqualTo(ClusterType.V4_DIRECT);
        verify(resolver).execute(eq(instance), any());
    }

    private RocketMQDashboardProvider provider(DefaultMQAdminExt adminExt) {
        return new RocketMQDashboardProvider(adminExt, mock(RuntimeAdminClientResolver.class));
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

    private TopicList topicList() {
        TopicList topicList = new TopicList();
        topicList.setTopicList(Set.of("order-topic"));
        return topicList;
    }

    private KVTable runtimeStats() {
        HashMap<String, String> table = new HashMap<>();
        table.put("brokerVersionDesc", "  V5_3_3  ");
        table.put("putTps", "1.0 2.0 3.0");
        table.put("getTransferedTps", "4.0 5.0 6.0");
        table.put("msgPutTotalTodayMorning", "42");

        KVTable kvTable = new KVTable();
        kvTable.setTable(table);
        return kvTable;
    }
}
