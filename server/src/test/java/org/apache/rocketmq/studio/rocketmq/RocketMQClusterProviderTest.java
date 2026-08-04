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

import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.cluster.broker.ClusterVO;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RocketMQClusterProviderTest {

    @Test
    void discoverClustersShouldParseRuntimeTpsWithExtraWhitespace() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        RocketMQProperties properties = new RocketMQProperties();
        properties.setNamesrvAddr("10.0.0.1:9876");
        RocketMQClusterProvider provider = new RocketMQClusterProvider(adminExt, properties);

        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911")).thenReturn(runtimeStats());

        List<ClusterVO> clusters = provider.discoverClusters();

        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0).getBrokers()).hasSize(1);
        assertThat(clusters.get(0).getBrokers().get(0).getTpsIn()).isEqualTo(12);
        assertThat(clusters.get(0).getBrokers().get(0).getTpsOut()).isEqualTo(34);
    }

    private ClusterInfo clusterInfo() {
        ClusterInfo clusterInfo = new ClusterInfo();
        HashMap<Long, String> addrs = new HashMap<>();
        addrs.put(0L, "10.0.0.11:10911");
        HashMap<String, BrokerData> brokerAddrTable = new HashMap<>();
        brokerAddrTable.put("broker-a", new BrokerData("DefaultCluster", "broker-a", addrs));
        clusterInfo.setBrokerAddrTable(brokerAddrTable);

        HashMap<String, Set<String>> clusterAddrTable = new HashMap<>();
        clusterAddrTable.put("DefaultCluster", Set.of("broker-a"));
        clusterInfo.setClusterAddrTable(clusterAddrTable);
        return clusterInfo;
    }

    private KVTable runtimeStats() {
        KVTable table = new KVTable();
        HashMap<String, String> stats = new HashMap<>();
        stats.put("putTps", "  12.7   10.0  9.0");
        stats.put("getTransferedTps", "\t34.2  30.0  29.0");
        table.setTable(stats);
        return table;
    }
}
