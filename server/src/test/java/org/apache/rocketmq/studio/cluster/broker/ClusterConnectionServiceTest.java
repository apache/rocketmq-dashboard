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

import org.apache.rocketmq.studio.common.domain.enums.BrokerStatus;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClusterConnectionServiceTest {

    @Mock
    private RealClusterProvider clusterProvider;

    @InjectMocks
    private ClusterConnectionService service;

    private ClusterVO clusterWith(String... brokerNames) {
        List<BrokerVO> brokers = java.util.Arrays.stream(brokerNames)
                .map(name -> BrokerVO.builder().name(name).status(BrokerStatus.running).build())
                .toList();
        return ClusterVO.builder()
                .name("DefaultCluster")
                .brokers(brokers)
                .build();
    }

    @Test
    void connectionShouldSummariseTopologyTest() {
        when(clusterProvider.describeCluster(eq("10.0.0.1:9876")))
                .thenReturn(clusterWith("broker-a", "broker-b"));

        ClusterProbeResult result = service.testConnection(
                TestConnectionDTO.builder().namesrvAddr(" 10.0.0.1:9876 ").build());

        assertThat(result.isConnected()).isTrue();
        assertThat(result.getNamesrvAddr()).isEqualTo("10.0.0.1:9876");
        assertThat(result.getClusterName()).isEqualTo("DefaultCluster");
        assertThat(result.getBrokerCount()).isEqualTo(2);
        assertThat(result.getBrokerNames()).containsExactly("broker-a", "broker-b");
        assertThat(result.getElapsedMillis()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void connectionShouldPropagateProviderFailureTest() {
        when(clusterProvider.describeCluster(eq("10.0.0.9:9876")))
                .thenThrow(new BusinessException(502, "Failed to connect NameServer"));

        assertThatThrownBy(() -> service.testConnection(
                TestConnectionDTO.builder().namesrvAddr("10.0.0.9:9876").build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Failed to connect NameServer");
    }
}
