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
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.common.config.LegacyJackson2Config;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.persistence.entity.RmqOperationAudit;
import org.apache.rocketmq.studio.persistence.entity.RmqStudioUser;
import org.apache.rocketmq.studio.persistence.mapper.RmqOperationAuditMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The operation audit covered every business mutation but none of the authentication surface, so
 * the console could prove who changed a topic but not who logged in, who failed a login, who was
 * disabled or whose password was reset. These tests pin the rows the auth and user-management
 * endpoints have to leave in the audit table.
 */
@WebMvcTest({AuthController.class, StudioUserController.class})
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "studio.auth.login-required=false")
@Import({AuthWebConfig.class, LegacyJackson2Config.class, AuthAuditTrailTest.AuditSink.class})
class AuthAuditTrailTest extends WebMvcAuthTestSupport {

    @MockitoBean
    private RmqOperationAuditMapper auditMapper;

    private static final String ADMIN_AUTHORIZATION = "Bearer root-admin-token";

    @Autowired
    private MockMvc mockMvc;

    /**
     * The slice registers the production AuthInterceptor, so the acting operator has to arrive the
     * way it does in production: as an authenticated request principal.
     */
    @Override
    @BeforeEach
    protected void configureControllerSliceAuth() {
        super.configureControllerSliceAuth();
        when(authProperties.isLoginRequired()).thenReturn(true);
    }

    @AfterEach
    void clearPrincipal() {
        AuthenticatedUserContext.clear();
    }

    @TestConfiguration
    static class AuditSink {
        @Bean
        OperationAuditService operationAuditService(RmqOperationAuditMapper mapper) {
            return new OperationAuditService(mapper);
        }
    }

    @Test
    void successfulLoginIsRecordedWithTheAttemptedUsername() throws Exception {
        when(authService.login(any(LoginDTO.class))).thenReturn(loginResponse("alice"));

        mockMvc.perform(post("/api/auth/login")
                        .header(AuthCookie.SESSION_DELIVERY_HEADER, "bearer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password-1\"}"))
                .andExpect(status().isOk());

        RmqOperationAudit row = onlyRow("LOGIN");
        assertThat(row.getOperator()).isEqualTo("alice");
        assertThat(row.getResourceType()).isEqualTo("AUTH");
        assertThat(row.getResult()).isEqualTo("SUCCESS");
    }

