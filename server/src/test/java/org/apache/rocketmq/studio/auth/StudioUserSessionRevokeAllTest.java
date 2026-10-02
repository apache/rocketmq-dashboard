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

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.common.config.LegacyJackson2Config;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.persistence.entity.RmqStudioSession;
import org.apache.rocketmq.studio.persistence.mapper.RmqStudioSessionMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqStudioUserMapper;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A suspected credential leak is answered by invalidating every live session at once. Walking the
 * user list drawer by drawer leaves the incident half-contained whenever the response is
 * interrupted, so the bulk revocation is one statement over the same active-session predicate the
 * per-user revoke already uses — sparing the sessions of the administrator who pulls the lever,
 * who would otherwise log themselves out in the middle of the response.
 */
@WebMvcTest(StudioUserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({LegacyJackson2Config.class, StudioUserSessionRevokeAllTest.SessionService.class})
class StudioUserSessionRevokeAllTest {

    private static final String REVOKE_ALL_PATH = "/api/studio-users/sessions/revoke-all";
    private static final String ADMIN_AUTHORIZATION = "Bearer root-admin-token";
    private static final String READER_AUTHORIZATION = "Bearer reader-token";
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-13T12:00:00Z"), ZoneOffset.UTC);

    @MockitoBean
    private AuthProperties authProperties;

    @MockitoBean
    private SettingsRepository settingsRepository;

    @MockitoBean
    private ToolCatalog toolCatalog;

    /**
     * Declared here rather than inherited: this slice has to build against both the tree that
     * contains only this change and the tree where the auth controllers already audit, and a
     * controller collaborator a slice does not declare is a context that cannot start.
     */
    @MockitoBean
    private OperationAuditService operationAuditService;

    @MockitoBean
    private RmqStudioUserMapper userMapper;

    @MockitoBean
    private RmqStudioSessionMapper sessionMapper;

    /**
     * The slice runs the production {@link AuthInterceptor} (it is a {@code WebMvcConfigurer} and
     * therefore part of a {@code @WebMvcTest}), so the operator has to arrive as an authenticated
     * principal; the service behind it stays real, because the statement the endpoint writes is
     * what this class is about.
     */
    @MockitoSpyBean
    private AuthService authService;

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void clearPrincipal() {
        AuthenticatedUserContext.clear();
    }

    @TestConfiguration
    static class SessionService {

        @Bean
        AuthService authService(AuthProperties authProperties, SettingsRepository settingsRepository,
                                RmqStudioUserMapper userMapper,
                                RmqStudioSessionMapper sessionMapper) {
            return new AuthService(authProperties, settingsRepository, CLOCK, userMapper, sessionMapper,
                    new PasswordHasher());
        }
    }

    @Test
    void revokingEverySessionSparesTheOperatorWhoPulledTheLever() throws Exception {
        when(authService.getAuthenticatedUser(ADMIN_AUTHORIZATION))
                .thenReturn(Optional.of(userInfo(1L, "root-admin", true)));
        when(sessionMapper.update(isNull(), any(Wrapper.class))).thenReturn(12);

        mockMvc.perform(post(REVOKE_ALL_PATH)
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.revokedSessionCount").value(12));

        UpdateWrapper<RmqStudioSession> update = theOnlySessionUpdate();
        assertThat(update.getSqlSet()).contains("revoked_at");
        assertThat(update.getSqlSegment())
                .as("the bulk revocation reuses the active-session predicate, minus the operator")
                .contains("revoked_at", "expires_at", "user_id");
        assertThat(update.getParamNameValuePairs().values()).contains(1L);
    }

    @Test
    void aCallWithoutAPrincipalRevokesEverySessionThereIs() throws Exception {
        when(authProperties.isLoginRequired()).thenReturn(false);
        when(settingsRepository.loadGeneralSettings())
                .thenReturn(GeneralSettingsVO.builder().requireLogin(false).build());
        when(sessionMapper.update(isNull(), any(Wrapper.class))).thenReturn(7);

        mockMvc.perform(post(REVOKE_ALL_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revokedSessionCount").value(7));

        assertThat(theOnlySessionUpdate().getSqlSegment())
                .as("nobody to spare, so the statement carries no user filter")
                .doesNotContain("user_id");
    }

    @Test
    void aReaderCannotReachTheBulkRevocation() throws Exception {
        when(authService.getAuthenticatedUser(READER_AUTHORIZATION))
                .thenReturn(Optional.of(userInfo(2L, "reader", false)));

        mockMvc.perform(post(REVOKE_ALL_PATH)
                        .header(HttpHeaders.AUTHORIZATION, READER_AUTHORIZATION))
                .andExpect(status().isForbidden());

        verify(sessionMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void theBulkRevocationKeepsThePerUserContractOfADatabaseBackedDeployment() {
        AuthService inMemoryService = new AuthService(new AuthProperties(), settingsRepository, CLOCK);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(inMemoryService,
                "revokeAllSessions", new Object[] {null}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires database persistence");
    }

    private UpdateWrapper<RmqStudioSession> theOnlySessionUpdate() {
        ArgumentCaptor<UpdateWrapper<RmqStudioSession>> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(sessionMapper).update(isNull(), captor.capture());
        return captor.getValue();
    }

    private static LoginVO.UserInfo userInfo(Long userId, String username, boolean admin) {
        return LoginVO.UserInfo.builder()
                .userId(userId)
                .username(username)
                .admin(admin)
                .build();
    }
}
