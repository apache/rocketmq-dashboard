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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    void listCredentialsShouldForwardFiltersAndDefaults() throws Exception {
        PageResult<CloudCredentialVO> page = PageResult.of(List.of(), 0, 1, 20);
        when(credentialService.listMasked(eq(InstanceVendor.ALIYUN), eq("orders"), eq(2), eq(50)))
                .thenReturn(page);

        mockMvc.perform(get("/api/cloud-credentials")
                        .param("vendor", "ALIYUN")
                        .param("search", "orders")
                        .param("page", "2")
                        .param("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(credentialService).listMasked(InstanceVendor.ALIYUN, "orders", 2, 50);
    }

    @Test
    void listCredentialsShouldApplyDefaultPaging() throws Exception {
        when(credentialService.listMasked(null, null, 1, 20)).thenReturn(PageResult.of(List.of(), 0, 1, 20));

        mockMvc.perform(get("/api/cloud-credentials"))
                .andExpect(status().isOk());

        verify(credentialService).listMasked(null, null, 1, 20);
    }

    @Test
    void createCredentialShouldRejectAMissingBody() throws Exception {
        mockMvc.perform(post("/api/cloud-credentials/create")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cloud credential request is required"));

        verify(credentialService, org.mockito.Mockito.never())
                .create(any(CloudCredentialVO.class));
    }

    @Test
    void createCredentialShouldDelegateTheValidatedRequest() throws Exception {
        CloudCredentialVO created = new CloudCredentialVO();
        created.setId(3L);
        created.setName("production");
        when(credentialService.create(any(CloudCredentialVO.class))).thenReturn(created);

        mockMvc.perform(post("/api/cloud-credentials/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"production","vendor":"ALIYUN",
                                 "accessKey":"ak-1","secretKey":"sk-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(3));

        org.mockito.ArgumentCaptor<CloudCredentialVO> captor =
                org.mockito.ArgumentCaptor.forClass(CloudCredentialVO.class);
        verify(credentialService).create(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getName()).isEqualTo("production");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getVendor())
                .isEqualTo(InstanceVendor.ALIYUN);
    }

    @Test
    void updateCredentialShouldDelegateTheValidatedRequest() throws Exception {
        CloudCredentialVO updated = new CloudCredentialVO();
        updated.setId(9L);
        when(credentialService.update(any(UpdateCloudCredentialDTO.class))).thenReturn(updated);

        mockMvc.perform(post("/api/cloud-credentials/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":9,\"secretKey\":\"rotated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9));

        org.mockito.ArgumentCaptor<UpdateCloudCredentialDTO> captor =
                org.mockito.ArgumentCaptor.forClass(UpdateCloudCredentialDTO.class);
        verify(credentialService).update(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getId()).isEqualTo(9L);
    }

    @Test
    void deleteCredentialShouldParseAndDelegateTheId() throws Exception {
        mockMvc.perform(post("/api/cloud-credentials/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"7\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(credentialService).delete(7L);
    }
}
