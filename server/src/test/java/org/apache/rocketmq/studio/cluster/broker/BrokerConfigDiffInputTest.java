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

import org.apache.rocketmq.studio.cluster.config.ClusterConfigVO;
import org.apache.rocketmq.studio.provider.apache.RocketMQBrokerConfigService;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrokerConfigDiffInputTest {

    @Mock
    private ClusterService clusterService;

    @Mock
    private RocketMQBrokerConfigService brokerConfigService;

    private BrokerConfigDiffService service;

    @BeforeEach
    void setUp() {
        service = new BrokerConfigDiffService(clusterService, brokerConfigService);
    }

    private ClusterVO clusterWithOneBroker() {
        List<BrokerVO> brokers = new ArrayList<>();
        brokers.add(BrokerVO.builder().name("broker-a").addr("10.0.0.1:10911").build());
        return ClusterVO.builder().id("cluster-a").brokers(brokers).build();
    }

    @Test
    void rejectsBlankClusterIdentifiers() {
        assertThatThrownBy(() -> service.compare("  ", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cluster");
        assertThatThrownBy(() -> service.compare("  ", "instance-a"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cluster");
    }

    @Test
    void routesInstanceScopedComparisonThroughScopedLookups() {
        when(clusterService.getCluster("cluster-a", "instance-a")).thenReturn(clusterWithOneBroker());
        when(brokerConfigService.getBrokerConfig("10.0.0.1:10911", "instance-a"))
                .thenReturn(ClusterConfigVO.builder().writeQueueNums(8).readQueueNums(8).build());

        service.compare(" cluster-a ", " instance-a ");

        verify(clusterService).getCluster("cluster-a", "instance-a");
        verify(brokerConfigService).getBrokerConfig("10.0.0.1:10911", "instance-a");
    }

    @Test
    void treatsBlankInstanceAsUnscopedComparison() {
        when(clusterService.getCluster("cluster-a")).thenReturn(
                ClusterVO.builder().id("cluster-a").brokers(new ArrayList<>()).build());

        assertThatThrownBy(() -> service.compare("cluster-a", "  "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no broker");

        verify(clusterService).getCluster("cluster-a");
        verify(brokerConfigService, org.mockito.Mockito.never())
                .getBrokerConfig(anyString(), isNull());
    }
}
