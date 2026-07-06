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
package com.rocketmq.studio.cluster.broker;

import com.rocketmq.studio.cluster.config.ClusterConfigVO;
import com.rocketmq.studio.cluster.config.UpdateConfigDTO;

import com.rocketmq.studio.common.domain.enums.ClusterStatus;
import com.rocketmq.studio.common.domain.enums.ClusterType;
import com.rocketmq.studio.common.domain.enums.FlushDiskType;
import com.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClusterServiceTest {

    @Mock
    private ClusterRepository clusterRepository;

    @Mock
    private ClusterProvider clusterProvider;

    @InjectMocks
    private ClusterService clusterService;

    private ClusterVO sampleCluster;

    @BeforeEach
    void setUp() {
        sampleCluster = ClusterVO.builder()
                .name("test-cluster")
                .nsClusterName("ns-test-cluster")
                .type(ClusterType.V5_PROXY_CLUSTER)
                .endpoint("10.0.0.1:9876")
                .status(ClusterStatus.healthy)
                .version("5.1.0")
                .brokers(Collections.emptyList())
                .proxies(Collections.emptyList())
                .nameServers(Collections.emptyList())
                .config(ClusterConfigVO.builder()
                        .flushDiskType(FlushDiskType.ASYNC_FLUSH)
                        .writeQueueNums(8)
                        .readQueueNums(8)
                        .maxMessageSize(4194304)
                        .autoCreateTopicEnable(true)
                        .autoCreateSubscriptionGroup(true)
                        .fileReservedTime(72)
                        .brokerPermission(6)
                        .build())
                .topicCount(10)
                .groupCount(5)
                .build();
        sampleCluster.setId("cluster-1");
    }

    @Test
    void listClustersShouldReturnAllClusters() {
        ClusterVO secondCluster = ClusterVO.builder()
                .name("second-cluster")
                .status(ClusterStatus.warning)
                .build();
        secondCluster.setId("cluster-2");

        when(clusterRepository.findAll()).thenReturn(Arrays.asList(sampleCluster, secondCluster));

        List<ClusterVO> result = clusterService.listClusters();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("test-cluster");
        assertThat(result.get(1).getName()).isEqualTo("second-cluster");
        verify(clusterRepository).findAll();
    }

    @Test
    void listClustersShouldReturnEmptyListWhenNoClusters() {
        when(clusterRepository.findAll()).thenReturn(Collections.emptyList());

        List<ClusterVO> result = clusterService.listClusters();

        assertThat(result).isEmpty();
    }

    @Test
    void getClusterShouldReturnClusterWhenFound() {
        when(clusterRepository.findById("cluster-1")).thenReturn(Optional.of(sampleCluster));

        ClusterVO result = clusterService.getCluster("cluster-1");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("cluster-1");
        assertThat(result.getName()).isEqualTo("test-cluster");
        assertThat(result.getStatus()).isEqualTo(ClusterStatus.healthy);
        assertThat(result.getType()).isEqualTo(ClusterType.V5_PROXY_CLUSTER);
    }

    @Test
    void getClusterShouldThrowWhenNotFound() {
        when(clusterRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clusterService.getCluster("nonexistent"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cluster not found: nonexistent")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));
    }

    @Test
    void updateConfigShouldUpdateFlushDiskType() {
        when(clusterRepository.findById("cluster-1")).thenReturn(Optional.of(sampleCluster));

        UpdateConfigDTO command = UpdateConfigDTO.builder()
                .id("cluster-1")
                .flushDiskType("SYNC_FLUSH")
                .build();

        ClusterVO result = clusterService.updateClusterConfig(command);

        assertThat(result.getConfig().getFlushDiskType()).isEqualTo(FlushDiskType.SYNC_FLUSH);
        verify(clusterRepository).updateConfig(eq("cluster-1"), any(ClusterConfigVO.class));
    }

    @Test
    void updateConfigShouldUpdateMultipleFields() {
        when(clusterRepository.findById("cluster-1")).thenReturn(Optional.of(sampleCluster));

        UpdateConfigDTO command = UpdateConfigDTO.builder()
                .id("cluster-1")
                .flushDiskType("SYNC_FLUSH")
                .autoCreateTopicEnable(false)
                .autoCreateSubscriptionGroup(false)
                .maxMessageSize(8388608)
                .fileReservedTime(168)
                .writeQueueNums(16)
                .readQueueNums(16)
                .brokerPermission(4)
                .build();

        ClusterVO result = clusterService.updateClusterConfig(command);

        ClusterConfigVO config = result.getConfig();
        assertThat(config.getFlushDiskType()).isEqualTo(FlushDiskType.SYNC_FLUSH);
        assertThat(config.isAutoCreateTopicEnable()).isFalse();
        assertThat(config.isAutoCreateSubscriptionGroup()).isFalse();
        assertThat(config.getMaxMessageSize()).isEqualTo(8388608);
        assertThat(config.getFileReservedTime()).isEqualTo(168);
        assertThat(config.getWriteQueueNums()).isEqualTo(16);
        assertThat(config.getReadQueueNums()).isEqualTo(16);
        assertThat(config.getBrokerPermission()).isEqualTo(4);
    }

    @Test
    void updateConfigShouldPreserveExistingValuesWhenCommandFieldsAreNull() {
        when(clusterRepository.findById("cluster-1")).thenReturn(Optional.of(sampleCluster));

        UpdateConfigDTO command = UpdateConfigDTO.builder()
                .id("cluster-1")
                .flushDiskType("SYNC_FLUSH")
                .build();

        ClusterVO result = clusterService.updateClusterConfig(command);

        ClusterConfigVO config = result.getConfig();
        assertThat(config.getFlushDiskType()).isEqualTo(FlushDiskType.SYNC_FLUSH);
        assertThat(config.getWriteQueueNums()).isEqualTo(8);
        assertThat(config.getReadQueueNums()).isEqualTo(8);
        assertThat(config.isAutoCreateTopicEnable()).isTrue();
    }

    @Test
    void updateConfigShouldThrowWhenClusterNotFound() {
        when(clusterRepository.findById("missing")).thenReturn(Optional.empty());

        UpdateConfigDTO command = UpdateConfigDTO.builder()
                .id("missing")
                .flushDiskType("SYNC_FLUSH")
                .build();

        assertThatThrownBy(() -> clusterService.updateClusterConfig(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cluster not found: missing");
    }

    @Test
    void updateConfigShouldCreateConfigWhenNull() {
        ClusterVO clusterWithNullConfig = ClusterVO.builder()
                .name("null-config-cluster")
                .status(ClusterStatus.healthy)
                .config(null)
                .build();
        clusterWithNullConfig.setId("cluster-nc");

        when(clusterRepository.findById("cluster-nc")).thenReturn(Optional.of(clusterWithNullConfig));

        UpdateConfigDTO command = UpdateConfigDTO.builder()
                .id("cluster-nc")
                .flushDiskType("ASYNC_FLUSH")
                .build();

        ClusterVO result = clusterService.updateClusterConfig(command);

        assertThat(result.getConfig()).isNotNull();
        assertThat(result.getConfig().getFlushDiskType()).isEqualTo(FlushDiskType.ASYNC_FLUSH);
    }

    @Test
    void restartBrokerShouldReturnTrue() {
        when(clusterRepository.findById("cluster-1")).thenReturn(Optional.of(sampleCluster));

        boolean result = clusterService.restartBroker("cluster-1", "broker-0");

        assertThat(result).isTrue();
    }

    @Test
    void restartBrokerShouldThrowWhenClusterNotFound() {
        when(clusterRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clusterService.restartBroker("missing", "broker-0"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cluster not found: missing");
    }
}
