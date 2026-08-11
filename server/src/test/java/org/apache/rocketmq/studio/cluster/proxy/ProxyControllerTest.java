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
package org.apache.rocketmq.studio.cluster.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.cluster.broker.ClusterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProxyController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProxyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ClusterService clusterService;

    @MockBean
    private ProxyAddressService proxyAddressService;

    @Test
    void restartProxyShouldPassValidatedRequest() throws Exception {
        RestartProxyDTO request = RestartProxyDTO.builder()
                .clusterId("cluster-1")
                .addr("127.0.0.1:8081")
                .build();
        when(clusterService.restartProxy(any(RestartProxyDTO.class))).thenReturn(true);

        mockMvc.perform(post("/api/proxies/restart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(clusterService).restartProxy(any(RestartProxyDTO.class));
    }

    @Test
    void restartProxyShouldRejectMissingClusterId() throws Exception {
        RestartProxyDTO request = RestartProxyDTO.builder()
                .addr("127.0.0.1:8081")
                .build();

        mockMvc.perform(post("/api/proxies/restart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("clusterId is required"));

        verifyNoInteractions(clusterService);
    }

    @Test
    void restartProxyShouldRejectBlankAddr() throws Exception {
        RestartProxyDTO request = RestartProxyDTO.builder()
                .clusterId("cluster-1")
                .addr(" ")
                .build();

        mockMvc.perform(post("/api/proxies/restart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("addr is required"));

        verifyNoInteractions(clusterService);
    }

    @Test
    void listProxiesShouldReturnProxiesForCluster() throws Exception {
        when(proxyAddressService.getHomePage())
                .thenReturn(ProxyHomeVO.builder()
                        .proxyAddrList(List.of("127.0.0.1:8081"))
                        .currentProxyAddr("127.0.0.1:8081")
                        .build());

        mockMvc.perform(get("/api/proxies").param("clusterId", "cluster-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].addr").value("127.0.0.1:8081"));

        verify(proxyAddressService).getHomePage();
    }

    @Test
    void listProxiesShouldRejectMissingClusterId() throws Exception {
        mockMvc.perform(get("/api/proxies"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("clusterId is required"));
    }

    @Test
    void reloadProxyConfigShouldReturnSuccess() throws Exception {
        RestartProxyDTO request = RestartProxyDTO.builder()
                .clusterId("cluster-1")
                .addr("127.0.0.1:8081")
                .build();

        mockMvc.perform(post("/api/proxies/config/reload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.success").value(true));

        verify(proxyAddressService).reloadConfig("127.0.0.1:8081");
    }

    @Test
    void reloadProxyConfigShouldRejectMissingAddr() throws Exception {
        RestartProxyDTO request = RestartProxyDTO.builder()
                .clusterId("cluster-1")
                .build();

        mockMvc.perform(post("/api/proxies/config/reload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("addr is required"));

        verifyNoInteractions(proxyAddressService);
    }
}