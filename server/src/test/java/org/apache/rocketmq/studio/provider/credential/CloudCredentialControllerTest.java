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
package org.apache.rocketmq.studio.provider.credential;

import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CloudCredentialController.class)
@AutoConfigureMockMvc(addFilters = false)
class CloudCredentialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CloudCredentialService credentialService;

    @Test
    void getCredentialSecretsShouldDisableResponseCaching() throws Exception {
        CloudCredentialVO credentials = new CloudCredentialVO();
        credentials.setId(1L);
        credentials.setAccessKey("access-key");
        credentials.setSecretKey("secret-key");
        when(credentialService.reveal(1L)).thenReturn(credentials);

        mockMvc.perform(get("/api/cloud-credentials/1/credentials"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    @Test
    void listCredentialsShouldPassFiltersAndDefaults() throws Exception {
        when(credentialService.listMasked(InstanceVendor.ALIYUN, "prod", 1, 20))
                .thenReturn(PageResult.empty(1, 20));

        mockMvc.perform(get("/api/cloud-credentials")
                        .param("vendor", "ALIYUN")
                        .param("search", "prod"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items").isEmpty());

        verify(credentialService).listMasked(InstanceVendor.ALIYUN, "prod", 1, 20);
    }

    @Test
    void createCredentialShouldRejectMissingBody() throws Exception {
        mockMvc.perform(post("/api/cloud-credentials/create")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void createCredentialShouldDelegateWithParsedVendor() throws Exception {
        CloudCredentialVO created = new CloudCredentialVO();
        created.setId(5L);
        created.setName("prod-tencent");
        created.setVendor(InstanceVendor.TENCENT);
        when(credentialService.create(any(CloudCredentialVO.class))).thenReturn(created);

        mockMvc.perform(post("/api/cloud-credentials/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"prod-tencent\",\"vendor\":\"tencent\","
                                + "\"accessKey\":\"ak-1\",\"secretKey\":\"sk-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.name").value("prod-tencent"));

        verify(credentialService).create(any(CloudCredentialVO.class));
    }
}
