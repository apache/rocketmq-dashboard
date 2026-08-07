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
package org.apache.rocketmq.studio.cluster.broker;

import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.common.domain.enums.BrokerStatus;
import org.apache.rocketmq.studio.common.domain.enums.ClusterType;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealClusterProviderTest {

    @Mock
    private MqAdminExtFactory adminFactory;

    private final MqAdminProperties properties = new MqAdminProperties();

    private RealClusterProvider provider;

    @BeforeEach
    void setUp() {
        provider = new RealClusterProvider(adminFactory, properties);
    }

    @SuppressWarnings("unchecked")
    private void stubClusterInfo(String namesrvAddr, ClusterInfo info) throws Exception {
        MQAdminExt admin = org.mockito.Mockito.mock(MQAdminExt.class);
        when(admin.examineBrokerClusterInfo()).thenReturn(info);
        when(adminFactory.execute(eq(namesrvAddr), isNull(), any())).thenAnswer(invocation -> {
            MqAdminExtFactory.AdminAction<Object> action = invocation.getArgument(2);
            return action.apply(admin);
        });
    }

    private ClusterInfo sampleClusterInfo() {
        ClusterInfo info = new ClusterInfo();
        HashMap<Long, String> addrs = new HashMap<>();
        addrs.put(0L, "10.0.0.11:10911");
        addrs.put(1L, "10.0.0.12:10911");
        HashMap<String, BrokerData> brokerAddrTable = new HashMap<>();
        brokerAddrTable.put("broker-b",
                new BrokerData("DefaultCluster", "broker-b", new HashMap<>(addrs)));
        brokerAddrTable.put("broker-a",
                new BrokerData("DefaultCluster", "broker-a", new HashMap<>(addrs)));
        info.setBrokerAddrTable(brokerAddrTable);
        HashMap<String, Set<String>> clusterAddrTable = new HashMap<>();
        clusterAddrTable.put("DefaultCluster", Set.of("broker-a", "broker-b"));
        info.setClusterAddrTable(clusterAddrTable);
        return info;
    }

    @Test
    void describeClusterShouldMapLiveTopology() throws Exception {
        stubClusterInfo("10.0.0.1:9876;10.0.0.2:9876", sampleClusterInfo());

        ClusterVO cluster = provider.describeCluster("10.0.0.1:9876;10.0.0.2:9876");

        assertThat(cluster.getName()).isEqualTo("DefaultCluster");
        assertThat(cluster.getType()).isEqualTo(ClusterType.V4_DIRECT);
        assertThat(cluster.getEndpoint()).isEqualTo("10.0.0.1:9876;10.0.0.2:9876");
        assertThat(cluster.getNameServers()).extracting("addr")
                .containsExactly("10.0.0.1:9876", "10.0.0.2:9876");
        assertThat(cluster.getBrokers()).hasSize(2);
        assertThat(cluster.getBrokers().get(0).getName()).isEqualTo("broker-a");
        assertThat(cluster.getBrokers().get(0).getAddr()).isEqualTo("10.0.0.11:10911");
        assertThat(cluster.getBrokers().get(0).getStatus()).isEqualTo(BrokerStatus.running);
    }

    @Test
    void discoverClustersShouldReturnEmptyWhenNoNamesrvConfigured() {
        properties.setNamesrvAddr("  ");

        assertThat(provider.discoverClusters()).isEmpty();
    }

    @Test
    void discoverClustersShouldUseConfiguredNamesrv() throws Exception {
        properties.setNamesrvAddr("10.0.0.1:9876");
        stubClusterInfo("10.0.0.1:9876", sampleClusterInfo());

        List<ClusterVO> clusters = provider.discoverClusters();

        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0).getBrokers()).hasSize(2);
    }

    @Test
    void discoverClustersShouldGroupBrokersByCluster() throws Exception {
        ClusterInfo info = new ClusterInfo();
        HashMap<Long, String> addrs = new HashMap<>();
        addrs.put(0L, "10.0.0.11:10911");
        HashMap<String, BrokerData> brokerAddrTable = new HashMap<>();
        brokerAddrTable.put("broker-a1",
                new BrokerData("ClusterA", "broker-a1", new HashMap<>(addrs)));
        brokerAddrTable.put("broker-b1",
                new BrokerData("ClusterB", "broker-b1", new HashMap<>(addrs)));
        info.setBrokerAddrTable(brokerAddrTable);
        HashMap<String, Set<String>> clusterAddrTable = new HashMap<>();
        clusterAddrTable.put("ClusterA", Set.of("broker-a1"));
        clusterAddrTable.put("ClusterB", Set.of("broker-b1"));
        info.setClusterAddrTable(clusterAddrTable);
        properties.setNamesrvAddr("10.0.0.1:9876");
        stubClusterInfo("10.0.0.1:9876", info);

        List<ClusterVO> clusters = provider.discoverClusters();

        assertThat(clusters).hasSize(2);
        ClusterVO clusterA = clusters.stream()
                .filter(cluster -> cluster.getId().equals("ClusterA")).findFirst().orElseThrow();
        ClusterVO clusterB = clusters.stream()
                .filter(cluster -> cluster.getId().equals("ClusterB")).findFirst().orElseThrow();
        assertThat(clusterA.getBrokers()).extracting(BrokerVO::getName)
                .containsExactly("broker-a1");
        assertThat(clusterB.getBrokers()).extracting(BrokerVO::getName)
                .containsExactly("broker-b1");
    }
}
