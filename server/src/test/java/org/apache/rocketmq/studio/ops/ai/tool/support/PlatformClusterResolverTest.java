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
package org.apache.rocketmq.studio.ops.ai.tool.support;

import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver.ManagedBroker;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver.ManagedCluster;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformClusterResolverTest {

    @Mock
    private InstanceRepository instanceRepository;

    @Mock
    private RuntimeAdminClientResolver runtimeAdminClientResolver;

    private final Map<String, MQAdminExt> admins = new HashMap<>();

    private PlatformClusterResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new PlatformClusterResolver(instanceRepository, runtimeAdminClientResolver);
        when(runtimeAdminClientResolver.execute(any(InstanceVO.class), any())).thenAnswer(invocation -> {
            InstanceVO instance = invocation.getArgument(0);
            MqAdminExtFactory.AdminAction<Object> action = invocation.getArgument(1);
            MQAdminExt admin = admins.get(instance.getName());
            if (admin == null) {
                throw new IllegalStateException("No admin for instance " + instance.getName());
            }
            return action.apply(admin);
        });
    }

    @Test
    void resolveShouldReturnFirstManagingInstanceTest() throws Exception {
        InstanceVO first = apacheInstance("instance-a", "ns-a:9876");
        InstanceVO second = apacheInstance("instance-b", "ns-b:9876");
        when(instanceRepository.findAll()).thenReturn(List.of(second, first));
        admins.put("instance-a", adminWith(clusterInfo(
                Map.of("rmq-a", Set.of("broker-a")),
                Map.of("broker-a", brokerData("rmq-a", "broker-a", 0L, "10.0.0.1:10911")))));
        admins.put("instance-b", adminWith(clusterInfo(
                Map.of("rmq-b", Set.of("broker-b")),
                Map.of("broker-b", brokerData("rmq-b", "broker-b", 0L, "10.0.0.2:10911")))));

        assertThat(resolver.resolveInstanceId("rmq-b")).isEqualTo("instance-b");
        assertThat(resolver.require("rmq-a").instanceId()).isEqualTo("instance-a");
        assertThat(resolver.require(" rmq-a ").clusterName()).isEqualTo("rmq-a");
    }

    @Test
    void requireShouldThrow404WhenClusterUnownedTest() throws Exception {
        when(instanceRepository.findAll())
                .thenReturn(List.of(apacheInstance("instance-a", "ns-a:9876")));
        admins.put("instance-a", adminWith(clusterInfo(
                Map.of("rmq-a", Set.of("broker-a")),
                Map.of("broker-a", brokerData("rmq-a", "broker-a", 0L, "10.0.0.1:10911")))));

        assertThatThrownBy(() -> resolver.require("rmq-missing"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cluster not found: rmq-missing")
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode())
                        .isEqualTo(404));
    }

    @Test
    void requireShouldRejectClusterNameManagedByMultipleInstancesTest() throws Exception {
        InstanceVO first = apacheInstance("instance-a", "ns-a:9876");
        InstanceVO second = apacheInstance("instance-b", "ns-b:9876");
        when(instanceRepository.findAll()).thenReturn(List.of(second, first));
        admins.put("instance-a", adminWith(clusterInfo(
                Map.of("DefaultCluster", Set.of("broker-a")),
                Map.of("broker-a", brokerData(
                        "DefaultCluster", "broker-a", 0L, "10.0.0.1:10911")))));
        admins.put("instance-b", adminWith(clusterInfo(
                Map.of("DefaultCluster", Set.of("broker-b")),
                Map.of("broker-b", brokerData(
                        "DefaultCluster", "broker-b", 0L, "10.0.0.2:10911")))));

        assertThatThrownBy(() -> resolver.require("DefaultCluster"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cluster name is ambiguous across instances: DefaultCluster (instance-a, instance-b)")
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode())
                        .isEqualTo(409));
    }

    @Test
    void requireShouldRejectBlankClusterNameTest() {
        assertThatThrownBy(() -> resolver.require(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("clusterName is required")
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode())
                        .isEqualTo(400));
    }

    @Test
    void scanShouldAggregateAndDedupeAcrossInstancesTest() throws Exception {
        InstanceVO first = apacheInstance("instance-a", "ns-a:9876");
        InstanceVO second = apacheInstance("instance-b", "ns-a:9876");
        when(instanceRepository.findAll()).thenReturn(List.of(second, first));
        ClusterInfo shared = clusterInfo(
                Map.of("shared-cluster", Set.of("broker-s")),
                Map.of("broker-s", brokerData("shared-cluster", "broker-s", 0L, "10.0.0.9:10911")));
        admins.put("instance-a", adminWith(shared));
        admins.put("instance-b", adminWith(clusterInfo(
                Map.of("shared-cluster", Set.of("broker-s"), "rmq-b", Set.of("broker-b")),
                Map.of("broker-s", brokerData("shared-cluster", "broker-s", 0L, "10.0.0.9:10911"),
                        "broker-b", brokerData("rmq-b", "broker-b", 0L, "10.0.0.2:10911")))));

        List<ManagedCluster> clusters = resolver.scan();

        assertThat(clusters)
                .extracting(ManagedCluster::clusterName, ManagedCluster::instanceId)
                .containsExactly(
                        tuple("shared-cluster", "instance-a"),
                        tuple("rmq-b", "instance-b"));
        ManagedCluster sharedCluster = clusters.stream()
                .filter(cluster -> "shared-cluster".equals(cluster.clusterName()))
                .findFirst().orElseThrow();
        assertThat(sharedCluster.nameServerAddrs()).containsExactly("ns-a:9876");
        assertThat(sharedCluster.brokers())
                .extracting(ManagedBroker::brokerName, ManagedBroker::brokerId,
                        ManagedBroker::address, ManagedBroker::master)
                .containsExactly(tuple("broker-s", 0L, "10.0.0.9:10911", true));
    }

    @Test
    void scanShouldSkipUnreachableInstancesTest() throws Exception {
        when(instanceRepository.findAll()).thenReturn(List.of(
                apacheInstance("instance-down", "ns-x:9876"),
                apacheInstance("instance-up", "ns-y:9876")));
        admins.put("instance-up", adminWith(clusterInfo(
                Map.of("rmq-up", Set.of("broker-u")),
                Map.of("broker-u", brokerData("rmq-up", "broker-u", 0L, "10.0.0.3:10911")))));

        assertThat(resolver.scan())
                .extracting(ManagedCluster::clusterName, ManagedCluster::instanceId)
                .containsExactly(tuple("rmq-up", "instance-up"));
    }

    @Test
    void manageableInstancesShouldFilterCloudAndEndpointlessTest() {
        InstanceVO cloud = InstanceVO.builder()
                .name("cloud-1").vendor(InstanceVendor.ALIYUN).endpoint("rmq.aliyuncs.com:8080").build();
        InstanceVO endpointless = InstanceVO.builder()
                .name("no-endpoint").vendor(InstanceVendor.APACHE).build();
        InstanceVO zulu = apacheInstance("zulu", "ns-z:9876");
        InstanceVO alpha = apacheInstance("alpha", "ns-a:9876");
        when(instanceRepository.findAll()).thenReturn(List.of(cloud, endpointless, zulu, alpha));

        assertThat(resolver.manageableInstances())
                .extracting(InstanceVO::getName)
                .containsExactly("alpha", "zulu");
    }

    @Test
    void scanWithVersionsShouldEnrichMastersAndDeriveStatusTest() throws Exception {
        when(instanceRepository.findAll())
                .thenReturn(List.of(apacheInstance("instance-a", "ns-a:9876")));
        MQAdminExt admin = adminWith(clusterInfo(
                Map.of("rmq-healthy", Set.of("broker-h"), "rmq-degraded", Set.of("broker-d")),
                Map.of("broker-h", brokerData("rmq-healthy", "broker-h", 0L, "10.0.0.1:10911"),
                        "broker-d", brokerData("rmq-degraded", "broker-d", 0L, "10.0.0.2:10911"))));
        admins.put("instance-a", admin);
        KVTable table = new KVTable();
        HashMap<String, String> stats = new HashMap<>();
        stats.put("brokerVersionDesc", "V5_5_0");
        table.setTable(stats);
        when(admin.fetchBrokerRuntimeStats("10.0.0.1:10911")).thenReturn(table);
        when(admin.fetchBrokerRuntimeStats("10.0.0.2:10911"))
                .thenThrow(new IllegalStateException("unreachable"));

        Map<String, ManagedCluster> byName = new HashMap<>();
        resolver.scanWithBrokerVersions().forEach(cluster -> byName.put(cluster.clusterName(), cluster));

        assertThat(byName.get("rmq-healthy").status()).isEqualTo(ClusterStatus.healthy);
        assertThat(byName.get("rmq-healthy").brokers())
                .singleElement()
                .extracting(ManagedBroker::version)
                .isEqualTo("V5_5_0");
        assertThat(byName.get("rmq-degraded").status()).isEqualTo(ClusterStatus.warning);
        assertThat(byName.get("rmq-degraded").brokers())
                .singleElement()
                .extracting(ManagedBroker::version)
                .isNull();
    }

    private static InstanceVO apacheInstance(String name, String endpoint) {
        return InstanceVO.builder()
                .name(name)
                .vendor(InstanceVendor.APACHE)
                .endpoint(endpoint)
                .build();
    }

    private MQAdminExt adminWith(ClusterInfo info) throws Exception {
        MQAdminExt admin = org.mockito.Mockito.mock(MQAdminExt.class);
        when(admin.examineBrokerClusterInfo()).thenReturn(info);
        return admin;
    }

    private static ClusterInfo clusterInfo(
            Map<String, Set<String>> clusterAddrTable,
            Map<String, BrokerData> brokerAddrTable) {
        ClusterInfo info = new ClusterInfo();
        info.setClusterAddrTable(new HashMap<>(clusterAddrTable));
        info.setBrokerAddrTable(new HashMap<>(brokerAddrTable));
        return info;
    }

    private static BrokerData brokerData(String cluster, String brokerName, Long brokerId, String addr) {
        HashMap<Long, String> addrs = new HashMap<>();
        addrs.put(brokerId, addr);
        return new BrokerData(cluster, brokerName, addrs);
    }
}
