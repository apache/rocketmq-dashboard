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

package com.rocketmq.studio.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OpsController.class)
@AutoConfigureMockMvc(addFilters = false)
class OpsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OpsService opsService;

    @Test
    void homePageShouldReturnOpsSettings() throws Exception {
        OpsHomeVO home = OpsHomeVO.builder()
                .namesvrAddrList(List.of("127.0.0.1:9876", "10.0.0.1:9876"))
                .currentNamesrv("127.0.0.1:9876")
                .useVIPChannel(true)
                .useTLS(false)
                .build();
        when(opsService.getHomePage()).thenReturn(home);

        mockMvc.perform(get("/api/ops/homePage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.namesvrAddrList[0]").value("127.0.0.1:9876"))
                .andExpect(jsonPath("$.data.currentNamesrv").value("127.0.0.1:9876"))
                .andExpect(jsonPath("$.data.useVIPChannel").value(true))
                .andExpect(jsonPath("$.data.useTLS").value(false));
    }

    @Test
    void updateNameSvrAddrShouldDelegateToService() throws Exception {
        mockMvc.perform(post("/api/ops/updateNameSvrAddr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("namesrvAddr", "10.0.0.1:9876"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(opsService).updateNameServer(eq("10.0.0.1:9876"));
    }

    @Test
    void updateNameSvrAddrShouldRejectMissingAddress() throws Exception {
        mockMvc.perform(post("/api/ops/updateNameSvrAddr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("namesrvAddr is required"));

        verifyNoInteractions(opsService);
    }

    @Test
    void addNameSvrAddrShouldDelegateToService() throws Exception {
        mockMvc.perform(post("/api/ops/addNameSvrAddr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("namesrvAddr", "10.0.0.2:9876"))))
                .andExpect(status().isOk());

        verify(opsService).addNameServer(eq("10.0.0.2:9876"));
    }

    @Test
    void addNameSvrAddrShouldRejectBlankAddress() throws Exception {
        mockMvc.perform(post("/api/ops/addNameSvrAddr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("namesrvAddr", " "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("namesrvAddr is required"));

        verifyNoInteractions(opsService);
    }

    @Test
    void updateVipChannelShouldDelegateToService() throws Exception {
        mockMvc.perform(post("/api/ops/updateIsVIPChannel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("useVIPChannel", false))))
                .andExpect(status().isOk());

        verify(opsService).updateVipChannel(false);
    }

    @Test
    void updateVipChannelShouldRejectMissingFlag() throws Exception {
        mockMvc.perform(post("/api/ops/updateIsVIPChannel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("useVIPChannel is required"));

        verifyNoInteractions(opsService);
    }

    @Test
    void updateUseTlsShouldDelegateToService() throws Exception {
        mockMvc.perform(post("/api/ops/updateUseTLS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("useTLS", true))))
                .andExpect(status().isOk());

        verify(opsService).updateUseTLS(true);
    }

    @Test
    void updateUseTlsShouldRejectMissingFlag() throws Exception {
        mockMvc.perform(post("/api/ops/updateUseTLS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("useTLS is required"));

        verifyNoInteractions(opsService);
    }
}
