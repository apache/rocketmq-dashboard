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

import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.cluster.broker.ClusterVO;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.MqAdminProperties;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RocketMQClusterProviderTest {

    @Test
    void discoverClustersShouldParseRuntimeTpsWithExtraWhitespace() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        RocketMQClusterProvider provider = newProvider(adminExt);

        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911")).thenReturn(runtimeStats());

        List<ClusterVO> clusters = provider.discoverClusters();

        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0).getBrokers()).hasSize(1);
        assertThat(clusters.get(0).getBrokers().get(0).getTpsIn()).isEqualTo(10);
        assertThat(clusters.get(0).getBrokers().get(0).getTpsOut()).isEqualTo(30);
        assertThat(clusters.get(0).getBrokers().get(0).isRuntimeStatsAvailable()).isTrue();
        assertThat(clusters.get(0).getStatus()).isEqualTo(ClusterStatus.healthy);
    }

    @Test
    void discoverClustersShouldPopulateSafeDefaults() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        RocketMQClusterProvider provider = newProvider(adminExt);

        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());

        List<ClusterVO> clusters = provider.discoverClusters();

        assertThat(clusters).hasSize(1);
        ClusterVO cluster = clusters.get(0);
        assertThat(cluster.getName()).isEqualTo("DefaultCluster");
        // A concrete type is required by AI tool projections (rmq.cluster.list) and must not be null.
        assertThat(cluster.getType()).isNotNull();
        // Real cluster has no proxies configured - must never be null for the web UI
        assertThat(cluster.getProxies()).isNotNull().isEmpty();
        assertThat(cluster.getTpsHistory()).isNotNull().isEmpty();
        assertThat(cluster.getNameServers()).hasSize(1);
        assertThat(cluster.getNameServers().get(0).getAddr()).isEqualTo("10.0.0.1:9876");
    }

    @Test
    void refreshClusterDetailShouldPopulateSafeDefaults() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        RocketMQClusterProvider provider = newProvider(adminExt);

        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());

        ClusterVO cluster = provider.refreshClusterDetail("DefaultCluster");

        assertThat(cluster).isNotNull();
        assertThat(cluster.getId()).isEqualTo("DefaultCluster");
        assertThat(cluster.getProxies()).isNotNull().isEmpty();
        assertThat(cluster.getTpsHistory()).isNotNull().isEmpty();
    }

    @Test
    void refreshClusterDetailShouldSurfaceNameServerFailuresInsteadOfReturningNull() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        RocketMQClusterProvider provider = newProvider(adminExt);
        when(adminExt.examineBrokerClusterInfo()).thenThrow(new IllegalStateException("NameServer unavailable"));

        assertThatThrownBy(() -> provider.refreshClusterDetail("DefaultCluster"))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(502);
                    assertThat(error.getMessage()).contains("NameServer unavailable");
                });
    }

    @Test
    void discoverClustersMarksWarningWhenBrokerRuntimeStatsAreUnavailable() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        RocketMQClusterProvider provider = newProvider(adminExt);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911"))
                .thenThrow(new IllegalStateException("broker unavailable"));

        ClusterVO cluster = provider.discoverClusters().get(0);

        assertThat(cluster.getStatus()).isEqualTo(ClusterStatus.warning);
        assertThat(cluster.getBrokers()).singleElement()
                .extracting(broker -> broker.isRuntimeStatsAvailable())
                .isEqualTo(false);
    }

    @Test
    void discoverClustersShouldKeepOneMinuteTpsAboveIntegerRange() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        RocketMQClusterProvider provider = newProvider(adminExt);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911"))
                .thenReturn(runtimeStats("1.0 3000000000.0 3.0", "4.0 4000000000.0 6.0"));

        List<ClusterVO> clusters = provider.discoverClusters();

        assertThat(clusters).singleElement().satisfies(cluster -> {
            assertThat(cluster.getBrokers()).singleElement().satisfies(broker -> {
                assertThat(broker.getTpsIn()).isEqualTo(3_000_000_000L);
                assertThat(broker.getTpsOut()).isEqualTo(4_000_000_000L);
            });
        });
    }

    @Test
    void discoverClustersShouldRejectNonFiniteRuntimeMetrics() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        RocketMQClusterProvider provider = newProvider(adminExt);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        KVTable runtime = runtimeStats("1 NaN 3", "1 Infinity 3");
        runtime.getTable().put("commitLogDiskRatio", "NaN");
        when(adminExt.fetchBrokerRuntimeStats("10.0.0.11:10911")).thenReturn(runtime);

        ClusterVO cluster = provider.discoverClusters().get(0);

        assertThat(cluster.getBrokers()).singleElement().satisfies(broker -> {
            assertThat(broker.getTpsIn()).isZero();
            assertThat(broker.getTpsOut()).isZero();
            assertThat(broker.getDiskUsage()).isZero();
        });
    }

    @Test
    void discoverClustersShouldUseFirstNonBlankBrokerAddress() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        RocketMQClusterProvider provider = newProvider(adminExt);
        ClusterInfo info = clusterInfo();
        LinkedHashMap<Long, String> addrs = new LinkedHashMap<>();
        addrs.put(0L, "  ");
        addrs.put(1L, null);
        addrs.put(2L, " 10.0.0.12:10911 ");
        info.getBrokerAddrTable().put(
                "broker-a", new BrokerData("DefaultCluster", "broker-a", addrs));
        when(adminExt.examineBrokerClusterInfo()).thenReturn(info);

        ClusterVO cluster = provider.discoverClusters().get(0);

        assertThat(cluster.getBrokers()).singleElement()
                .extracting(broker -> broker.getAddr())
                .isEqualTo("10.0.0.12:10911");
    }

    @Test
    void discoverClustersShouldDiscoverProxiesViaHeartbeatSyncerTest() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        RocketMQClusterProvider provider = newProvider(adminExt);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());

        Connection first = new Connection();
        first.setClientAddr("10.0.3.5:54321");
        Connection second = new Connection();
        second.setClientAddr("10.0.3.6:12345");
        ConsumerConnection consumerConnection = new ConsumerConnection();
        consumerConnection.setConnectionSet(new java.util.HashSet<>(java.util.List.of(first, second)));
        when(adminExt.examineConsumerConnectionInfo("CID_DefaultHeartBeatSyncerTopic"))
                .thenReturn(consumerConnection);

        List<ClusterVO> clusters = provider.discoverClusters();

        assertThat(clusters).hasSize(1);
        assertThat(clusters.get(0).getProxies()).hasSize(2);
        assertThat(clusters.get(0).getProxies().get(0).getAddr()).isEqualTo("10.0.3.5:8080");
        assertThat(clusters.get(0).getProxies().get(0).getGrpcPort()).isEqualTo(8081);
        assertThat(clusters.get(0).getProxies().get(0).getStatus()).isEqualTo(ClusterStatus.healthy);
    }

    @Test
    void discoverClustersShouldIgnoreNullHeartbeatConnectionsTest() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        RocketMQClusterProvider provider = newProvider(adminExt);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());
        Connection valid = new Connection();
        valid.setClientAddr("10.0.3.5:54321");
        java.util.HashSet<Connection> connections = new java.util.HashSet<>();
        connections.add(null);
        connections.add(valid);
        ConsumerConnection consumerConnection = new ConsumerConnection();
        consumerConnection.setConnectionSet(connections);
        when(adminExt.examineConsumerConnectionInfo("CID_DefaultHeartBeatSyncerTopic"))
                .thenReturn(consumerConnection);

        List<ClusterVO> clusters = provider.discoverClusters();

        assertThat(clusters.get(0).getProxies()).singleElement()
                .extracting(proxy -> proxy.getAddr())
                .isEqualTo("10.0.3.5:8080");
    }

    private RocketMQClusterProvider newProvider(DefaultMQAdminExt adminExt) {
        MqAdminExtFactory adminFactory = mock(MqAdminExtFactory.class);
        when(adminFactory.execute(anyString(), any(), any())).thenAnswer(invocation ->
                invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(2).apply(adminExt));
        RocketMQProperties properties = new RocketMQProperties();
        properties.setNamesrvAddr("10.0.0.1:9876");
        return new RocketMQClusterProvider(adminFactory, properties, mock(RuntimeAdminClientResolver.class));
    }

    @Test
    void discoverClustersShouldUseSelectedInstanceAdminCredential() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        MqAdminExtFactory adminFactory = mock(MqAdminExtFactory.class);
        RocketMQClusterProvider provider = newAuthenticatedInstanceProvider(adminFactory, adminExt);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());

        List<ClusterVO> clusters = provider.discoverClusters("instance-a");

        assertThat(clusters).singleElement()
                .extracting(ClusterVO::getName)
                .isEqualTo("DefaultCluster");
        verify(adminFactory).execute(eq("10.0.0.2:9876"), isA(AclClientRPCHook.class),
                eq("cluster-admin"), any());
        verify(adminFactory, never()).execute(eq("10.0.0.2:9876"), isNull(), any());
    }

    @Test
    void refreshClusterDetailShouldUseSelectedInstanceAdminCredential() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        MqAdminExtFactory adminFactory = mock(MqAdminExtFactory.class);
        RocketMQClusterProvider provider = newAuthenticatedInstanceProvider(adminFactory, adminExt);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo());

        ClusterVO cluster = provider.refreshClusterDetail("DefaultCluster", "instance-a");

        assertThat(cluster).isNotNull();
        assertThat(cluster.getName()).isEqualTo("DefaultCluster");
        verify(adminFactory).execute(eq("10.0.0.2:9876"), isA(AclClientRPCHook.class),
                eq("cluster-admin"), any());
        verify(adminFactory, never()).execute(eq("10.0.0.2:9876"), isNull(), any());
    }

    private RocketMQClusterProvider newAuthenticatedInstanceProvider(MqAdminExtFactory adminFactory,
                                                                      DefaultMQAdminExt adminExt) {
        InstanceRepository instanceRepository = mock(InstanceRepository.class);
        InstanceVO instance = InstanceVO.builder()
                .name("Authenticated instance")
                .vendor(InstanceVendor.APACHE)
                .type(InstanceType.DIRECT)
                .endpoint("10.0.0.2:9876")
                .adminCredentialRef("cluster-admin")
                .build();
        instance.setId(1L);
        when(instanceRepository.findByIdentifier("instance-a")).thenReturn(Optional.of(instance));
        MqAdminProperties adminProperties = new MqAdminProperties();
        MqAdminProperties.Credential credential = new MqAdminProperties.Credential();
        credential.setAccessKey("admin-ak");
        credential.setSecretKey("admin-sk");
        adminProperties.getCredentials().put("cluster-admin", credential);
        RuntimeAdminClientResolver resolver =
                new RuntimeAdminClientResolver(instanceRepository, adminFactory, adminProperties,
                        org.mockito.Mockito.mock(org.apache.rocketmq.studio.cluster.broker.MqClientPool.class));
        when(adminFactory.execute(eq("10.0.0.2:9876"), any(), eq("cluster-admin"), any()))
                .thenAnswer(invocation -> invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(3)
                        .apply(adminExt));
        RocketMQProperties properties = new RocketMQProperties();
        properties.setNamesrvAddr("10.0.0.1:9876");
        return new RocketMQClusterProvider(adminFactory, properties, resolver);
    }

    @Test
    void discoverClustersShouldExposeNameServerFailures() throws Exception {
        DefaultMQAdminExt adminExt = mock(DefaultMQAdminExt.class);
        RocketMQClusterProvider provider = newProvider(adminExt);

        when(adminExt.examineBrokerClusterInfo()).thenThrow(new IllegalStateException("NameServer unavailable"));

        assertThatThrownBy(provider::discoverClusters)
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(502);
                    assertThat(error.getMessage()).contains("NameServer unavailable");
                });
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
        return runtimeStats("  12.7   10.0  9.0", "\\t34.2  30.0  29.0");
    }

    private KVTable runtimeStats(String putTps, String getTransferredTps) {
        KVTable table = new KVTable();
        HashMap<String, String> stats = new HashMap<>();
        stats.put("putTps", putTps);
        stats.put("getTransferredTps", getTransferredTps);
        table.setTable(stats);
        return table;
    }
}
