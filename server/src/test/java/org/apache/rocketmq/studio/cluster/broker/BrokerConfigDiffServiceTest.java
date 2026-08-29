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

import org.apache.rocketmq.studio.cluster.config.BrokerConfigDiffVO;
import org.apache.rocketmq.studio.cluster.config.ClusterConfigVO;
import org.apache.rocketmq.studio.common.domain.enums.FlushDiskType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.provider.apache.RocketMQBrokerConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.verify;
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

    @Test
    void consistentBrokerConfigurationShouldBeReportedTest() {
        when(clusterService.getCluster("cluster-a")).thenReturn(cluster(
                broker("broker-a", "10.0.0.1:10911"),
                broker("broker-b", "10.0.0.2:10911")));
        when(brokerConfigService.getBrokerConfig("10.0.0.1:10911", null))
                .thenReturn(config(FlushDiskType.ASYNC_FLUSH, true, 8, 6, "04"));
        when(brokerConfigService.getBrokerConfig("10.0.0.2:10911", null))
                .thenReturn(config(FlushDiskType.ASYNC_FLUSH, true, 8, 6, "04"));

        BrokerConfigDiffVO result = service.compare(" cluster-a ", null);

        assertThat(result.getCluster()).isEqualTo("cluster-a");
        assertThat(result.isComplete()).isTrue();
        assertThat(result.isDriftDetected()).isFalse();
        assertThat(result.getBrokerCount()).isEqualTo(2);
        assertThat(result.getReachableBrokerCount()).isEqualTo(2);
        assertThat(result.getComparedFields())
                .contains("flushDiskType", "autoCreateTopicEnable", "writeQueueNums");
        assertThat(result.getDifferences()).isEmpty();
        assertThat(result.getBrokers())
                .extracting(
                        BrokerConfigDiffVO.BrokerStatusVO::getName,
                        BrokerConfigDiffVO.BrokerStatusVO::getAddress,
                        BrokerConfigDiffVO.BrokerStatusVO::isReachable)
                .containsExactly(
                        tuple("broker-a", "10.0.0.1:10911", true),
                        tuple("broker-b", "10.0.0.2:10911", true));
    }

    @Test
    void changedBrokerValuesShouldBeExposedTest() {
        when(clusterService.getCluster("cluster-a", "instance-a")).thenReturn(cluster(
                broker("broker-a", "10.0.0.1:10911"),
                broker("broker-b", "10.0.0.2:10911")));
        when(brokerConfigService.getBrokerConfig("10.0.0.1:10911", "instance-a"))
                .thenReturn(config(FlushDiskType.ASYNC_FLUSH, true, 8, 6, "04"));
        when(brokerConfigService.getBrokerConfig("10.0.0.2:10911", "instance-a"))
                .thenReturn(config(FlushDiskType.SYNC_FLUSH, false, 16, 4, "06"));

        BrokerConfigDiffVO result = service.compare(" cluster-a ", " instance-a ");

        assertThat(result.isComplete()).isTrue();
        assertThat(result.isDriftDetected()).isTrue();
        assertThat(result.getDifferences())
                .extracting(BrokerConfigDiffVO.ConfigDifferenceVO::getField)
                .contains(
                        "flushDiskType",
                        "autoCreateTopicEnable",
                        "writeQueueNums",
                        "readQueueNums",
                        "brokerPermission",
                        "deleteWhen");
        BrokerConfigDiffVO.ConfigDifferenceVO queueNums = result.getDifferences().stream()
                .filter(difference -> difference.getField().equals("writeQueueNums"))
                .findFirst()
                .orElseThrow();
        assertThat(queueNums.getBrokerProperty()).isEqualTo("defaultTopicQueueNums");
        assertThat(queueNums.getValues())
                .extracting(
                        BrokerConfigDiffVO.ConfigValueVO::getBrokerName,
                        BrokerConfigDiffVO.ConfigValueVO::getAddress,
                        BrokerConfigDiffVO.ConfigValueVO::isConfigured,
                        BrokerConfigDiffVO.ConfigValueVO::getValue)
                .containsExactly(
                        tuple("broker-a", "10.0.0.1:10911", true, "8"),
                        tuple("broker-b", "10.0.0.2:10911", true, "16"));
        verify(clusterService).getCluster("cluster-a", "instance-a");
    }

    @Test
    void partialResultsShouldBeKeptWhenBrokerConfigReadFailsTest() {
        when(clusterService.getCluster("cluster-a")).thenReturn(cluster(
                broker("broker-a", "10.0.0.1:10911"),
                broker("broker-b", "10.0.0.2:10911")));
        when(brokerConfigService.getBrokerConfig("10.0.0.1:10911", null))
                .thenReturn(config(FlushDiskType.ASYNC_FLUSH, true, 8, 6, "04"));
        when(brokerConfigService.getBrokerConfig("10.0.0.2:10911", null))
                .thenThrow(new BusinessException(502, "broker unavailable"));

        BrokerConfigDiffVO result = service.compare("cluster-a", null);

        assertThat(result.isComplete()).isFalse();
        assertThat(result.isDriftDetected()).isFalse();
        assertThat(result.getReachableBrokerCount()).isEqualTo(1);
        assertThat(result.getBrokers())
                .extracting(
                        BrokerConfigDiffVO.BrokerStatusVO::getAddress,
                        BrokerConfigDiffVO.BrokerStatusVO::isReachable,
                        BrokerConfigDiffVO.BrokerStatusVO::getMessage)
                .containsExactly(
                        tuple("10.0.0.1:10911", true, null),
                        tuple("10.0.0.2:10911", false, null));
    }

    @Test
    void brokerAddressesShouldBeDeduplicatedBeforeReadingConfigTest() {
        when(clusterService.getCluster("cluster-a")).thenReturn(cluster(
                broker("broker-a", "10.0.0.1:10911"),
                broker("broker-a-duplicate", " 10.0.0.1:10911 "),
                broker("broker-b", "10.0.0.2:10911")));
        when(brokerConfigService.getBrokerConfig("10.0.0.1:10911", null))
                .thenReturn(config(FlushDiskType.ASYNC_FLUSH, true, 8, 6, "04"));
        when(brokerConfigService.getBrokerConfig("10.0.0.2:10911", null))
                .thenReturn(config(FlushDiskType.ASYNC_FLUSH, true, 8, 6, "04"));

        BrokerConfigDiffVO result = service.compare("cluster-a", null);

        assertThat(result.getBrokerCount()).isEqualTo(2);
        assertThat(result.getBrokers())
                .extracting(BrokerConfigDiffVO.BrokerStatusVO::getAddress)
                .containsExactly("10.0.0.1:10911", "10.0.0.2:10911");
    }

    @Test
    void singleReachableBrokerShouldBeCompleteWithoutDriftTest() {
        when(clusterService.getCluster("cluster-a")).thenReturn(cluster(
                broker(null, "10.0.0.1:10911")));
        when(brokerConfigService.getBrokerConfig("10.0.0.1:10911", null))
                .thenReturn(config(FlushDiskType.ASYNC_FLUSH, true, 8, 6, "04"));

        BrokerConfigDiffVO result = service.compare("cluster-a", null);

        assertThat(result.isComplete()).isTrue();
        assertThat(result.isDriftDetected()).isFalse();
        assertThat(result.getBrokerCount()).isEqualTo(1);
        assertThat(result.getBrokers()).singleElement().satisfies(status -> {
            assertThat(status.getName()).isEqualTo("10.0.0.1:10911");
            assertThat(status.isReachable()).isTrue();
        });
    }

    @Test
    void clusterWithoutBrokerAddressesShouldBeRejectedTest() {
        when(clusterService.getCluster("cluster-a")).thenReturn(cluster(
                broker("broker-a", " "),
                broker("broker-b", null)));

        assertThatThrownBy(() -> service.compare("cluster-a", null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cluster has no broker endpoints: cluster-a")
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode())
                        .isEqualTo(409));
    }

    @Test
    void blankClusterIdShouldBeRejectedTest() {
        assertThatThrownBy(() -> service.compare(" ", null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("cluster is required")
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode())
                        .isEqualTo(400));
    }

    private ClusterVO cluster(BrokerVO... brokers) {
        ClusterVO cluster = ClusterVO.builder()
                .name("cluster-a")
                .brokers(List.of(brokers))
                .build();
        cluster.setId("cluster-a");
        return cluster;
    }

    private BrokerVO broker(String name, String address) {
        return BrokerVO.builder()
                .name(name)
                .addr(address)
                .build();
    }

    private ClusterConfigVO config(
            FlushDiskType flushDiskType,
            boolean autoCreateTopic,
            int queueNums,
            int brokerPermission,
            String deleteWhen) {
        return ClusterConfigVO.builder()
                .flushDiskType(flushDiskType)
                .autoCreateTopicEnable(autoCreateTopic)
                .autoCreateSubscriptionGroup(true)
                .maxMessageSize(4194304)
                .msgTraceTopicName("RMQ_SYS_TRACE_TOPIC")
                .deleteWhen(deleteWhen)
                .fileReservedTime(72)
                .writeQueueNums(queueNums)
                .readQueueNums(queueNums)
                .brokerPermission(brokerPermission)
                .build();
    }
}
