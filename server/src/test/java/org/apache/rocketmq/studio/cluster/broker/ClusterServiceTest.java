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

import org.apache.rocketmq.studio.cluster.config.ClusterConfigUpdateResultVO;
import org.apache.rocketmq.studio.cluster.config.ClusterConfigVO;
import org.apache.rocketmq.studio.cluster.config.UpdateConfigDTO;
import org.apache.rocketmq.studio.cluster.nameserver.CreateNameServerDTO;
import org.apache.rocketmq.studio.cluster.nameserver.DeleteNameServerDTO;
import org.apache.rocketmq.studio.cluster.nameserver.NameServerVO;
import org.apache.rocketmq.studio.cluster.nameserver.RestartNameServerDTO;
import org.apache.rocketmq.studio.cluster.nameserver.UpdateNameServerDTO;
import org.apache.rocketmq.studio.cluster.nameserver.UpgradeNameServerDTO;
import org.apache.rocketmq.studio.cluster.proxy.ProxyVO;
import org.apache.rocketmq.studio.cluster.proxy.RestartProxyDTO;

import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.apache.rocketmq.studio.common.domain.enums.ClusterType;
import org.apache.rocketmq.studio.common.domain.enums.FlushDiskType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.apache.rocketmq.studio.provider.apache.RocketMQBrokerConfigService;
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
import org.assertj.core.api.ThrowableAssert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClusterServiceTest {

    @Mock
    private ClusterRepository clusterRepository;

    @Mock
    private ClusterProvider clusterProvider;

    @Mock
    private RocketMQBrokerConfigService brokerConfigService;

    @Mock
    private AuditService auditService;

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
                .brokers(List.of(BrokerVO.builder()
                        .name("broker-0")
                        .addr("10.0.0.1:10911")
                        .build()))
                .proxies(List.of(ProxyVO.builder()
                        .addr("10.0.0.10:8081")
                        .build()))
                .nameServers(List.of(NameServerVO.builder()
                        .addr("10.0.0.20:9876")
                        .build()))
                .config(ClusterConfigVO.builder()
                        .flushDiskType(FlushDiskType.ASYNC_FLUSH)
                        .writeQueueNums(8)
                        .readQueueNums(8)
                        .maxMessageSize(4194304)
                        .msgTraceTopicName("RMQ_SYS_TRACE_TOPIC")
                        .autoCreateTopicEnable(true)
                        .autoCreateSubscriptionGroup(true)
                        .deleteWhen("04")
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

        when(clusterProvider.discoverClusters()).thenReturn(Arrays.asList(sampleCluster, secondCluster));

        List<ClusterVO> result = clusterService.listClusters();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("test-cluster");
        assertThat(result.get(1).getName()).isEqualTo("second-cluster");
        verify(clusterRepository, never()).findAll();
    }

    @Test
    void listClustersShouldUseSelectedInstance() {
        when(clusterProvider.discoverClusters("instance-1")).thenReturn(List.of(sampleCluster));

        List<ClusterVO> result = clusterService.listClusters("instance-1");

        assertThat(result).containsExactly(sampleCluster);
        verify(clusterProvider).discoverClusters("instance-1");
        verify(clusterProvider, never()).discoverClusters();
    }

    @Test
    void updateClusterConfigShouldRejectDifferentDefaultReadAndWriteQueueNums() {
        UpdateConfigDTO command = UpdateConfigDTO.builder()
                .id("cluster-1")
                .writeQueueNums(8)
                .readQueueNums(16)
                .build();

        assertThatThrownBy(() -> clusterService.updateClusterConfig(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("requires matching writeQueueNums and readQueueNums");

        verifyNoInteractions(clusterRepository, clusterProvider);
    }

    @Test
    void listClustersShouldReturnEmptyListWhenNoClusters() {
        when(clusterProvider.discoverClusters()).thenReturn(Collections.emptyList());

        List<ClusterVO> result = clusterService.listClusters();

        assertThat(result).isEmpty();
    }

    @Test
    void getClusterShouldReturnClusterWhenFound() {
        when(clusterProvider.refreshClusterDetail("cluster-1")).thenReturn(sampleCluster);

        ClusterVO result = clusterService.getCluster("cluster-1");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("cluster-1");
        assertThat(result.getName()).isEqualTo("test-cluster");
        assertThat(result.getStatus()).isEqualTo(ClusterStatus.healthy);
        assertThat(result.getType()).isEqualTo(ClusterType.V5_PROXY_CLUSTER);
    }

    @Test
    void getClusterShouldUseSelectedInstance() {
        when(clusterProvider.refreshClusterDetail("cluster-1", "instance-1")).thenReturn(sampleCluster);

        ClusterVO result = clusterService.getCluster("cluster-1", "instance-1");

        assertThat(result).isSameAs(sampleCluster);
        verify(clusterProvider).refreshClusterDetail("cluster-1", "instance-1");
        verify(clusterProvider, never()).refreshClusterDetail("cluster-1");
    }

    @Test
    void getClusterShouldThrowWhenNotFound() {
        when(clusterProvider.refreshClusterDetail("nonexistent")).thenReturn(null);

        assertThatThrownBy(() -> clusterService.getCluster("nonexistent"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cluster details are unavailable: nonexistent")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(503));
    }

    @Test
    void listProxiesShouldUseResolvedCluster() {
        when(clusterProvider.refreshClusterDetail("cluster-1")).thenReturn(sampleCluster);

        List<ProxyVO> proxies = clusterService.listProxies("cluster-1");

        assertThat(proxies).extracting(ProxyVO::getAddr).containsExactly("10.0.0.10:8081");
    }

    @Test
    void requireProxyShouldRejectUnknownAddress() {
        when(clusterProvider.refreshClusterDetail("cluster-1")).thenReturn(sampleCluster);

        assertThatThrownBy(() -> clusterService.requireProxy("cluster-1", "127.0.0.1:8081"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Proxy not found: 127.0.0.1:8081")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));
    }

    @Test
    void updateConfigShouldUpdateFlushDiskType() {
        when(clusterRepository.findById("cluster-1")).thenReturn(Optional.of(sampleCluster));

        UpdateConfigDTO command = UpdateConfigDTO.builder()
                .id("cluster-1")
                .flushDiskType("SYNC_FLUSH")
                .build();

        ClusterConfigUpdateResultVO result = clusterService.updateClusterConfig(command);

        assertThat(result.getCluster().getConfig().getFlushDiskType()).isEqualTo(FlushDiskType.SYNC_FLUSH);
        verify(clusterRepository).updateConfig(eq("cluster-1"), any(ClusterConfigVO.class));
    }

    @Test
    void updateConfigShouldSucceedWhenAuditRecordingFails() {
        when(clusterRepository.findById("cluster-1")).thenReturn(Optional.of(sampleCluster));
        doThrow(new IllegalStateException("audit storage unavailable")).when(auditService)
                .record(any(), any(), any(), any(), any());

        ClusterConfigUpdateResultVO result = clusterService.updateClusterConfig(UpdateConfigDTO.builder()
                .id("cluster-1")
                .flushDiskType("SYNC_FLUSH")
                .build());

        assertThat(result.getStatus()).isEqualTo(ClusterConfigUpdateResultVO.Status.SUCCESS);
        verify(clusterRepository).updateConfig(eq("cluster-1"), any(ClusterConfigVO.class));
    }

    @Test
    void updateConfigShouldFailWhenNoBrokerAddressIsAvailable() {
        sampleCluster.setBrokers(List.of());
        when(clusterRepository.findById("cluster-1")).thenReturn(Optional.of(sampleCluster));

        ClusterConfigUpdateResultVO result = clusterService.updateClusterConfig(UpdateConfigDTO.builder()
                .id("cluster-1")
                .flushDiskType("SYNC_FLUSH")
                .build());

        assertThat(result.getStatus()).isEqualTo(ClusterConfigUpdateResultVO.Status.FAILED);
        assertThat(result.getSuccessfulBrokers()).isEmpty();
        assertThat(result.getFailedBrokers()).singleElement().satisfies(failure -> {
            assertThat(failure.getAddress()).isEqualTo("N/A");
            assertThat(failure.getMessage()).contains("No broker address");
        });
        verify(clusterRepository, never()).updateConfig(eq("cluster-1"), any());
        verifyNoInteractions(brokerConfigService);
        verify(auditService).record(
                eq("UPDATE_CLUSTER_CONFIG"),
                eq("CLUSTER:cluster-1"),
                eq("cluster-1"),
                org.mockito.ArgumentMatchers.contains("No broker address"),
                eq("FAILED"));
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

        ClusterConfigUpdateResultVO result = clusterService.updateClusterConfig(command);

        ClusterConfigVO config = result.getCluster().getConfig();
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
        ClusterConfigVO storedConfig = sampleCluster.getConfig();

        UpdateConfigDTO command = UpdateConfigDTO.builder()
                .id("cluster-1")
                .flushDiskType("SYNC_FLUSH")
                .build();

        ClusterConfigUpdateResultVO result = clusterService.updateClusterConfig(command);

        ClusterConfigVO config = result.getCluster().getConfig();
        assertThat(config).isNotSameAs(storedConfig);
        assertThat(config.getFlushDiskType()).isEqualTo(FlushDiskType.SYNC_FLUSH);
        assertThat(config.getWriteQueueNums()).isEqualTo(8);
        assertThat(config.getReadQueueNums()).isEqualTo(8);
        assertThat(config.getMaxMessageSize()).isEqualTo(4194304);
        assertThat(config.getMsgTraceTopicName()).isEqualTo("RMQ_SYS_TRACE_TOPIC");
        assertThat(config.isAutoCreateTopicEnable()).isTrue();
        assertThat(config.isAutoCreateSubscriptionGroup()).isTrue();
        assertThat(config.getDeleteWhen()).isEqualTo("04");
        assertThat(config.getFileReservedTime()).isEqualTo(72);
        assertThat(config.getBrokerPermission()).isEqualTo(6);
        assertThat(storedConfig.getFlushDiskType()).isEqualTo(FlushDiskType.ASYNC_FLUSH);
    }

    @Test
    void updateConfigShouldReportPartialFailureAfterOneBrokerSucceeds() {
        sampleCluster.setBrokers(List.of(
                BrokerVO.builder().name("broker-0").addr("10.0.0.1:10911").build(),
                BrokerVO.builder().name("broker-1").addr("10.0.0.2:10911").build()));
        when(clusterRepository.findById("cluster-1")).thenReturn(Optional.of(sampleCluster));
        doNothing().when(brokerConfigService).updateBrokerConfig(
                eq("10.0.0.1:10911"), eq("cluster-1"), any());
        doThrow(new BusinessException(500, "broker unavailable"))
                .when(brokerConfigService).updateBrokerConfig(
                        eq("10.0.0.2:10911"), eq("cluster-1"), any());

        ClusterConfigUpdateResultVO result = clusterService.updateClusterConfig(
                UpdateConfigDTO.builder().id("cluster-1").writeQueueNums(16).build());

        assertThat(result.getStatus()).isEqualTo(ClusterConfigUpdateResultVO.Status.PARTIAL);
        assertThat(result.getSuccessfulBrokers()).containsExactly("10.0.0.1:10911");
        assertThat(result.getFailedBrokers()).singleElement().satisfies(failure -> {
            assertThat(failure.getAddress()).isEqualTo("10.0.0.2:10911");
            assertThat(failure.getMessage()).contains("broker unavailable");
        });
        verify(brokerConfigService).updateBrokerConfig(
                eq("10.0.0.1:10911"), eq("cluster-1"), any());
        verify(brokerConfigService).updateBrokerConfig(
                eq("10.0.0.2:10911"), eq("cluster-1"), any());
        verify(clusterRepository, never()).updateConfig(eq("cluster-1"), any());
        verify(auditService).record(
                eq("UPDATE_CLUSTER_CONFIG"),
                eq("CLUSTER:cluster-1"),
                eq("cluster-1"),
                org.mockito.ArgumentMatchers.contains("10.0.0.2:10911"),
                eq("PARTIAL"));
    }

    @Test
    void updateConfigShouldUseSelectedInstanceForBrokerUpdates() {
        sampleCluster.setBrokers(List.of(BrokerVO.builder().name("broker-0").addr("10.0.0.1:10911").build()));
        when(clusterProvider.refreshClusterDetail("cluster-1", "instance-1")).thenReturn(sampleCluster);

        ClusterConfigUpdateResultVO result = clusterService.updateClusterConfig(UpdateConfigDTO.builder()
                .id("cluster-1")
                .instanceId("instance-1")
                .writeQueueNums(16)
                .build());

        assertThat(result.getStatus()).isEqualTo(ClusterConfigUpdateResultVO.Status.SUCCESS);
        verify(clusterProvider).refreshClusterDetail("cluster-1", "instance-1");
        verify(brokerConfigService).updateBrokerConfig(
                eq("10.0.0.1:10911"), eq("cluster-1"), eq("instance-1"), any());
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
    void updateConfigShouldNotFallBackToPersistedClusterWhenLiveRefreshFails() {
        when(clusterProvider.refreshClusterDetail("cluster-1"))
                .thenThrow(new BusinessException(502, "NameServer unavailable"));

        UpdateConfigDTO command = UpdateConfigDTO.builder()
                .id("cluster-1")
                .flushDiskType("SYNC_FLUSH")
                .build();

        assertThatThrownBy(() -> clusterService.updateClusterConfig(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("NameServer unavailable")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(502));
        verify(clusterRepository, never()).findById("cluster-1");
    }

    @Test
    void updateConfigShouldRejectInvalidFlushDiskType() {
        when(clusterRepository.findById("cluster-1")).thenReturn(Optional.of(sampleCluster));

        UpdateConfigDTO command = UpdateConfigDTO.builder()
                .id("cluster-1")
                .flushDiskType("INVALID_FLUSH")
                .build();

        assertThatThrownBy(() -> clusterService.updateClusterConfig(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid flushDiskType: INVALID_FLUSH")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(clusterRepository, never()).updateConfig(eq("cluster-1"), any(ClusterConfigVO.class));
    }

    @Test
    void updateConfigShouldCreateConfigWhenNull() {
        ClusterVO clusterWithNullConfig = ClusterVO.builder()
                .name("null-config-cluster")
                .status(ClusterStatus.healthy)
                .brokers(List.of(BrokerVO.builder().addr("10.0.0.1:10911").build()))
                .config(null)
                .build();
        clusterWithNullConfig.setId("cluster-nc");

        when(clusterRepository.findById("cluster-nc")).thenReturn(Optional.of(clusterWithNullConfig));

        UpdateConfigDTO command = UpdateConfigDTO.builder()
                .id("cluster-nc")
                .flushDiskType("ASYNC_FLUSH")
                .build();

        ClusterConfigUpdateResultVO result = clusterService.updateClusterConfig(command);

        assertThat(result.getCluster().getConfig()).isNotNull();
        assertThat(result.getCluster().getConfig().getFlushDiskType()).isEqualTo(FlushDiskType.ASYNC_FLUSH);
    }

    @Test
    void updateConfigShouldNotMutateStoredConfigWhenRepositoryUpdateFails() {
        ClusterConfigVO storedConfig = sampleCluster.getConfig();
        RuntimeException persistenceFailure = new RuntimeException("persistence failed");
        when(clusterRepository.findById("cluster-1")).thenReturn(Optional.of(sampleCluster));
        doThrow(persistenceFailure).when(clusterRepository)
                .updateConfig(eq("cluster-1"), any(ClusterConfigVO.class));

        UpdateConfigDTO command = UpdateConfigDTO.builder()
                .id("cluster-1")
                .flushDiskType("SYNC_FLUSH")
                .writeQueueNums(16)
                .build();

        assertThatThrownBy(() -> clusterService.updateClusterConfig(command))
                .isSameAs(persistenceFailure);
        assertThat(sampleCluster.getConfig()).isSameAs(storedConfig);
        assertThat(storedConfig.getFlushDiskType()).isEqualTo(FlushDiskType.ASYNC_FLUSH);
        assertThat(storedConfig.getWriteQueueNums()).isEqualTo(8);
    }

    @Test
    void updateConfigShouldLeaveNullStoredConfigWhenRepositoryUpdateFails() {
        ClusterVO clusterWithNullConfig = ClusterVO.builder()
                .name("null-config-cluster")
                .status(ClusterStatus.healthy)
                .brokers(List.of(BrokerVO.builder().addr("10.0.0.1:10911").build()))
                .config(null)
                .build();
        clusterWithNullConfig.setId("cluster-nc");
        RuntimeException persistenceFailure = new RuntimeException("persistence failed");
        when(clusterRepository.findById("cluster-nc")).thenReturn(Optional.of(clusterWithNullConfig));
        doThrow(persistenceFailure).when(clusterRepository)
                .updateConfig(eq("cluster-nc"), any(ClusterConfigVO.class));

        UpdateConfigDTO command = UpdateConfigDTO.builder()
                .id("cluster-nc")
                .flushDiskType("ASYNC_FLUSH")
                .build();

        assertThatThrownBy(() -> clusterService.updateClusterConfig(command))
                .isSameAs(persistenceFailure);
        assertThat(clusterWithNullConfig.getConfig()).isNull();
    }

    @Test
    void restartBrokerShouldThrowUnsupportedWhenBrokerExists() {
        when(clusterRepository.findById("cluster-1")).thenReturn(Optional.of(sampleCluster));

        assertUnsupportedOperation(() -> clusterService.restartBroker("cluster-1", "broker-0"),
                "Broker restart is not implemented");
    }

    @Test
    void restartBrokerShouldThrowWhenClusterNotFound() {
        when(clusterRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clusterService.restartBroker("missing", "broker-0"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cluster not found: missing");
    }

    @Test
    void restartBrokerShouldThrowWhenBrokerNotFound() {
        when(clusterRepository.findById("cluster-1")).thenReturn(Optional.of(sampleCluster));

        assertThatThrownBy(() -> clusterService.restartBroker("cluster-1", "missing-broker"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Broker not found: missing-broker")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));
    }

    @Test
    void createNameServerShouldThrowUnsupportedWhenClusterExists() {
        when(clusterRepository.findById("cluster-1")).thenReturn(Optional.of(sampleCluster));
        CreateNameServerDTO command = CreateNameServerDTO.builder()
                .clusterId("cluster-1")
                .addr("10.0.0.21:9876")
                .build();

        assertUnsupportedOperation(() -> clusterService.createNameServer(command),
                "NameServer create is not implemented");
    }

    @Test
    void nameServerOperationsShouldThrowUnsupportedWhenNameServerExists() {
        when(clusterRepository.findById("cluster-1")).thenReturn(Optional.of(sampleCluster));
        UpdateNameServerDTO update = UpdateNameServerDTO.builder()
                .clusterId("cluster-1")
                .addr("10.0.0.20:9876")
                .build();
        RestartNameServerDTO restart = RestartNameServerDTO.builder()
                .clusterId("cluster-1")
                .addr("10.0.0.20:9876")
                .build();
        UpgradeNameServerDTO upgrade = UpgradeNameServerDTO.builder()
                .clusterId("cluster-1")
                .addr("10.0.0.20:9876")
                .targetVersion("5.3.0")
                .build();
        DeleteNameServerDTO delete = DeleteNameServerDTO.builder()
                .clusterId("cluster-1")
                .addr("10.0.0.20:9876")
                .build();

        assertUnsupportedOperation(() -> clusterService.updateNameServer(update),
                "NameServer update is not implemented");
        assertUnsupportedOperation(() -> clusterService.restartNameServer(restart),
                "NameServer restart is not implemented");
        assertUnsupportedOperation(() -> clusterService.upgradeNameServer(upgrade),
                "NameServer upgrade is not implemented");
        assertUnsupportedOperation(() -> clusterService.deleteNameServer(delete),
                "NameServer delete is not implemented");
    }

    @Test
    void nameServerOperationsShouldRejectNullCommand() {
        assertThatThrownBy(() -> clusterService.createNameServer((CreateNameServerDTO) null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("NameServer request is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> clusterService.updateNameServer(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("NameServer request is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> clusterService.restartNameServer(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("NameServer request is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> clusterService.upgradeNameServer(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("NameServer request is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> clusterService.deleteNameServer(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("NameServer request is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verifyNoInteractions(clusterRepository);
    }

    @Test
    void restartProxyShouldThrowUnsupportedWhenProxyExists() {
        when(clusterRepository.findById("cluster-1")).thenReturn(Optional.of(sampleCluster));
        RestartProxyDTO command = RestartProxyDTO.builder()
                .clusterId("cluster-1")
                .addr("10.0.0.10:8081")
                .build();

        assertUnsupportedOperation(() -> clusterService.restartProxy(command),
                "Proxy restart is not implemented");
    }

    @Test
    void updateNameServerShouldThrowWhenNameServerNotFound() {
        when(clusterRepository.findById("cluster-1")).thenReturn(Optional.of(sampleCluster));
        UpdateNameServerDTO command = UpdateNameServerDTO.builder()
                .clusterId("cluster-1")
                .addr("missing:9876")
                .build();

        assertThatThrownBy(() -> clusterService.updateNameServer(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("NameServer not found: missing:9876")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));
    }

    @Test
    void restartNameServerShouldThrowWhenNameServerNotFound() {
        when(clusterRepository.findById("cluster-1")).thenReturn(Optional.of(sampleCluster));
        RestartNameServerDTO command = RestartNameServerDTO.builder()
                .clusterId("cluster-1")
                .addr("missing:9876")
                .build();

        assertThatThrownBy(() -> clusterService.restartNameServer(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("NameServer not found: missing:9876")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));
    }

    @Test
    void upgradeNameServerShouldThrowWhenNameServerNotFound() {
        when(clusterRepository.findById("cluster-1")).thenReturn(Optional.of(sampleCluster));
        UpgradeNameServerDTO command = UpgradeNameServerDTO.builder()
                .clusterId("cluster-1")
                .addr("missing:9876")
                .targetVersion("5.3.0")
                .build();

        assertThatThrownBy(() -> clusterService.upgradeNameServer(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("NameServer not found: missing:9876")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));
    }

    @Test
    void deleteNameServerShouldThrowWhenNameServerNotFound() {
        when(clusterRepository.findById("cluster-1")).thenReturn(Optional.of(sampleCluster));
        DeleteNameServerDTO command = DeleteNameServerDTO.builder()
                .clusterId("cluster-1")
                .addr("missing:9876")
                .build();

        assertThatThrownBy(() -> clusterService.deleteNameServer(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("NameServer not found: missing:9876")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));
    }

    @Test
    void restartProxyShouldThrowWhenProxyNotFound() {
        when(clusterRepository.findById("cluster-1")).thenReturn(Optional.of(sampleCluster));
        RestartProxyDTO command = RestartProxyDTO.builder()
                .clusterId("cluster-1")
                .addr("missing:8081")
                .build();

        assertThatThrownBy(() -> clusterService.restartProxy(command))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Proxy not found: missing:8081")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));
    }

    @Test
    void updateClusterConfigShouldMirrorSingleQueueNumsUpdate() {
        when(clusterProvider.refreshClusterDetail("cluster-1")).thenReturn(sampleCluster);
        UpdateConfigDTO command = UpdateConfigDTO.builder()
                .id("cluster-1")
                .writeQueueNums(12)
                .build();

        ClusterConfigUpdateResultVO result = clusterService.updateClusterConfig(command);

        // The broker exposes a single defaultTopicQueueNums property, so the stored write and read
        // queue counts must stay in sync after a partial update.
        assertThat(result.getCluster().getConfig().getWriteQueueNums()).isEqualTo(12);
        assertThat(result.getCluster().getConfig().getReadQueueNums()).isEqualTo(12);
    }

    @Test
    void updateClusterConfigShouldUseLiveClusterWhenItIsNotPersisted() {
        when(clusterProvider.refreshClusterDetail("cluster-1")).thenReturn(sampleCluster);
        UpdateConfigDTO command = UpdateConfigDTO.builder()
                .id("cluster-1")
                .maxMessageSize(8_388_608)
                .build();

        ClusterConfigUpdateResultVO result = clusterService.updateClusterConfig(command);

        assertThat(result.getCluster().getConfig().getMaxMessageSize()).isEqualTo(8_388_608);
        assertThat(result.getStatus()).isEqualTo(ClusterConfigUpdateResultVO.Status.SUCCESS);
        verify(clusterRepository).updateConfig("cluster-1", result.getCluster().getConfig());
    }

    private void assertUnsupportedOperation(ThrowableAssert.ThrowingCallable callable, String message) {
        assertThatThrownBy(callable)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(message)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(501));
    }
}
