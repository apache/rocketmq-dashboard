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

import com.rocketmq.studio.cluster.capability.ClusterCapability;
import com.rocketmq.studio.cluster.capability.ClusterCapabilityService;
import com.rocketmq.studio.cluster.capability.ClusterCapabilityVO;
import com.rocketmq.studio.cluster.capability.ClusterCapabilityVO.CapabilityStatus;
import com.rocketmq.studio.cluster.config.ClusterConfigVO;
import com.rocketmq.studio.cluster.config.UpdateConfigDTO;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketmq.studio.common.domain.enums.ClusterStatus;
import com.rocketmq.studio.common.domain.enums.ClusterType;
import com.rocketmq.studio.common.domain.enums.FlushDiskType;
import com.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
    private ClusterCapabilityService clusterCapabilityService;

    @Test
    void listClustersShouldReturnAllClusters() throws Exception {
        ClusterVO cluster1 = buildCluster("cluster-1", "production-cluster", ClusterStatus.healthy);
        ClusterVO cluster2 = buildCluster("cluster-2", "staging-cluster", ClusterStatus.warning);
        when(clusterService.listClusters()).thenReturn(Arrays.asList(cluster1, cluster2));

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
        when(clusterService.listClusters()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/clusters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
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
        when(clusterService.getCluster("cluster-1")).thenReturn(cluster);

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
    void getCapabilitiesShouldReturnNormalizedCapabilityContractWithoutSecrets() throws Exception {
        String unavailableReason = "Capability is not available on the v4 direct path";
        ClusterCapabilityVO capabilities = new ClusterCapabilityVO(
                "cluster-1",
                ClusterType.V4_DIRECT,
                Map.of(
                        ClusterCapability.LITE_TOPIC,
                        CapabilityStatus.unsupported(unavailableReason)
                )
        );
        when(clusterCapabilityService.getCapabilities("cluster-1")).thenReturn(capabilities);

        String responseBody = mockMvc.perform(get("/api/clusters/cluster-1/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.schemaVersion").value("v1"))
                .andExpect(jsonPath("$.data.clusterId").value("cluster-1"))
                .andExpect(jsonPath("$.data.accessType").value("v4-namesrv"))
                .andExpect(jsonPath("$.data.source").value("configured-default"))
                .andExpect(jsonPath("$.data.capabilities").isMap())
                .andExpect(jsonPath("$.data.capabilities.length()").value(13))
                .andExpect(jsonPath("$.data.capabilities['lite-topic'].state").value("unsupported"))
                .andExpect(jsonPath("$.data.capabilities['lite-topic'].reason").value(unavailableReason))
                .andExpect(jsonPath("$.data.capabilities['metadata-read'].state").value("unknown"))
                .andExpect(jsonPath("$.data.endpoint").doesNotExist())
                .andExpect(jsonPath("$.data.credentials").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .toLowerCase(Locale.ROOT);

        for (String sensitiveTerm : List.of(
                "endpoint",
                "nameserveraddress",
                "proxyaddress",
                "username",
                "password",
                "token",
                "accesskey",
                "secretkey",
                "credential",
                "super-secret-value")) {
            assertFalse(responseBody.contains(sensitiveTerm), "Response leaked sensitive term: " + sensitiveTerm);
        }
        verify(clusterCapabilityService).getCapabilities("cluster-1");
    }

    @Test
    void getCapabilitiesShouldPreserveBusinessErrorEnvelopeForMissingCluster() throws Exception {
        when(clusterCapabilityService.getCapabilities("missing"))
                .thenThrow(new BusinessException(404, "Cluster not found: missing"));

        mockMvc.perform(get("/api/clusters/missing/capabilities"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("Cluster not found: missing"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(clusterCapabilityService).getCapabilities("missing");
    }

    @Test
    void updateConfigShouldReturnUpdatedCluster() throws Exception {
        ClusterVO updated = buildCluster("cluster-1", "production-cluster", ClusterStatus.healthy);
        updated.setConfig(ClusterConfigVO.builder()
                .flushDiskType(FlushDiskType.SYNC_FLUSH)
                .writeQueueNums(16)
                .readQueueNums(16)
                .build());
        when(clusterService.updateClusterConfig(any(UpdateConfigDTO.class))).thenReturn(updated);

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
                .andExpect(jsonPath("$.data.id").value("cluster-1"))
                .andExpect(jsonPath("$.data.config.flushDiskType").value("SYNC_FLUSH"))
                .andExpect(jsonPath("$.data.config.writeQueueNums").value(16))
                .andExpect(jsonPath("$.data.config.readQueueNums").value(16));
    }

    @Test
    void restartBrokerShouldReturnSuccess() throws Exception {
        when(clusterService.restartBroker("cluster-1", "broker-0")).thenReturn(true);

        mockMvc.perform(post("/api/clusters/cluster-1/brokers/broker-0/restart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.message").value("Broker restart initiated for broker-0"));
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
}
