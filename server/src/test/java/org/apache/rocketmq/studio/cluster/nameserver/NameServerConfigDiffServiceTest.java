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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NameServerConfigDiffServiceTest {

    @Mock
    private ClusterService clusterService;

    @Mock
    private MqAdminExtFactory adminFactory;

    @Mock
    private RuntimeAdminClientResolver runtimeAdminClientResolver;

    @Mock
    private MQAdminExt adminExt;

    private NameServerConfigDiffService service;

    private ClusterVO clusterWithEndpoint(String endpoint) {
        return ClusterVO.builder().id("cluster-a").endpoint(endpoint).build();
    }

    private Properties config(String listenPort) {
        Properties properties = new Properties();
        properties.setProperty("listenPort", listenPort);
        properties.setProperty("useEpollNativeSelector", "true");
        properties.setProperty("enableControllerInNamesrv", "false");
        return properties;
    }

    @BeforeEach
    void setUp() {
        lenient().when(adminFactory.execute(anyString(), any(), any())).thenAnswer(invocation ->
                invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(2).apply(adminExt));
        service = new NameServerConfigDiffService(clusterService, adminFactory, runtimeAdminClientResolver);
    }

    @Test
    void reportsAlignedNameServerNodesAsCompleteWithoutDrift() throws Exception {
        when(clusterService.getCluster("cluster-a")).thenReturn(clusterWithEndpoint(
                "10.0.0.1:9876;10.0.0.2:9876"));
        when(adminExt.getNameServerConfig(List.of("10.0.0.1:9876"))).thenReturn(
                Map.of("10.0.0.1:9876", config("9876")));
        when(adminExt.getNameServerConfig(List.of("10.0.0.2:9876"))).thenReturn(
                Map.of("10.0.0.2:9876", config("9876")));

        NameServerConfigDiffVO result = service.compare("cluster-a");

        assertThat(result.isComplete()).isTrue();
        assertThat(result.getNodeCount()).isEqualTo(2);
        assertThat(result.getReachableNodeCount()).isEqualTo(2);
        assertThat(result.isDriftDetected()).isFalse();
        assertThat(result.getDifferences()).isEmpty();
    }

    @Test
    void flagsListenPortDriftAcrossNameServerNodes() throws Exception {
        when(clusterService.getCluster("cluster-a")).thenReturn(clusterWithEndpoint(
                "10.0.0.1:9876;10.0.0.2:9876"));
        when(adminExt.getNameServerConfig(List.of("10.0.0.1:9876"))).thenReturn(
                Map.of("10.0.0.1:9876", config("9876")));
        when(adminExt.getNameServerConfig(List.of("10.0.0.2:9876"))).thenReturn(
                Map.of("10.0.0.2:9876", config("9877")));

        NameServerConfigDiffVO result = service.compare("cluster-a");

        assertThat(result.isDriftDetected()).isTrue();
        assertThat(result.getDifferences())
                .anyMatch(difference -> "listenPort".equals(difference.getKey())
                        && difference.getValues().size() == 2);
    }

    @Test
    void rejectsClustersWithoutAnyNameServerEndpoint() {
        when(clusterService.getCluster("cluster-a")).thenReturn(clusterWithEndpoint(null));

        assertThatThrownBy(() -> service.compare("cluster-a"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no NameServer");
    }
}
