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
package com.rocketmq.studio.cluster.nameserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketmq.studio.cluster.broker.ClusterService;
import com.rocketmq.studio.common.domain.enums.ClusterStatus;
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
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.success").value(true));

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
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.success").value(true));

        verify(clusterService).deleteNameServer(any(DeleteNameServerDTO.class));
    }
}