    @Test
    void failedLoginIsRecordedWithTheMessageTheApiAnswered() throws Exception {
        when(authService.login(any(LoginDTO.class)))
                .thenThrow(new BusinessException(401, "Invalid username or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"mallory\",\"password\":\"guessed-secret\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));

        RmqOperationAudit row = onlyRow("LOGIN");
        assertThat(row.getOperator()).isEqualTo("mallory");
        assertThat(row.getResult()).isEqualTo("FAILURE");
        assertThat(row.getDetail()).contains("Invalid username or password");
        assertThat(row.getDetail()).doesNotContain("guessed-secret");
    }

    @Test
    void aRateLimitedLoginIsRecordedWithTheSameMessageTheApiAnswered() throws Exception {
        when(authService.login(any(LoginDTO.class)))
                .thenThrow(new BusinessException(429, "Too many failed attempts, try again later"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"mallory\",\"password\":\"guessed-secret\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message")
                        .value("Too many failed attempts, try again later"));

        RmqOperationAudit row = onlyRow("LOGIN");
        assertThat(row.getOperator()).isEqualTo("mallory");
        assertThat(row.getResult()).isEqualTo("FAILURE");
        assertThat(row.getDetail()).contains("Too many failed attempts");
    }

    @Test
    void logoutIsRecordedForTheOwnerOfTheToken() throws Exception {
        when(authService.getAuthenticatedUser("Bearer token-1"))
                .thenReturn(Optional.of(userInfo("alice")));

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-1"))
                .andExpect(status().isOk());

        RmqOperationAudit row = onlyRow("LOGOUT");
        assertThat(row.getOperator()).isEqualTo("alice");
        assertThat(row.getResult()).isEqualTo("SUCCESS");
        verify(authService).logout("Bearer token-1");
    }

    @Test
    void aSelfServicePasswordChangeIsRecordedWithoutThePassword() throws Exception {
        when(authService.getAuthenticatedUser("Bearer token-2"))
                .thenReturn(Optional.of(userInfo("alice")));

        mockMvc.perform(post("/api/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password-1\","
                                + "\"newPassword\":\"password-2\"}"))
                .andExpect(status().isOk());

        RmqOperationAudit row = onlyRow("UPDATE_OWN_PASSWORD");
        assertThat(row.getOperator()).isEqualTo("alice");
        assertThat(row.getResourceName()).isEqualTo("alice");
        assertThat(String.valueOf(row.getDetail())).doesNotContain("password-1", "password-2");
    }

    @Test
    void creatingAUserIsRecordedWithTheAffectedAccount() throws Exception {
        when(authService.getAuthenticatedUser(ADMIN_AUTHORIZATION)).thenReturn(Optional.of(adminInfo()));
        when(authService.createUser("alice", "password-1", true)).thenReturn(user(9L, "alice"));

        mockMvc.perform(post("/api/studio-users")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password-1\","
                                + "\"admin\":true}"))
                .andExpect(status().isOk());

        RmqOperationAudit row = onlyRow("CREATE_USER");
        assertThat(row.getOperator()).isEqualTo("root-admin");
        assertThat(row.getResourceName()).isEqualTo("alice");
        assertThat(row.getResult()).isEqualTo("SUCCESS");
        assertThat(row.getDetail()).contains("admin=true").doesNotContain("password-1");
    }

    @Test
    void disablingAnAccountIsRecordedWithTheOutcome() throws Exception {
        when(authService.getAuthenticatedUser(ADMIN_AUTHORIZATION)).thenReturn(Optional.of(adminInfo()));
        when(authService.setUserEnabled(9L, false)).thenReturn(user(9L, "alice"));

        mockMvc.perform(post("/api/studio-users/9/status")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        RmqOperationAudit row = onlyRow("UPDATE_USER_STATUS");
        assertThat(row.getOperator()).isEqualTo("root-admin");
        assertThat(row.getResourceName()).isEqualTo("alice");
        assertThat(row.getDetail()).contains("enabled=false");
    }

    @Test
    void anAdminPasswordResetIsRecordedWithTheAffectedAccount() throws Exception {
        when(authService.getAuthenticatedUser(ADMIN_AUTHORIZATION)).thenReturn(Optional.of(adminInfo()));

        mockMvc.perform(post("/api/studio-users/9/password")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"password-2\"}"))
                .andExpect(status().isOk());

        RmqOperationAudit row = onlyRow("RESET_USER_PASSWORD");
        assertThat(row.getOperator()).isEqualTo("root-admin");
        assertThat(row.getResourceName()).isEqualTo("9");
        assertThat(row.getResult()).isEqualTo("SUCCESS");
        assertThat(String.valueOf(row.getDetail())).doesNotContain("password-2");
    }

    @Test
    void revokingAUsersSessionsIsRecordedWithTheRevokedCount() throws Exception {
        when(authService.getAuthenticatedUser(ADMIN_AUTHORIZATION)).thenReturn(Optional.of(adminInfo()));
        when(authService.revokeSessionsForUser(9L)).thenReturn(3);

        mockMvc.perform(post("/api/studio-users/9/sessions/revoke")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN_AUTHORIZATION))
                .andExpect(status().isOk());

        RmqOperationAudit row = onlyRow("REVOKE_USER_SESSIONS");
        assertThat(row.getOperator()).isEqualTo("root-admin");
        assertThat(row.getResourceName()).isEqualTo("9");
        assertThat(row.getDetail()).contains("revoked=3");
    }

    @Test
    void anAuditSinkFailureDoesNotChangeTheLoginResponse() throws Exception {
        when(authService.login(any(LoginDTO.class))).thenReturn(loginResponse("alice"));
        when(auditMapper.insert(any(RmqOperationAudit.class)))
                .thenThrow(new IllegalStateException("audit table unavailable"));

        mockMvc.perform(post("/api/auth/login")
                        .header(AuthCookie.SESSION_DELIVERY_HEADER, "bearer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"password-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value("alice"))
                .andExpect(jsonPath("$.data.token").value("mock-jwt-alice"));
    }

    private RmqOperationAudit onlyRow(String operation) {
        List<RmqOperationAudit> rows = auditRows(operation);
        assertThat(rows).as("audit rows recorded for %s", operation).hasSize(1);
        return rows.get(0);
    }

    private List<RmqOperationAudit> auditRows(String operation) {
        ArgumentCaptor<RmqOperationAudit> captor =
                ArgumentCaptor.forClass(RmqOperationAudit.class);
        verify(auditMapper, atLeastOnce()).insert(captor.capture());
        return captor.getAllValues().stream()
                .filter(row -> operation.equals(row.getOperation()))
                .toList();
    }

    private static LoginVO loginResponse(String username) {
        return LoginVO.builder()
                .token("mock-jwt-" + username)
                .expiresIn(86400)
                .user(userInfo(username))
                .build();
    }

    private static LoginVO.UserInfo adminInfo() {
        return LoginVO.UserInfo.builder().userId(1L).username("root-admin").admin(true).build();
    }

    private static LoginVO.UserInfo userInfo(String username) {
        return LoginVO.UserInfo.builder().userId(5L).username(username).admin(false).build();
    }

    private static RmqStudioUser user(Long id, String username) {
        RmqStudioUser user = new RmqStudioUser();
        user.setId(id);
        user.setUsername(username);
        user.setAdmin(false);
        user.setEnabled(true);
        return user;
    }
}
