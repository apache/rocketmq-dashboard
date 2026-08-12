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

import org.apache.rocketmq.studio.common.config.CorsConfig;
import org.apache.rocketmq.studio.instance.InstanceController;
import org.apache.rocketmq.studio.instance.InstanceCapabilityService;
import org.apache.rocketmq.studio.instance.InstanceService;
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
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = InstanceController.class, properties = "studio.auth.login-required=true")
@AutoConfigureMockMvc(addFilters = false)
@Import({AuthWebConfig.class, CorsConfig.class})
class AuthCorsIntegrationTest {

    private static final String FRONTEND_ORIGIN = "https://studio.example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InstanceService instanceService;

    @MockBean
    private InstanceCapabilityService instanceCapabilityService;

    @MockBean
    private AuthProperties authProperties;

    @MockBean
    private AuthService authService;

    @MockBean
    private SettingsRepository settingsRepository;

    @BeforeEach
    void enableLoginProtection() {
        when(authProperties.isLoginRequired()).thenReturn(true);
    }

    @Test
    void shouldAllowCorsPreflightWithoutAuthentication() throws Exception {
        mockMvc.perform(options("/api/instances")
                        .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"));

        verifyNoMoreInteractions(authService);
    }

    @Test
    void shouldStillRejectAnonymousProtectedRequests() throws Exception {
        mockMvc.perform(get("/api/instances")
                        .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN))
                .andExpect(status().isUnauthorized());

        verify(authService).isAuthenticated(null);
    }
    @Test
    void shouldRejectNonAdminMutationBeforeControllerExecution() throws Exception {
        String authorization = "Bearer reader-token";
        when(authService.isAuthenticated(authorization)).thenReturn(true);
        when(authService.isAdmin(authorization)).thenReturn(false);

        mockMvc.perform(post("/api/instances/create")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("Admin permission required"));

        verifyNoInteractions(instanceService);
    }

}
