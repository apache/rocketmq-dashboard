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

import org.apache.rocketmq.studio.WebMvcAuthTestSupport;

import org.apache.rocketmq.studio.common.config.LegacyJackson2Config;
import org.springframework.context.annotation.Import;

import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.persistence.entity.RmqStudioUser;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StudioUserController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "studio.auth.login-required=false")
@Import(LegacyJackson2Config.class)
class StudioUserControllerTest extends WebMvcAuthTestSupport {

    @Autowired
    private MockMvc mockMvc;

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
        when(authService.listActiveSessionSummaries(List.of(7L)))
                .thenReturn(Map.of(7L, StudioUserSessionSummaryVO.builder()
                        .userId(7L)
                        .activeSessionCount(2)
                        .lastSessionSeenAt(LocalDateTime.parse("2026-08-22T09:30:00"))
                        .nearestSessionExpiresAt(LocalDateTime.parse("2026-08-22T10:00:00"))
                        .build()));

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
                .andExpect(jsonPath("$.data.items[0].activeSessionCount").value(2))
                .andExpect(jsonPath("$.data.items[0].lastSessionSeenAt")
                        .value("2026-08-22T09:30:00"))
                .andExpect(jsonPath("$.data.items[0].nearestSessionExpiresAt")
                        .value("2026-08-22T10:00:00"))
                .andExpect(jsonPath("$.data.total").value(21))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(20));

        verify(authService).listUsers("oper", false, true, 2, 20);
        verify(authService).listActiveSessionSummaries(List.of(7L));
    }

    @Test
    void listUsesBoundedDefaults() throws Exception {
        when(authService.listUsers(null, null, null, 1, 20))
                .thenReturn(PageResult.empty(1, 20));
        when(authService.listActiveSessionSummaries(List.of()))
                .thenReturn(Map.of());

        mockMvc.perform(get("/api/studio-users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    void sessionOverviewReturnsActiveSessionCounts() throws Exception {
        when(authService.getSessionOverview()).thenReturn(StudioUserSessionOverviewVO.builder()
                .activeSessionCount(5)
                .activeUserCount(3)
                .expiringSoonSessionCount(1)
                .staleSessionCount(2)
                .expiringSoonWindowMinutes(5)
                .staleSessionThresholdMinutes(15)
                .build());

        mockMvc.perform(get("/api/studio-users/sessions/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activeSessionCount").value(5))
                .andExpect(jsonPath("$.data.activeUserCount").value(3))
                .andExpect(jsonPath("$.data.expiringSoonSessionCount").value(1))
                .andExpect(jsonPath("$.data.staleSessionCount").value(2));

        verify(authService).getSessionOverview();
    }

    @Test
    void revokeSessionsReturnsTheRevokedSessionCount() throws Exception {
        when(authService.revokeSessionsForUser(7L)).thenReturn(3);

        mockMvc.perform(post("/api/studio-users/7/sessions/revoke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(7))
                .andExpect(jsonPath("$.data.revokedSessionCount").value(3));

        verify(authService).revokeSessionsForUser(7L);
    }
}
