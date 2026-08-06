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
import java.util.Set;

import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.ops.dashboard.DashboardDataVO;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RocketMQDashboardProviderTest {

    @Test
    void dashboardClusterOverviewShouldUseBrokerVersionDesc() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        when(adminExt.fetchAllTopicList()).thenReturn(topicList());
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911")).thenReturn(runtimeStats());

        RocketMQDashboardProvider provider = new RocketMQDashboardProvider(adminExt);

        DashboardDataVO dashboard = provider.getDashboardData();

        assertThat(dashboard.getClusters()).hasSize(1);
        assertThat(dashboard.getClusters().get(0).getVersion()).isEqualTo("V5_3_3");
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
        table.put("getTransferredTps", "4.0 5.0 6.0");
        table.put("msgPutTotalTodayMorning", "42");

        KVTable kvTable = new KVTable();
        kvTable.setTable(table);
        return kvTable;
    }
}
