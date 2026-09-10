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
import org.apache.rocketmq.studio.cluster.config.ClusterConfigUpdateResultVO;
import org.apache.rocketmq.studio.cluster.config.ClusterConfigPreviewVO;
import org.apache.rocketmq.studio.cluster.config.ClusterConfigVO;
import org.apache.rocketmq.studio.cluster.config.UpdateConfigDTO;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.apache.rocketmq.studio.common.domain.enums.ClusterType;
import org.apache.rocketmq.studio.common.domain.enums.FlushDiskType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClusterController.class)
@AutoConfigureMockMvc(addFilters = false)
class ClusterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ClusterService clusterService;

    @MockBean
    private ClusterConnectionService clusterConnectionService;

    @MockBean
    private BrokerConfigDiffService brokerConfigDiffService;

    @Test
    void listRegistryClustersShouldReturnDiscoveredClustersTest() throws Exception {
        when(clusterService.listRegistryClusters()).thenReturn(Collections.singletonList(
                ClusterVO.builder()
                        .id("DefaultCluster")
                        .name("rocketmq1")
                        .nsClusterName("DefaultCluster")
                        .endpoint("rocketmq1-nameserver:9876")
                        .status(ClusterStatus.healthy)
                        .build()));

        mockMvc.perform(get("/api/clusters/registry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("rocketmq1"))
                .andExpect(jsonPath("$.data[0].nsClusterName").value("DefaultCluster"))
                .andExpect(jsonPath("$.data[0].endpoint").value("rocketmq1-nameserver:9876"));

        verify(clusterService).listRegistryClusters();
    }

    @Test
    void connectionShouldReturnProbeResultTest() throws Exception {
        ClusterProbeResult probe = ClusterProbeResult.builder()
                .connected(true)
                .namesrvAddr("10.0.0.1:9876")
                .clusterName("DefaultCluster")
                .brokerCount(2)
                .brokerNames(Arrays.asList("broker-a", "broker-b"))
                .elapsedMillis(12L)
                .message("Connected to 2 broker(s) in 12ms")
                .build();
        when(clusterConnectionService.testConnection(any(TestConnectionDTO.class))).thenReturn(probe);

        ObjectNode command = objectMapper.createObjectNode().put("namesrvAddr", "10.0.0.1:9876");

        mockMvc.perform(post("/api/clusters/test-connection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.connected").value(true))
                .andExpect(jsonPath("$.data.clusterName").value("DefaultCluster"))
                .andExpect(jsonPath("$.data.brokerCount").value(2))
                .andExpect(jsonPath("$.data.brokerNames.length()").value(2));

        verify(clusterConnectionService).testConnection(any(TestConnectionDTO.class));
    }

    @Test
    void connectionShouldRejectBlankNamesrvAddrTest() throws Exception {
        ObjectNode command = objectMapper.createObjectNode().put("namesrvAddr", " ");

        mockMvc.perform(post("/api/clusters/test-connection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("namesrvAddr is required"));

        verifyNoInteractions(clusterConnectionService);
    }

    @Test
    void listClustersShouldReturnAllClusters() throws Exception {
        ClusterVO cluster1 = buildCluster("cluster-1", "production-cluster", ClusterStatus.healthy);
        ClusterVO cluster2 = buildCluster("cluster-2", "staging-cluster", ClusterStatus.warning);
        when(clusterService.listClusters(isNull())).thenReturn(Arrays.asList(cluster1, cluster2));

        mockMvc.perform(get("/api/clusters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value("cluster-1"))
                .andExpect(jsonPath("$.data[0].name").value("production-cluster"))
                .andExpect(jsonPath("$.data[0].status").value("healthy"))
                .andExpect(jsonPath("$.data[1].id").value("cluster-2"))
                .andExpect(jsonPath("$.data[1].name").value("staging-cluster"));
    }

    @Test
    void listClustersShouldReturnEmptyArrayWhenNoClusters() throws Exception {
        when(clusterService.listClusters(isNull())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/clusters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void listClustersShouldForwardTheSelectedInstance() throws Exception {
        when(clusterService.listClusters("instance-1")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/clusters").param("instanceId", "instance-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(clusterService).listClusters("instance-1");
    }

    @Test
    void getClusterShouldForwardTheSelectedInstance() throws Exception {
        ClusterVO cluster = buildCluster("cluster-1", "production-cluster", ClusterStatus.healthy);
        when(clusterService.getCluster("cluster-1", "instance-1")).thenReturn(cluster);

        mockMvc.perform(get("/api/clusters/cluster-1").param("instanceId", "instance-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("cluster-1"));

        verify(clusterService).getCluster("cluster-1", "instance-1");
    }

    @Test
    void getClusterShouldReturnClusterDetail() throws Exception {
        ClusterVO cluster = buildCluster("cluster-1", "production-cluster", ClusterStatus.healthy);
        cluster.setConfig(ClusterConfigVO.builder()
                .flushDiskType(FlushDiskType.SYNC_FLUSH)
                .writeQueueNums(8)
                .readQueueNums(8)
                .maxMessageSize(4194304)
                .autoCreateTopicEnable(true)
                .build());
        when(clusterService.getCluster("cluster-1", null)).thenReturn(cluster);

        mockMvc.perform(get("/api/clusters/cluster-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("cluster-1"))
                .andExpect(jsonPath("$.data.name").value("production-cluster"))
                .andExpect(jsonPath("$.data.status").value("healthy"))
                .andExpect(jsonPath("$.data.type").value("V5_PROXY_CLUSTER"))
                .andExpect(jsonPath("$.data.config.flushDiskType").value("SYNC_FLUSH"))
                .andExpect(jsonPath("$.data.config.writeQueueNums").value(8));
    }

    @Test
    void updateConfigShouldReturnUpdatedCluster() throws Exception {
        ClusterVO updated = buildCluster("cluster-1", "production-cluster", ClusterStatus.healthy);
        updated.setConfig(ClusterConfigVO.builder()
                .flushDiskType(FlushDiskType.SYNC_FLUSH)
                .writeQueueNums(16)
                .readQueueNums(16)
                .build());
        when(clusterService.updateClusterConfig(any(UpdateConfigDTO.class))).thenReturn(
                ClusterConfigUpdateResultVO.builder()
                        .cluster(updated)
                        .status(ClusterConfigUpdateResultVO.Status.SUCCESS)
                        .successfulBrokers(Collections.emptyList())
                        .failedBrokers(Collections.emptyList())
                        .build());

        UpdateConfigDTO command = UpdateConfigDTO.builder()
                .id("cluster-1")
                .flushDiskType("SYNC_FLUSH")
                .writeQueueNums(16)
                .readQueueNums(16)
                .build();

        mockMvc.perform(post("/api/clusters/config/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.cluster.id").value("cluster-1"))
                .andExpect(jsonPath("$.data.cluster.config.flushDiskType").value("SYNC_FLUSH"))
                .andExpect(jsonPath("$.data.cluster.config.writeQueueNums").value(16))
                .andExpect(jsonPath("$.data.cluster.config.readQueueNums").value(16));
    }

    @Test
    void previewConfigShouldReturnEffectiveBrokerChanges() throws Exception {
        ClusterVO cluster = buildCluster("cluster-1", "production-cluster", ClusterStatus.healthy);
        when(clusterService.previewClusterConfig(any(UpdateConfigDTO.class))).thenReturn(
                ClusterConfigPreviewVO.builder()
                        .cluster(cluster)
                        .targetBrokers(Collections.singletonList(
                                ClusterConfigPreviewVO.BrokerTargetVO.builder()
                                        .name("broker-0")
                                        .address("10.0.0.1:10911")
                                        .build()))
                        .brokerProperties(Collections.singletonMap("defaultTopicQueueNums", "16"))
                        .changes(Collections.singletonList(
                                ClusterConfigPreviewVO.ConfigChangeVO.builder()
                                        .field("writeQueueNums")
                                        .currentValue("8")
                                        .proposedValue("16")
                                        .brokerProperty("defaultTopicQueueNums")
                                        .build()))
                        .changed(true)
                        .build());

        UpdateConfigDTO command = UpdateConfigDTO.builder()
                .id("cluster-1")
                .writeQueueNums(16)
                .readQueueNums(16)
                .build();

        mockMvc.perform(post("/api/clusters/config/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.changed").value(true))
                .andExpect(jsonPath("$.data.targetBrokers[0].address").value("10.0.0.1:10911"))
                .andExpect(jsonPath("$.data.brokerProperties.defaultTopicQueueNums").value("16"))
                .andExpect(jsonPath("$.data.changes[0].field").value("writeQueueNums"))
                .andExpect(jsonPath("$.data.changes[0].brokerProperty").value("defaultTopicQueueNums"));

        verify(clusterService).previewClusterConfig(any(UpdateConfigDTO.class));
    }

    @Test
    void brokerConfigCompareShouldReturnDriftResultTest() throws Exception {
        when(brokerConfigDiffService.compare("cluster-1", "instance-1")).thenReturn(
                BrokerConfigDiffVO.builder()
                        .cluster("cluster-1")
                        .complete(true)
                        .driftDetected(true)
                        .brokerCount(2)
                        .reachableBrokerCount(2)
                        .comparedFields(Arrays.asList("flushDiskType", "writeQueueNums"))
                        .brokers(Arrays.asList(
                                BrokerConfigDiffVO.BrokerStatusVO.builder()
                                        .name("broker-a")
                                        .address("10.0.0.1:10911")
                                        .reachable(true)
                                        .build(),
                                BrokerConfigDiffVO.BrokerStatusVO.builder()
                                        .name("broker-b")
                                        .address("10.0.0.2:10911")
                                        .reachable(true)
                                        .build()))
                        .differences(Collections.singletonList(
                                BrokerConfigDiffVO.ConfigDifferenceVO.builder()
                                        .field("writeQueueNums")
                                        .brokerProperty("defaultTopicQueueNums")
                                        .values(Arrays.asList(
                                                BrokerConfigDiffVO.ConfigValueVO.builder()
                                                        .brokerName("broker-a")
                                                        .address("10.0.0.1:10911")
                                                        .configured(true)
                                                        .value("8")
                                                        .build(),
                                                BrokerConfigDiffVO.ConfigValueVO.builder()
                                                        .brokerName("broker-b")
                                                        .address("10.0.0.2:10911")
                                                        .configured(true)
                                                        .value("16")
                                                        .build()))
                                        .build()))
                        .build());

        mockMvc.perform(get("/api/clusters/cluster-1/broker-config-diff")
                        .param("instanceId", "instance-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.complete").value(true))
                .andExpect(jsonPath("$.data.driftDetected").value(true))
                .andExpect(jsonPath("$.data.brokerCount").value(2))
                .andExpect(jsonPath("$.data.reachableBrokerCount").value(2))
                .andExpect(jsonPath("$.data.brokers[0].address").value("10.0.0.1:10911"))
                .andExpect(jsonPath("$.data.differences[0].field").value("writeQueueNums"))
                .andExpect(jsonPath("$.data.differences[0].brokerProperty").value("defaultTopicQueueNums"))
                .andExpect(jsonPath("$.data.differences[0].values[1].value").value("16"));

        verify(brokerConfigDiffService).compare("cluster-1", "instance-1");
    }

    @Test
    void updateConfigShouldRejectNullRequestBody() throws Exception {
        mockMvc.perform(post("/api/clusters/config/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Cluster config update request is required"));

        verifyNoInteractions(clusterService);
    }

    @Test
    void updateConfigShouldRejectMissingId() throws Exception {
        UpdateConfigDTO command = UpdateConfigDTO.builder()
                .flushDiskType("SYNC_FLUSH")
                .writeQueueNums(16)
                .build();

        mockMvc.perform(post("/api/clusters/config/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("id is required"));

        verifyNoInteractions(clusterService);
    }

    @Test
    void updateConfigShouldRejectBlankId() throws Exception {
        UpdateConfigDTO command = UpdateConfigDTO.builder()
                .id(" ")
                .flushDiskType("SYNC_FLUSH")
                .build();

        mockMvc.perform(post("/api/clusters/config/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("id is required"));

        verifyNoInteractions(clusterService);
    }

    @ParameterizedTest(name = "{0}={1} should be rejected")
    @MethodSource("outOfRangeConfigValues")
    void updateConfigShouldRejectOutOfRangeValues(String field, int value, String expectedMessage)
            throws Exception {
        ObjectNode command = objectMapper.createObjectNode()
                .put("id", "cluster-1")
                .put(field, value);

        mockMvc.perform(post("/api/clusters/config/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(expectedMessage));

        verifyNoInteractions(clusterService);
    }

    @ParameterizedTest(name = "{0}={1} should be accepted")
    @MethodSource("boundaryConfigValues")
    void updateConfigShouldAcceptBoundaryValues(String field, int value) throws Exception {
        when(clusterService.updateClusterConfig(any(UpdateConfigDTO.class)))
                .thenReturn(successfulUpdateResult());
        ObjectNode command = objectMapper.createObjectNode()
                .put("id", "cluster-1")
                .put(field, value);

        mockMvc.perform(post("/api/clusters/config/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(clusterService).updateClusterConfig(any(UpdateConfigDTO.class));
    }

    @Test
    void updateConfigShouldAcceptIdOnlyPartialRequest() throws Exception {
        when(clusterService.updateClusterConfig(any(UpdateConfigDTO.class)))
                .thenReturn(successfulUpdateResult());
        ObjectNode command = objectMapper.createObjectNode().put("id", "cluster-1");

        mockMvc.perform(post("/api/clusters/config/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(clusterService).updateClusterConfig(any(UpdateConfigDTO.class));
    }

    @Test
    void restartBrokerShouldReturnSuccess() throws Exception {
        when(clusterService.restartBroker("cluster-1", "broker-0")).thenReturn(true);

        mockMvc.perform(post("/api/clusters/cluster-1/brokers/broker-0/restart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.message").value("Broker restart initiated for broker-0"));
    }

    private ClusterConfigUpdateResultVO successfulUpdateResult() {
        return ClusterConfigUpdateResultVO.builder()
                .cluster(buildCluster("cluster-1", "production-cluster", ClusterStatus.healthy))
                .status(ClusterConfigUpdateResultVO.Status.SUCCESS)
                .successfulBrokers(Collections.emptyList())
                .failedBrokers(Collections.emptyList())
                .build();
    }

    private ClusterVO buildCluster(String id, String name, ClusterStatus status) {
        ClusterVO cluster = ClusterVO.builder()
                .name(name)
                .type(ClusterType.V5_PROXY_CLUSTER)
                .endpoint("10.0.0.1:9876")
                .status(status)
                .version("5.1.0")
                .brokers(Collections.emptyList())
                .proxies(Collections.emptyList())
                .nameServers(Collections.emptyList())
                .topicCount(10)
                .groupCount(5)
                .build();
        cluster.setId(id);
        return cluster;
    }

    private static Stream<Arguments> outOfRangeConfigValues() {
        return Stream.of(
                Arguments.of("maxMessageSize", 1_048_575,
                        "maxMessageSize must be between 1048576 and 134217728"),
                Arguments.of("maxMessageSize", 134_217_729,
                        "maxMessageSize must be between 1048576 and 134217728"),
                Arguments.of("fileReservedTime", 0,
                        "fileReservedTime must be between 1 and 720"),
                Arguments.of("fileReservedTime", 721,
                        "fileReservedTime must be between 1 and 720"),
                Arguments.of("writeQueueNums", 0,
                        "writeQueueNums must be between 1 and 256"),
                Arguments.of("writeQueueNums", 257,
                        "writeQueueNums must be between 1 and 256"),
                Arguments.of("readQueueNums", 0,
                        "readQueueNums must be between 1 and 256"),
                Arguments.of("readQueueNums", 257,
                        "readQueueNums must be between 1 and 256"),
                Arguments.of("brokerPermission", -1,
                        "brokerPermission must be between 0 and 7"),
                Arguments.of("brokerPermission", 8,
                        "brokerPermission must be between 0 and 7")
        );
    }

    private static Stream<Arguments> boundaryConfigValues() {
        return Stream.of(
                Arguments.of("maxMessageSize", 1_048_576),
                Arguments.of("maxMessageSize", 134_217_728),
                Arguments.of("fileReservedTime", 1),
                Arguments.of("fileReservedTime", 720),
                Arguments.of("writeQueueNums", 1),
                Arguments.of("writeQueueNums", 256),
                Arguments.of("readQueueNums", 1),
                Arguments.of("readQueueNums", 256),
                Arguments.of("brokerPermission", 0),
                Arguments.of("brokerPermission", 7)
        );
    }
}
