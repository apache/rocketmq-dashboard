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
package com.rocketmq.studio.cluster.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketmq.studio.cluster.broker.ClusterService;
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

@WebMvcTest(ProxyController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProxyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ClusterService clusterService;

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
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.success").value(true));

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
}
