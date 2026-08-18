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
import org.apache.rocketmq.studio.common.exception.BusinessException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private ClusterInfo multiClusterInfo() {
        ClusterInfo info = sampleClusterInfo();
        HashMap<Long, String> addrs = new HashMap<>();
        addrs.put(0L, "10.0.0.21:10911");
        info.getBrokerAddrTable().put("broker-c",
                new BrokerData("AnalyticsCluster", "broker-c", addrs));
        info.getClusterAddrTable().put("AnalyticsCluster", Set.of("broker-c"));
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
    void describeClusterShouldDefaultAbsentRuntimeCollectionsToEmpty() throws Exception {
        stubClusterInfo("10.0.0.1:9876", sampleClusterInfo());

        ClusterVO cluster = provider.describeCluster("10.0.0.1:9876");

        // A live cluster without a proxy must still serialize non-null collections
        // so the web UI never dereferences null component lists.
        assertThat(cluster.getProxies()).isEmpty();
        assertThat(cluster.getTpsHistory()).isEmpty();
        assertThat(cluster.getConfig()).isNotNull();
    }

    @Test
    void discoverClustersShouldReturnEmptyWhenNoNamesrvConfigured() {
        properties.setNamesrvAddr("  ");

        assertThat(provider.discoverClusters()).isEmpty();
    }

    @Test
    void describeClustersShouldTolerateNullClusterInfo() throws Exception {
        stubClusterInfo("10.0.0.1:9876", null);

        assertThat(provider.describeClusters("10.0.0.1:9876")).isEmpty();
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
    void discoversAndRefreshesEachClusterWithOnlyItsOwnBrokers() throws Exception {
        properties.setNamesrvAddr("10.0.0.1:9876");
        stubClusterInfo("10.0.0.1:9876", multiClusterInfo());

        List<ClusterVO> clusters = provider.discoverClusters();

        assertThat(clusters).extracting(ClusterVO::getId)
                .containsExactly("AnalyticsCluster", "DefaultCluster");
        assertThat(clusters.get(0).getBrokers()).extracting(BrokerVO::getName)
                .containsExactly("broker-c");
        assertThat(clusters.get(1).getBrokers()).extracting(BrokerVO::getName)
                .containsExactly("broker-a", "broker-b");

        ClusterVO refreshed = provider.refreshClusterDetail("AnalyticsCluster");
        assertThat(refreshed.getId()).isEqualTo("AnalyticsCluster");
        assertThat(refreshed.getBrokers()).extracting(BrokerVO::getName).containsExactly("broker-c");
    }

    @Test
    void rejectsUnknownClusterRefreshes() throws Exception {
        properties.setNamesrvAddr("10.0.0.1:9876");
        stubClusterInfo("10.0.0.1:9876", sampleClusterInfo());

        assertThatThrownBy(() -> provider.refreshClusterDetail("missing"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cluster not found");
    }
}
