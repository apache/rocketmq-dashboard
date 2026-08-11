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
package org.apache.rocketmq.studio.auth;

import org.apache.rocketmq.studio.instance.acl.AclController;
import org.apache.rocketmq.studio.instance.acl.AclService;
import org.apache.rocketmq.studio.provider.credential.CloudCredentialController;
import org.apache.rocketmq.studio.provider.credential.CloudCredentialService;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AclController.class, CloudCredentialController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(AuthWebConfig.class)
class AuthCredentialAuthorizationIntegrationTest {

    private static final String AUTHORIZATION = "Bearer reader-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AclService aclService;

    @MockBean
    private org.apache.rocketmq.studio.instance.acl.ApacheAclReadService apacheAclReadService;

    @MockBean
    private CloudCredentialService cloudCredentialService;

    @MockBean
    private AuthProperties authProperties;

    @MockBean
    private AuthService authService;

    @MockBean
    private SettingsRepository settingsRepository;

    @BeforeEach
    void authenticateReader() {
        when(authProperties.isLoginRequired()).thenReturn(true);
        when(authService.isAuthenticated(AUTHORIZATION)).thenReturn(true);
        when(authService.isAdmin(AUTHORIZATION)).thenReturn(false);
    }

    @Test
    void shouldRejectAclCredentialPathWithMatrixParameterForReader() throws Exception {
        mockMvc.perform(get("/api/acl/users/user-1/credentials;probe=1")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isForbidden());

        verifyNoInteractions(aclService);
    }

    @Test
    void shouldRejectCloudCredentialPathWithMatrixParameterForReader() throws Exception {
        mockMvc.perform(get("/api/cloud-credentials;probe=1/credential-1/credentials")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cloudCredentialService);
    }

    @Test
    void shouldAllowCredentialPathsWithMatrixParametersForAdministrator() throws Exception {
        when(authService.isAdmin(AUTHORIZATION)).thenReturn(true);

        mockMvc.perform(get("/api/acl/users/user-1/credentials;probe=1")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/cloud-credentials;probe=1/credential-1/credentials")
                        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION))
                .andExpect(status().isOk());

        verify(aclService).getUserCredentials("user-1");
        verify(cloudCredentialService).reveal("credential-1");
    }
}
