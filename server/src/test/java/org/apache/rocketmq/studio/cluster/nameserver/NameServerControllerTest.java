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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.cluster.broker.ClusterService;
import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NameServerController.class)
@AutoConfigureMockMvc(addFilters = false)
class NameServerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ClusterService clusterService;

    @MockBean
    private NameServerConfigDiffService configDiffService;

    @MockBean
    private NameserverRegistryService registryService;

    @Test
    void listRegistryShouldReturnRegisteredNameserversTest() throws Exception {
        when(registryService.list()).thenReturn(java.util.List.of(
                NameserverRegistryVO.builder()
                        .id(1L)
                        .name("rocketmq1")
                        .namesrvAddr("rocketmq1-nameserver:9876")
                        .k8sNamespace("rocketmq1")
                        .status("healthy")
                        .build()));

        mockMvc.perform(get("/api/nameservers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].name").value("rocketmq1"))
                .andExpect(jsonPath("$.data[0].namesrvAddr").value("rocketmq1-nameserver:9876"))
                .andExpect(jsonPath("$.data[0].k8sNamespace").value("rocketmq1"));

        verify(registryService).list();
    }

    @Test
    void createRegistryEntryShouldReturnCreatedEntryTest() throws Exception {
        when(registryService.create(any(CreateNameserverRegistryDTO.class))).thenReturn(
                NameserverRegistryVO.builder()
                        .id(3L)
                        .name("rocketmq3")
                        .namesrvAddr("rocketmq3-nameserver:9876")
                        .k8sNamespace("rocketmq3")
                        .build());

        mockMvc.perform(post("/api/nameservers/registry/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateNameserverRegistryDTO.builder()
                                .name("rocketmq3")
                                .namesrvAddr("rocketmq3-nameserver:9876")
                                .k8sNamespace("rocketmq3")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(3))
                .andExpect(jsonPath("$.data.k8sNamespace").value("rocketmq3"));

        verify(registryService).create(any(CreateNameserverRegistryDTO.class));
    }

    @Test
    void createRegistryEntryShouldRejectBlankNameTest() throws Exception {
        mockMvc.perform(post("/api/nameservers/registry/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CreateNameserverRegistryDTO.builder()
                                .name(" ")
                                .namesrvAddr("10.0.0.1:9876")
                                .build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("name is required"));

        verifyNoInteractions(registryService);
    }

    @Test
    void updateRegistryEntryShouldReturnUpdatedEntryTest() throws Exception {
        when(registryService.update(any(UpdateNameserverRegistryDTO.class))).thenReturn(
                NameserverRegistryVO.builder()
                        .id(1L)
                        .name("rocketmq1")
                        .namesrvAddr("rocketmq1-nameserver.svc:9876")
                        .build());

        mockMvc.perform(post("/api/nameservers/registry/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateNameserverRegistryDTO.builder()
                                .id(1L)
                                .name("rocketmq1")
                                .namesrvAddr("rocketmq1-nameserver.svc:9876")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.namesrvAddr").value("rocketmq1-nameserver.svc:9876"));

        verify(registryService).update(any(UpdateNameserverRegistryDTO.class));
    }

    @Test
    void updateRegistryEntryShouldRejectMissingIdTest() throws Exception {
        mockMvc.perform(post("/api/nameservers/registry/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(UpdateNameserverRegistryDTO.builder()
                                .name("rocketmq1")
                                .namesrvAddr("x:9876")
                                .build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("id is required"));

        verifyNoInteractions(registryService);
    }

    @Test
    void deleteRegistryEntryShouldReturnOkTest() throws Exception {
        mockMvc.perform(post("/api/nameservers/registry/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(DeleteNameserverRegistryDTO.builder()
                                .id(1L)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(registryService).delete(1L);
    }

    @Test
    void deleteRegistryEntryShouldRejectMissingIdTest() throws Exception {
        mockMvc.perform(post("/api/nameservers/registry/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("id is required"));

        verifyNoInteractions(registryService);
    }

    @Test
    void compareConfigurationShouldReturnDriftResult() throws Exception {
        NameServerConfigDiffVO result = NameServerConfigDiffVO.builder()
                .cluster("cluster-1")
                .complete(true)
                .driftDetected(true)
                .nodeCount(2)
                .reachableNodeCount(2)
                .comparedKeys(java.util.List.of("listenPort"))
                .nodes(java.util.List.of())
                .differences(java.util.List.of())
                .build();
        when(configDiffService.compare("cluster-1", "instance-1")).thenReturn(result);

        mockMvc.perform(get("/api/nameservers/config-diff")
                        .param("clusterId", "cluster-1")
                        .param("instanceId", "instance-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.cluster").value("cluster-1"))
                .andExpect(jsonPath("$.data.driftDetected").value(true));

        verify(configDiffService).compare("cluster-1", "instance-1");
    }

    @Test
    void compareConfigurationShouldRejectMissingClusterId() throws Exception {
        when(configDiffService.compare(null, null)).thenThrow(
                new org.apache.rocketmq.studio.common.exception.BusinessException(
                        400, "cluster is required"));

        mockMvc.perform(get("/api/nameservers/config-diff"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("cluster is required"));

        verify(configDiffService).compare(null, null);
    }

    @Test
    void createNameServerShouldPassValidatedRequest() throws Exception {
        CreateNameServerDTO request = CreateNameServerDTO.builder()
                .clusterId("cluster-1")
                .addr("127.0.0.1:9876")
                .version("5.3.2")
                .build();
        NameServerVO created = NameServerVO.builder()
                .addr("127.0.0.1:9876")
                .status(ClusterStatus.healthy)
                .build();
        when(clusterService.createNameServer(any(CreateNameServerDTO.class))).thenReturn(created);

        mockMvc.perform(post("/api/nameservers/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.addr").value("127.0.0.1:9876"));

        verify(clusterService).createNameServer(any(CreateNameServerDTO.class));
    }

    @Test
    void nameServerWriteEndpointsShouldRejectNullRequestBody() throws Exception {
        String[] paths = {
            "/api/nameservers/create",
            "/api/nameservers/update",
            "/api/nameservers/restart",
            "/api/nameservers/upgrade",
            "/api/nameservers/delete"
        };

        for (String path : paths) {
            mockMvc.perform(post(path)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("null"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.message").value("NameServer request is required"));
        }

        verifyNoInteractions(clusterService);
    }

    @Test
    void updateNameServerShouldRejectBlankAddr() throws Exception {
        UpdateNameServerDTO request = UpdateNameServerDTO.builder()
                .clusterId("cluster-1")
                .addr(" ")
                .build();

        mockMvc.perform(post("/api/nameservers/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("addr is required"));

        verifyNoInteractions(clusterService);
    }

    @Test
    void restartNameServerShouldPassValidatedRequest() throws Exception {
        RestartNameServerDTO request = RestartNameServerDTO.builder()
                .clusterId("cluster-1")
                .addr("127.0.0.1:9876")
                .build();
        when(clusterService.restartNameServer(any(RestartNameServerDTO.class))).thenReturn(true);

        mockMvc.perform(post("/api/nameservers/restart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(clusterService).restartNameServer(any(RestartNameServerDTO.class));
    }

    @Test
    void restartNameServerShouldRejectMissingClusterId() throws Exception {
        RestartNameServerDTO request = RestartNameServerDTO.builder()
                .addr("127.0.0.1:9876")
                .build();

        mockMvc.perform(post("/api/nameservers/restart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("clusterId is required"));

        verifyNoInteractions(clusterService);
    }

    @Test
    void upgradeNameServerShouldRejectMissingTargetVersion() throws Exception {
        UpgradeNameServerDTO request = UpgradeNameServerDTO.builder()
                .clusterId("cluster-1")
                .addr("127.0.0.1:9876")
                .build();

        mockMvc.perform(post("/api/nameservers/upgrade")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("targetVersion is required"));

        verifyNoInteractions(clusterService);
    }

    @Test
    void deleteNameServerShouldPassValidatedRequest() throws Exception {
        DeleteNameServerDTO request = DeleteNameServerDTO.builder()
                .clusterId("cluster-1")
                .addr("127.0.0.1:9876")
                .build();
        when(clusterService.deleteNameServer(any(DeleteNameServerDTO.class))).thenReturn(true);

        mockMvc.perform(post("/api/nameservers/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(clusterService).deleteNameServer(any(DeleteNameServerDTO.class));
    }
}
