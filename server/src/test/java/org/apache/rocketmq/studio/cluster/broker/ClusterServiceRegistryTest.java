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

import org.apache.rocketmq.studio.cluster.nameserver.NameserverRegistryService;
import org.apache.rocketmq.studio.cluster.nameserver.NameserverRegistryVO;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.apache.rocketmq.studio.provider.apache.RocketMQBrokerConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClusterServiceRegistryTest {

    @Mock
    private ClusterRepository clusterRepository;

    @Mock
    private ClusterProvider clusterProvider;

    @Mock
    private RocketMQBrokerConfigService brokerConfigService;

    @Mock
    private AuditService auditService;

    @Mock
    private NameserverRegistryService registryService;

    @InjectMocks
    private ClusterService clusterService;

    @Test
    void listRegistryClustersShouldProbeConcurrentlyAndSkipFailuresTest() {
        when(registryService.list()).thenReturn(List.of(
                NameserverRegistryVO.builder()
                        .id(1L)
                        .name("rocketmq1")
                        .namesrvAddr("rocketmq1-nameserver:9876")
                        .build(),
                NameserverRegistryVO.builder()
                        .id(2L)
                        .name("rocketmq2")
                        .namesrvAddr("rocketmq2-nameserver:9876")
                        .build()));
        when(clusterProvider.discoverClustersAt("rocketmq1-nameserver:9876")).thenReturn(List.of(
                ClusterVO.builder().id("DefaultCluster").name("DefaultCluster").build()));
        when(clusterProvider.discoverClustersAt("rocketmq2-nameserver:9876"))
                .thenThrow(new BusinessException(502, "unreachable"));

        List<ClusterVO> result = clusterService.listRegistryClusters();

        assertThat(result).hasSize(1);
        ClusterVO cluster = result.get(0);
        assertThat(cluster.getName()).isEqualTo("rocketmq1");
        assertThat(cluster.getNsClusterName()).isEqualTo("DefaultCluster");
        assertThat(cluster.getEndpoint()).isEqualTo("rocketmq1-nameserver:9876");
    }

    @Test
    void listRegistryClustersShouldReturnEmptyWhenRegistryEmptyTest() {
        when(registryService.list()).thenReturn(List.of());

        assertThat(clusterService.listRegistryClusters()).isEmpty();
    }

    @Test
    void listRegistryClustersShouldSkipEntriesWithoutAddressTest() {
        when(registryService.list()).thenReturn(List.of(
                NameserverRegistryVO.builder().id(1L).name("no-addr").namesrvAddr(" ").build()));

        assertThat(clusterService.listRegistryClusters()).isEmpty();
    }
}
