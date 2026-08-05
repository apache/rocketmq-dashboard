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

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private AuthService authService;
    private AuthProperties authProperties;
    private SettingsRepository settingsRepository;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        settingsRepository = mock(SettingsRepository.class);
        lenient().when(settingsRepository.loadGeneralSettings()).thenReturn(sessionSettings(30));
        authService = new AuthService(authProperties, settingsRepository, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    @Test
    void loginShouldReturnTokenForValidCredentials() {
        AuthProperties.User user = new AuthProperties.User();
        user.setUsername("testuser");
        user.setPassword("testpass");
        authProperties.setUsers(List.of(user));
        LoginDTO request = new LoginDTO();
        request.setUsername("testuser");
        request.setPassword("testpass");

        LoginVO response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).startsWith("studio-jwt-");
        assertThat(response.getExpiresIn()).isEqualTo(1800);
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getUsername()).isEqualTo("testuser");
        assertThat(response.getUser().isAdmin()).isFalse();
        assertThat(authService.isAuthenticated("Bearer " + response.getToken())).isTrue();
        assertThat(authService.isAuthenticated("bearer " + response.getToken())).isTrue();
        assertThat(authService.isAuthenticated("bEaReR " + response.getToken())).isTrue();
        assertThat(authService.getAuthenticatedUser("Bearer " + response.getToken()))
                .hasValueSatisfying(userInfo -> assertThat(userInfo.getUsername()).isEqualTo("testuser"));
        assertThat(authService.getAuthenticatedUser("Bearer unknown-token")).isEmpty();
        assertThat(authService.isAdmin("Bearer " + response.getToken())).isFalse();
    }

    @Test
    void loginShouldReturnAdminFlagForAdminUser() {
        AuthProperties.User user = new AuthProperties.User();
        user.setUsername("admin");
        user.setPassword("adminpass");
        user.setAdmin(true);
        authProperties.setUsers(List.of(user));
        LoginDTO request = new LoginDTO();
        request.setUsername("admin");
        request.setPassword("adminpass");

        LoginVO response = authService.login(request);

        assertThat(response.getUser().getUsername()).isEqualTo("admin");
        assertThat(response.getUser().isAdmin()).isTrue();
        assertThat(authService.isAdmin("Bearer " + response.getToken())).isTrue();
    }

    @Test
    void loginShouldUseConfiguredUsersWhenPresent() {
        AuthProperties.User user = new AuthProperties.User();
        user.setUsername("ops");
        user.setPassword("secret");
        user.setAdmin(true);
        authProperties.setUsers(List.of(user));

        LoginDTO request = new LoginDTO();
        request.setUsername("ops");
        request.setPassword("secret");

        LoginVO response = authService.login(request);

        assertThat(response.getUser().getUsername()).isEqualTo("ops");
        assertThat(response.getUser().isAdmin()).isTrue();
    }

    @Test
    void loginShouldRejectInvalidConfiguredUserPassword() {
        AuthProperties.User user = new AuthProperties.User();
        user.setUsername("ops");
        user.setPassword("secret");
        authProperties.setUsers(List.of(user));

        LoginDTO request = new LoginDTO();
        request.setUsername("ops");
        request.setPassword("wrong");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    void loginShouldRejectUnknownConfiguredUser() {
        AuthProperties.User user = new AuthProperties.User();
        user.setUsername("ops");
        user.setPassword("secret");
        authProperties.setUsers(List.of(user));

        LoginDTO request = new LoginDTO();
        request.setUsername("admin");
        request.setPassword("secret");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid username or password")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(401));
    }

    @Test
    void loginShouldRejectWithoutConfiguredUsers() {
        LoginDTO request = new LoginDTO();
        request.setUsername("admin");
        request.setPassword("secret");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("No valid login users are configured")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(503));
    }

    @Test
    void logoutShouldRevokeActiveTokenWithCaseInsensitiveBearerScheme() {
        AuthProperties.User user = new AuthProperties.User();
        user.setUsername("testuser");
        user.setPassword("testpass");
        authProperties.setUsers(List.of(user));
        LoginDTO request = new LoginDTO();
        request.setUsername("testuser");
        request.setPassword("testpass");
        LoginVO response = authService.login(request);

        authService.logout("bEaReR " + response.getToken());

        assertThat(authService.isAuthenticated("Bearer " + response.getToken())).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void scheduledCleanupShouldRemoveExpiredSessions() {
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(0L);
        authService = new AuthService(authProperties, settingsRepository, clock);
        AuthProperties.User user = new AuthProperties.User();
        user.setUsername("testuser");
        user.setPassword("testpass");
        authProperties.setUsers(List.of(user));
        LoginDTO request = new LoginDTO();
        request.setUsername("testuser");
        request.setPassword("testpass");
        LoginVO expiredSession = authService.login(request);
        when(clock.millis()).thenReturn(expiredSession.getExpiresIn() * 1000L);

        authService.purgeExpiredSessions();

        Map<String, ?> activeTokens = (Map<String, ?>) ReflectionTestUtils.getField(authService, "activeTokens");
        assertThat(activeTokens).isEmpty();
    }

    @Test
    void loginShouldUsePersistedSessionTimeout() {
        AuthProperties.User user = new AuthProperties.User();
        user.setUsername("testuser");
        user.setPassword("testpass");
        authProperties.setUsers(List.of(user));
        when(settingsRepository.loadGeneralSettings()).thenReturn(sessionSettings(45));
        LoginDTO request = new LoginDTO();
        request.setUsername("testuser");
        request.setPassword("testpass");

        LoginVO response = authService.login(request);

        assertThat(response.getExpiresIn()).isEqualTo(2700);
    }

    @Test
    void loginShouldFallBackToDefaultForInvalidPersistedSessionTimeout() {
        AuthProperties.User user = new AuthProperties.User();
        user.setUsername("testuser");
        user.setPassword("testpass");
        authProperties.setUsers(List.of(user));
        when(settingsRepository.loadGeneralSettings()).thenReturn(sessionSettings(0));
        LoginDTO request = new LoginDTO();
        request.setUsername("testuser");
        request.setPassword("testpass");

        LoginVO response = authService.login(request);

        assertThat(response.getExpiresIn()).isEqualTo(1800);
    }

    @Test
    void loginShouldRejectNullRequest() {
        assertThatThrownBy(() -> authService.login(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Login request is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
    }

    @Test
    void loginShouldThrowWhenUsernameIsNull() {
        LoginDTO request = new LoginDTO();
        request.setUsername(null);
        request.setPassword("password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Username is required");
    }

    @Test
    void loginShouldThrowWhenUsernameIsBlank() {
        LoginDTO request = new LoginDTO();
        request.setUsername("   ");
        request.setPassword("password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Username is required");
    }

    @Test
    void loginShouldThrowWhenPasswordIsNull() {
        LoginDTO request = new LoginDTO();
        request.setUsername("testuser");
        request.setPassword(null);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Password is required");
    }

    @Test
    void loginShouldThrowWhenPasswordIsBlank() {
        LoginDTO request = new LoginDTO();
        request.setUsername("testuser");
        request.setPassword("   ");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Password is required");
    }

    @Test
    void logoutShouldCompleteWithoutError() {
        authService.logout(null);
    }

    private GeneralSettingsVO sessionSettings(int minutes) {
        return GeneralSettingsVO.builder().sessionTimeout(minutes).build();
    }
}
