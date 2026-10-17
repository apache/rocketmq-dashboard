/*
 * Licensed to the Apache Software Foundation (ASF) under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApacheWriteTargetResolverTest {

    private static ClusterInfo twoClusterInfo() {
        ClusterInfo info = new ClusterInfo();
        info.setClusterAddrTable(new HashMap<>(Map.of(
                "cluster-a", Set.of("broker-a"), "cluster-b", Set.of("broker-b"))));
        info.setBrokerAddrTable(new HashMap<>(Map.of(
                "broker-a", new BrokerData("cluster-a", "broker-a",
                        new HashMap<>(Map.of(0L, "10.0.0.1:10911"))),
                "broker-b", new BrokerData("cluster-b", "broker-b",
                        new HashMap<>(Map.of(0L, "10.0.1.1:10911"))))));
        return info;
    }

    private static ClusterInfo singleClusterInfo() {
        ClusterInfo info = new ClusterInfo();
        info.setClusterAddrTable(new HashMap<>(Map.of("only", Set.of("broker-a"))));
        info.setBrokerAddrTable(new HashMap<>(Map.of(
                "broker-a", new BrokerData("only", "broker-a",
                        new HashMap<>(Map.of(0L, "10.0.0.1:10911"))))));
        return info;
    }

    private static InstanceVO virtualInstance(String name) {
        return InstanceVO.builder().name(name).build();
    }

    private static InstanceVO registeredInstance() {
        InstanceVO vo = InstanceVO.builder().name("registered").build();
        vo.setId(1L);
        return vo;
    }

    private static DefaultMQAdminExt adminReturning(ClusterInfo info) throws Exception {
        DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
        when(admin.examineBrokerClusterInfo()).thenReturn(info);
        return admin;
    }

    @Test
    void ownedClusterWinsOverEverythingElse() throws Exception {
        ApacheWriteTargetResolver.Target target = ApacheWriteTargetResolver.resolve(
                adminReturning(twoClusterInfo()), registeredInstance(), "cluster-b");

        assertThat(target.cluster()).isEqualTo("cluster-b");
        assertThat(target.masters()).containsExactly("10.0.1.1:10911");
        assertThat(target.brokers()).containsExactly("broker-b");
    }

    @Test
    void aVirtualInstanceWithoutOwnedClusterBecomesTheClusterByName() throws Exception {
        ApacheWriteTargetResolver.Target target = ApacheWriteTargetResolver.resolve(
                adminReturning(twoClusterInfo()), virtualInstance("cluster-a"), null);

        assertThat(target.cluster()).isEqualTo("cluster-a");
    }

    @Test
    void aVirtualInstanceNameDisagreeingWithTheOwnedClusterIsRejected() {
        assertThatThrownBy(() -> ApacheWriteTargetResolver.resolve(
                adminReturning(twoClusterInfo()), virtualInstance("cluster-a"), "cluster-b"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not match the physical cluster");
    }

    @Test
    void aRegisteredInstanceWithoutOwnedClusterIsRejectedOnMultiClusterEndpoints() {
        assertThatThrownBy(() -> ApacheWriteTargetResolver.resolve(
                adminReturning(twoClusterInfo()), registeredInstance(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("writes are rejected on multi-cluster endpoints");
    }

    @Test
    void aRegisteredInstanceWithoutOwnedClusterUsesTheUniqueClusterOnSingleClusterEndpoints() throws Exception {
        ApacheWriteTargetResolver.Target target = ApacheWriteTargetResolver.resolve(
                adminReturning(singleClusterInfo()), registeredInstance(), null);

        assertThat(target.cluster()).isEqualTo("only");
    }

    @Test
    void inconsistentBrokerTableOnTheUniqueClusterPathIsRejected() throws Exception {
        ClusterInfo info = singleClusterInfo();
        info.getBrokerAddrTable().put("broker-a",
                new BrokerData("somewhere-else", "broker-a", new HashMap<>(Map.of(0L, "10.0.0.1:10911"))));

        assertThatThrownBy(() -> ApacheWriteTargetResolver.resolve(adminReturning(info), registeredInstance(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("topology is not uniquely determined");
    }

    @Test
    void incompleteTopologyIsRejectedUpFront() throws Exception {
        ClusterInfo empty = new ClusterInfo();

        assertThatThrownBy(() -> ApacheWriteTargetResolver.resolve(
                adminReturning(empty), registeredInstance(), "cluster-a"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Incomplete cluster topology");
    }

    @Test
    void targetRejectsAClusterMissingFromTheTable() {
        assertThatThrownBy(() -> ApacheWriteTargetResolver.target(twoClusterInfo(), "no-such-cluster"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not exist or has no broker");
    }

    @Test
    void targetRejectsABrokerClaimedByTheClusterTableButAbsentFromTheBrokerTable() {
        ClusterInfo info = twoClusterInfo();
        info.getBrokerAddrTable().remove("broker-a");

        // A cluster-table entry with no broker-table row is caught by the master-address
        // check of the per-broker walk (broker == null), not by the cross-table scan.
        assertThatThrownBy(() -> ApacheWriteTargetResolver.target(info, "cluster-a"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Incomplete broker/master topology");
    }

    @Test
    void targetRejectsAClusterTableEntryMissingFromTheBrokerTableCrossCheck() {
        ClusterInfo info = twoClusterInfo();
        // An EXTRA broker claims cluster-a but is not in the cluster table's name set:
        // the cross-table scan rejects the topology as incomplete.
        info.getBrokerAddrTable().put("broker-x",
                new BrokerData("cluster-a", "broker-x", new HashMap<>(Map.of(0L, "10.0.0.9:10911"))));

        assertThatThrownBy(() -> ApacheWriteTargetResolver.target(info, "cluster-a"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("incomplete topology rejected");
    }

    @Test
    void targetRejectsABrokerWithoutAMasterAddress() {
        ClusterInfo info = twoClusterInfo();
        info.getBrokerAddrTable().put("broker-a",
                new BrokerData("cluster-a", "broker-a", new HashMap<>()));

        assertThatThrownBy(() -> ApacheWriteTargetResolver.target(info, "cluster-a"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Incomplete broker/master topology");
    }

    @Test
    void requireTopicRouteRejectsRoutesThatLeaveTheTargetCluster() throws Exception {
        DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
        ApacheWriteTargetResolver.Target target = new ApacheWriteTargetResolver.Target("cluster-a", Set.of("10.0.0.1:10911"), Set.of("broker-a"));
        TopicRouteData route = new TopicRouteData();
        route.setBrokerDatas(List.of(
                new BrokerData("cluster-a", "broker-a", new HashMap<>(Map.of(0L, "10.0.0.1:10911"))),
                new BrokerData("cluster-b", "broker-b", new HashMap<>(Map.of(0L, "10.0.1.1:10911")))));
        route.setQueueDatas(List.of());
        when(admin.examineTopicRouteInfo("orders")).thenReturn(route);

        assertThatThrownBy(() -> ApacheWriteTargetResolver.requireTopicRoute(admin, target, "orders"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("outside the target cluster");
    }

    @Test
    void requireTopicRouteAcceptsARouteFullyInsideTheTarget() throws Exception {
        DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
        ApacheWriteTargetResolver.Target target = new ApacheWriteTargetResolver.Target("cluster-a", Set.of("10.0.0.1:10911"), Set.of("broker-a"));
        TopicRouteData route = new TopicRouteData();
        route.setBrokerDatas(List.of(
                new BrokerData("cluster-a", "broker-a", new HashMap<>(Map.of(0L, "10.0.0.1:10911")))));
        org.apache.rocketmq.remoting.protocol.route.QueueData queue = new org.apache.rocketmq.remoting.protocol.route.QueueData();
        queue.setBrokerName("broker-a");
        route.setQueueDatas(List.of(queue));
        when(admin.examineTopicRouteInfo("orders")).thenReturn(route);

        ApacheWriteTargetResolver.requireTopicRoute(admin, target, "orders");
    }
}
