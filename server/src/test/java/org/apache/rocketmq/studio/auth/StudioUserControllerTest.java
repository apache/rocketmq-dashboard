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

import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.persistence.entity.RmqStudioUser;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudioUserController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "studio.auth.login-required=false")
class StudioUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private SettingsRepository settingsRepository;

    @BeforeEach
    void disableLoginForControllerSlice() {
        when(settingsRepository.loadGeneralSettings())
                .thenReturn(GeneralSettingsVO.builder().requireLogin(false).build());
    }

    @Test
    void listReturnsAFilteredPageWithoutPasswordHashes() throws Exception {
        RmqStudioUser user = new RmqStudioUser();
        user.setId(7L);
        user.setUsername("operator");
        user.setPasswordHash("must-not-be-exposed");
        user.setAdmin(false);
        user.setEnabled(true);
        user.setGmtCreate(LocalDateTime.parse("2026-08-22T08:00:00"));
        when(authService.listUsers("oper", false, true, 2, 20))
                .thenReturn(PageResult.of(List.of(user), 21, 2, 20));

        mockMvc.perform(get("/api/studio-users")
                        .param("search", "oper")
                        .param("admin", "false")
                        .param("enabled", "true")
                        .param("page", "2")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(7))
                .andExpect(jsonPath("$.data.items[0].username").value("operator"))
                .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.total").value(21))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(20));

        verify(authService).listUsers("oper", false, true, 2, 20);
    }

    @Test
    void listUsesBoundedDefaults() throws Exception {
        when(authService.listUsers(null, null, null, 1, 20))
                .thenReturn(PageResult.empty(1, 20));

        mockMvc.perform(get("/api/studio-users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20));
    }
}
