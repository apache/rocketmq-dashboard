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
package org.apache.rocketmq.studio.cluster.nameserver;

import org.apache.rocketmq.studio.cluster.broker.ClusterService;
import org.apache.rocketmq.studio.cluster.broker.ClusterVO;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NameServerConfigDiffServiceTest {

    @Mock
    private ClusterService clusterService;

    @Mock
    private MqAdminExtFactory adminFactory;

    @Mock
    private RuntimeAdminClientResolver runtimeAdminClientResolver;

    @Mock
    private MQAdminExt admin;

    private NameServerConfigDiffService service;

    @BeforeEach
    void setUp() {
        service = new NameServerConfigDiffService(
                clusterService, adminFactory, runtimeAdminClientResolver);
    }

    private void stubAdminFactory() {
        when(adminFactory.execute(anyString(), isNull(), any())).thenAnswer(invocation -> {
            MqAdminExtFactory.AdminAction<Object> action = invocation.getArgument(2);
            try {
                return action.apply(admin);
            } catch (BusinessException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new BusinessException(502, "RocketMQ admin call failed");
            }
        });
    }

    @Test
    void compareShouldReportConsistentSafeConfiguration() throws Exception {
        stubAdminFactory();
        when(clusterService.getCluster("cluster-a")).thenReturn(cluster(
                "ns-b:9876;ns-a:9876",
                List.of(nameServer("ns-a:9876"), nameServer("ns-b:9876"))));
        Properties first = properties(
                "listenPort", "9876",
                "serverWorkerThreads", "8",
                "password", "first-secret");
        Properties second = properties(
                "listenPort", "9876",
                "serverWorkerThreads", "8",
                "password", "second-secret");
        when(admin.getNameServerConfig(List.of("ns-a:9876")))
                .thenReturn(Map.of("ns-a:9876", first));
        when(admin.getNameServerConfig(List.of("ns-b:9876")))
                .thenReturn(Map.of("ns-b:9876", second));

        NameServerConfigDiffVO result = service.compare(" cluster-a ");

        assertThat(result.isComplete()).isTrue();
        assertThat(result.isDriftDetected()).isFalse();
        assertThat(result.getNodeCount()).isEqualTo(2);
        assertThat(result.getReachableNodeCount()).isEqualTo(2);
        assertThat(result.getComparedKeys()).contains("listenPort", "serverWorkerThreads");
        assertThat(result.getComparedKeys()).doesNotContain("password");
        assertThat(result.getDifferences()).isEmpty();
        assertThat(result.getNodes())
                .extracting(
                        NameServerConfigDiffVO.NodeStatusVO::getAddress,
                        NameServerConfigDiffVO.NodeStatusVO::isReachable)
                .containsExactly(
                        tuple("ns-a:9876", true),
                        tuple("ns-b:9876", true));
    }

    @Test
    void compareShouldResolveClusterThroughSelectedInstance() throws Exception {
        when(clusterService.getCluster("cluster-a", "instance-a")).thenReturn(cluster(
                "ns-a:9876;ns-b:9876",
                List.of(nameServer("ns-a:9876"), nameServer("ns-b:9876"))));
        when(runtimeAdminClientResolver.execute(eq("instance-a"), any())).thenAnswer(invocation -> {
            MqAdminExtFactory.AdminAction<Object> action = invocation.getArgument(1);
            return action.apply(admin);
        });
        when(admin.getNameServerConfig(List.of("ns-a:9876")))
                .thenReturn(Map.of("ns-a:9876", properties("listenPort", "9876")));
        when(admin.getNameServerConfig(List.of("ns-b:9876")))
                .thenReturn(Map.of("ns-b:9876", properties("listenPort", "9876")));

        NameServerConfigDiffVO result = service.compare(" cluster-a ", " instance-a ");

        assertThat(result.isComplete()).isTrue();
        verify(clusterService).getCluster("cluster-a", "instance-a");
        verify(runtimeAdminClientResolver, times(2)).execute(eq("instance-a"), any());
        verify(adminFactory, never()).execute(anyString(), isNull(), any());
    }

    @Test
    void compareShouldExposeChangedAndMissingSafeValues() throws Exception {
        stubAdminFactory();
        when(clusterService.getCluster("cluster-a")).thenReturn(cluster(
                "ns-a:9876;ns-b:9876",
                List.of(nameServer("ns-a:9876"), nameServer("ns-b:9876"))));
        Properties first = properties(
                "listenPort", "9876",
                "serverWorkerThreads", "8");
        Properties second = properties("listenPort", "19876");
        when(admin.getNameServerConfig(List.of("ns-a:9876")))
                .thenReturn(Map.of("ns-a:9876", first));
        when(admin.getNameServerConfig(List.of("ns-b:9876")))
                .thenReturn(Map.of("ns-b:9876", second));

        NameServerConfigDiffVO result = service.compare("cluster-a");

        assertThat(result.isComplete()).isTrue();
        assertThat(result.isDriftDetected()).isTrue();
        assertThat(result.getDifferences())
                .extracting(NameServerConfigDiffVO.ConfigDifferenceVO::getKey)
                .containsExactly("listenPort", "serverWorkerThreads");
        NameServerConfigDiffVO.ConfigDifferenceVO workerDifference =
                result.getDifferences().get(1);
        assertThat(workerDifference.getValues())
                .extracting(
                        NameServerConfigDiffVO.ConfigValueVO::getAddress,
                        NameServerConfigDiffVO.ConfigValueVO::isConfigured,
                        NameServerConfigDiffVO.ConfigValueVO::getValue)
                .containsExactly(
                        tuple("ns-a:9876", true, "8"),
                        tuple("ns-b:9876", false, null));
    }

    @Test
    void compareShouldKeepPartialResultsWhenOneNodeIsUnavailable() throws Exception {
        stubAdminFactory();
        when(clusterService.getCluster("cluster-a")).thenReturn(cluster(
                "ns-a:9876;ns-b:9876",
                List.of(nameServer("ns-a:9876"), nameServer("ns-b:9876"))));
        Properties first = properties("listenPort", "9876");
        when(admin.getNameServerConfig(List.of("ns-a:9876")))
                .thenReturn(Map.of("ns-a:9876", first));
        when(admin.getNameServerConfig(List.of("ns-b:9876")))
                .thenThrow(new IllegalStateException("unreachable"));

        NameServerConfigDiffVO result = service.compare("cluster-a");

        assertThat(result.isComplete()).isFalse();
        assertThat(result.isDriftDetected()).isFalse();
        assertThat(result.getReachableNodeCount()).isEqualTo(1);
        assertThat(result.getDifferences()).isEmpty();
        assertThat(result.getNodes())
                .extracting(
                        NameServerConfigDiffVO.NodeStatusVO::getAddress,
                        NameServerConfigDiffVO.NodeStatusVO::isReachable)
                .containsExactly(
                        tuple("ns-a:9876", true),
                        tuple("ns-b:9876", false));
    }

    @Test
    void compareShouldTreatOneReachableNodeAsACompleteCheck() throws Exception {
        stubAdminFactory();
        when(clusterService.getCluster("cluster-a"))
                .thenReturn(cluster("ns-a:9876", List.of()));
        when(admin.getNameServerConfig(List.of("ns-a:9876")))
                .thenReturn(Map.of("ns-a:9876", properties("listenPort", "9876")));

        NameServerConfigDiffVO result = service.compare("cluster-a");

        assertThat(result.isComplete()).isTrue();
        assertThat(result.isDriftDetected()).isFalse();
        assertThat(result.getNodeCount()).isEqualTo(1);
        assertThat(result.getReachableNodeCount()).isEqualTo(1);
        assertThat(result.getDifferences()).isEmpty();
    }

    @Test
    void compareShouldDeduplicateDnsHostnamesIgnoringCase() throws Exception {
        stubAdminFactory();
        when(clusterService.getCluster("cluster-a")).thenReturn(cluster(
                "ns.example.com:9876", List.of(nameServer("NS.EXAMPLE.COM:9876"))));
        when(admin.getNameServerConfig(List.of("NS.EXAMPLE.COM:9876")))
                .thenReturn(Map.of("NS.EXAMPLE.COM:9876", properties("listenPort", "9876")));

        NameServerConfigDiffVO result = service.compare("cluster-a");

        assertThat(result.getNodeCount()).isEqualTo(1);
        assertThat(result.getReachableNodeCount()).isEqualTo(1);
        verify(admin, times(1)).getNameServerConfig(List.of("NS.EXAMPLE.COM:9876"));
    }

    @Test
    void compareShouldMarkTheCheckIncompleteWhenEveryNodeIsUnavailable() throws Exception {
        stubAdminFactory();
        when(clusterService.getCluster("cluster-a")).thenReturn(cluster(
                "ns-a:9876;ns-b:9876",
                List.of(nameServer("ns-a:9876"), nameServer("ns-b:9876"))));
        when(admin.getNameServerConfig(List.of("ns-a:9876")))
                .thenThrow(new IllegalStateException("unreachable"));
        when(admin.getNameServerConfig(List.of("ns-b:9876")))
                .thenThrow(new IllegalStateException("unreachable"));

        NameServerConfigDiffVO result = service.compare("cluster-a");

        assertThat(result.isComplete()).isFalse();
        assertThat(result.isDriftDetected()).isFalse();
        assertThat(result.getReachableNodeCount()).isZero();
        assertThat(result.getDifferences()).isEmpty();
        assertThat(result.getNodes())
                .extracting(
                        NameServerConfigDiffVO.NodeStatusVO::getAddress,
                        NameServerConfigDiffVO.NodeStatusVO::isReachable)
                .containsExactly(
                        tuple("ns-a:9876", false),
                        tuple("ns-b:9876", false));
    }

    @Test
    void compareShouldRejectClusterWithoutNameServers() {
        when(clusterService.getCluster("cluster-a"))
                .thenReturn(cluster(null, List.of()));

        assertThatThrownBy(() -> service.compare("cluster-a"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cluster has no NameServer endpoints: cluster-a")
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode())
                        .isEqualTo(409));
    }

    @Test
    void compareShouldRejectBlankClusterId() {
        assertThatThrownBy(() -> service.compare(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("cluster is required")
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode())
                        .isEqualTo(400));
    }

    private ClusterVO cluster(String endpoint, List<NameServerVO> nameServers) {
        ClusterVO cluster = ClusterVO.builder()
                .name("cluster-a")
                .endpoint(endpoint)
                .nameServers(nameServers)
                .build();
        cluster.setId("cluster-a");
        return cluster;
    }

    private NameServerVO nameServer(String address) {
        return NameServerVO.builder().addr(address).build();
    }

    private Properties properties(String... entries) {
        Properties properties = new Properties();
        for (int index = 0; index < entries.length; index += 2) {
            properties.setProperty(entries[index], entries[index + 1]);
        }
        return properties;
    }
}
