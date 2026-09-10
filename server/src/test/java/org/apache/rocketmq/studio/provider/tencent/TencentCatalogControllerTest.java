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
package org.apache.rocketmq.studio.provider.tencent;

import org.apache.rocketmq.studio.provider.CloudInstanceOptionVO;
import org.apache.rocketmq.studio.provider.CloudRegionVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TencentCatalogController.class)
@AutoConfigureMockMvc(addFilters = false)
class TencentCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TencentCatalogService catalogService;

    @Test
    void listRegionsShouldForwardTheCredentialId() throws Exception {
        CloudRegionVO region = new CloudRegionVO();
        region.setRegionId("ap-guangzhou");
        when(catalogService.listRegions(9L)).thenReturn(List.of(region));

        mockMvc.perform(get("/api/cloud/tencent/regions").param("credentialId", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].regionId").value("ap-guangzhou"));

        verify(catalogService).listRegions(9L);
    }

    @Test
    void listInstancesShouldForwardTheCloudCoordinates() throws Exception {
        CloudInstanceOptionVO instance = new CloudInstanceOptionVO();
        instance.setInstanceId("rocketmq-xxx");
        when(catalogService.listCloudInstances(9L, "ap-guangzhou", "prod"))
                .thenReturn(List.of(instance));

        mockMvc.perform(get("/api/cloud/tencent/instances")
                        .param("credentialId", "9")
                        .param("regionId", "ap-guangzhou")
                        .param("search", "prod"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].instanceId").value("rocketmq-xxx"));

        verify(catalogService).listCloudInstances(9L, "ap-guangzhou", "prod");
    }

    @Test
    void listInstancesShouldWorkWithoutASearchFilter() throws Exception {
        when(catalogService.listCloudInstances(9L, "ap-guangzhou", null)).thenReturn(List.of());

        mockMvc.perform(get("/api/cloud/tencent/instances")
                        .param("credentialId", "9")
                        .param("regionId", "ap-guangzhou"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        verify(catalogService).listCloudInstances(9L, "ap-guangzhou", null);
    }

    @Test
    void listInstancesShouldRejectAMissingRegion() throws Exception {
        mockMvc.perform(get("/api/cloud/tencent/instances").param("credentialId", "9"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}
