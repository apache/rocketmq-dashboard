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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProxyCompatController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProxyCompatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProxyAddressService proxyAddressService;

    @Test
    void homePageShouldReturnProxyAddressState() throws Exception {
        ProxyHomeVO home = ProxyHomeVO.builder()
                .proxyAddrList(List.of("127.0.0.1:8081", "10.0.0.1:8081"))
                .currentProxyAddr("127.0.0.1:8081")
                .build();
        when(proxyAddressService.getHomePage()).thenReturn(home);

        mockMvc.perform(get("/api/proxy/homePage.query"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.proxyAddrList[0]").value("127.0.0.1:8081"))
                .andExpect(jsonPath("$.data.currentProxyAddr").value("127.0.0.1:8081"));
    }

    @Test
    void addProxyAddrShouldAcceptFormEncodedPayload() throws Exception {
        mockMvc.perform(post("/api/proxy/addProxyAddr.do")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("newProxyAddr", "10.0.0.1:8081"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(proxyAddressService).addProxyAddr(eq("10.0.0.1:8081"));
    }
}
