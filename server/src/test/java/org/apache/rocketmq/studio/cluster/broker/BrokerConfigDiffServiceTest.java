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

import org.apache.rocketmq.common.constant.PermName;
import org.apache.rocketmq.studio.common.domain.enums.FlushDiskType;
import org.apache.rocketmq.studio.cluster.config.BrokerConfigDiffVO;
import org.apache.rocketmq.studio.cluster.config.ClusterConfigVO;
import org.apache.rocketmq.studio.provider.apache.RocketMQBrokerConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrokerConfigDiffServiceTest {

    @Mock
    private ClusterService clusterService;

    @Mock
    private RocketMQBrokerConfigService brokerConfigService;

    private BrokerConfigDiffService service;

    @BeforeEach
    void setUp() {
        service = new BrokerConfigDiffService(clusterService, brokerConfigService);
    }

    private ClusterVO clusterWith(String... nameAddrPairs) {
        java.util.List<BrokerVO> brokers = new java.util.ArrayList<>();
        for (int i = 0; i < nameAddrPairs.length; i += 2) {
            brokers.add(BrokerVO.builder()
                    .name(nameAddrPairs[i])
                    .addr(nameAddrPairs[i + 1])
                    .build());
        }
        return ClusterVO.builder().id("cluster-a").brokers(brokers).build();
    }

    private ClusterConfigVO config(int writeQueues, int readQueues) {
        return ClusterConfigVO.builder()
                .writeQueueNums(writeQueues)
                .readQueueNums(readQueues)
                .maxMessageSize(4 * 1024 * 1024)
                .autoCreateTopicEnable(true)
                .autoCreateSubscriptionGroup(true)
                .deleteWhen("04")
                .fileReservedTime(72)
                .brokerPermission(PermName.PERM_READ | PermName.PERM_WRITE)
                .flushDiskType(FlushDiskType.ASYNC_FLUSH)
                .build();
    }

    @Test
    void reportsAlignedBrokersAsCompleteWithoutDrift() {
        when(clusterService.getCluster("cluster-a")).thenReturn(clusterWith(
                "broker-a", "10.0.0.1:10911", "broker-b", "10.0.0.2:10911"));
        when(brokerConfigService.getBrokerConfig("10.0.0.1:10911", null)).thenReturn(config(8, 8));
        when(brokerConfigService.getBrokerConfig("10.0.0.2:10911", null)).thenReturn(config(8, 8));

        BrokerConfigDiffVO result = service.compare("cluster-a", null);

        assertThat(result.isComplete()).isTrue();
        assertThat(result.getBrokerCount()).isEqualTo(2);
        assertThat(result.getReachableBrokerCount()).isEqualTo(2);
        assertThat(result.isDriftDetected()).isFalse();
        assertThat(result.getDifferences()).isEmpty();
    }

    @Test
    void flagsQueueCountDriftAcrossBrokers() {
        when(clusterService.getCluster("cluster-a")).thenReturn(clusterWith(
                "broker-a", "10.0.0.1:10911", "broker-b", "10.0.0.2:10911"));
        when(brokerConfigService.getBrokerConfig("10.0.0.1:10911", null)).thenReturn(config(8, 8));
        when(brokerConfigService.getBrokerConfig("10.0.0.2:10911", null)).thenReturn(config(16, 8));

        BrokerConfigDiffVO result = service.compare("cluster-a", null);

        assertThat(result.isComplete()).isTrue();
        assertThat(result.isDriftDetected()).isTrue();
        assertThat(result.getDifferences())
                .anyMatch(difference -> "writeQueueNums".equals(difference.getField())
                        && difference.getValues().size() == 2);
        assertThat(result.getDifferences())
                .noneMatch(difference -> "readQueueNums".equals(difference.getField()));
    }
}
