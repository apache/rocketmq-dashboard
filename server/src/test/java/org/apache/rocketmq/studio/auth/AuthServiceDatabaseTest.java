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
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.persistence.entity.RmqStudioSession;
import org.apache.rocketmq.studio.persistence.entity.RmqStudioUser;
import org.apache.rocketmq.studio.persistence.mapper.RmqStudioSessionMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqStudioUserMapper;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceDatabaseTest {

    private AuthService authService;
    private RmqStudioUserMapper userMapper;
    private RmqStudioSessionMapper sessionMapper;
    private PasswordHasher passwordHasher;

    @BeforeEach
    void setUp() {
        AuthProperties authProperties = new AuthProperties();
        SettingsRepository settingsRepository = mock(SettingsRepository.class);
        GeneralSettingsVO settings = GeneralSettingsVO.builder().sessionTimeout(30).build();
        when(settingsRepository.loadGeneralSettings()).thenReturn(settings);
        userMapper = mock(RmqStudioUserMapper.class);
        sessionMapper = mock(RmqStudioSessionMapper.class);
        passwordHasher = new PasswordHasher();
        authService = new AuthService(authProperties, settingsRepository,
                Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC), userMapper,
                sessionMapper, passwordHasher);
    }

    @Test
    void databaseLoginPersistsOnlyTokenHashAndReturnsImmutableUserId() {
        RmqStudioUser user = user("id-1", "operator", true, true, "password-1");
        when(userMapper.selectCount(isNull())).thenReturn(1L);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        LoginDTO request = new LoginDTO();
        request.setUsername("operator");
        request.setPassword("password-1");

        LoginVO result = authService.login(request);

        assertThat(result.getUser().getUserId()).isEqualTo("id-1");
        assertThat(result.getToken()).startsWith("studio-jwt-");
        org.mockito.ArgumentCaptor<RmqStudioSession> captor =
                org.mockito.ArgumentCaptor.forClass(RmqStudioSession.class);
        verify(sessionMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("id-1");
        assertThat(captor.getValue().getTokenHash()).doesNotContain(result.getToken());
        assertThat(captor.getValue().getTokenHash()).hasSize(64);
    }

    @Test
    void passwordChangeRevokesExistingSessions() {
        RmqStudioUser user = user("id-1", "operator", false, true, "password-1");
        when(userMapper.selectById("id-1")).thenReturn(user);

        authService.changePassword("id-1", "password-1", "password-2", true);

        verify(userMapper).update(isNull(), any(Wrapper.class));
        verify(sessionMapper).update(isNull(), any(Wrapper.class));
    }

    @Test
    void disablingLastEnabledAdministratorIsRejected() {
        RmqStudioUser user = user("id-1", "admin", true, true, "password-1");
        when(userMapper.selectById("id-1")).thenReturn(user);
        when(userMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> authService.setUserEnabled("id-1", false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("The last enabled administrator cannot be disabled");
    }

    @Test
    void databaseAuthenticationThrottlesLastSeenWrites() {
        RmqStudioUser user = user("id-1", "operator", false, true, "password-1");
        RmqStudioSession session = activeSession("session-1", "id-1",
                LocalDateTime.parse("2026-08-13T00:00:00"));
        when(sessionMapper.selectOne(any(Wrapper.class))).thenReturn(session);
        when(userMapper.selectById("id-1")).thenReturn(user);

        assertThat(authService.isAuthenticated("Bearer token-1")).isTrue();

        verify(sessionMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void databaseAuthenticationUpdatesStaleLastSeenTimestamp() {
        RmqStudioUser user = user("id-1", "operator", false, true, "password-1");
        RmqStudioSession session = activeSession("session-1", "id-1",
                LocalDateTime.parse("2026-08-12T23:54:59"));
        when(sessionMapper.selectOne(any(Wrapper.class))).thenReturn(session);
        when(userMapper.selectById("id-1")).thenReturn(user);

        assertThat(authService.isAuthenticated("Bearer token-1")).isTrue();

        verify(sessionMapper).update(isNull(), any(Wrapper.class));
    }

    @Test
    void databaseBootstrapIgnoresConcurrentUserCreation() {
        AuthProperties.User configuredUser = new AuthProperties.User();
        configuredUser.setUsername("operator");
        configuredUser.setPassword("password-1");
        AuthProperties properties = new AuthProperties();
        properties.setUsers(List.of(configuredUser));
        RmqStudioUser user = user("id-1", "operator", false, true, "password-1");
        when(userMapper.selectCount(isNull())).thenReturn(0L);
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);
        when(userMapper.insert(any(RmqStudioUser.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate username"));
        SettingsRepository databaseSettingsRepository = mock(SettingsRepository.class);
        when(databaseSettingsRepository.loadGeneralSettings())
                .thenReturn(GeneralSettingsVO.builder().sessionTimeout(30).build());
        authService = new AuthService(properties, databaseSettingsRepository,
                Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC), userMapper,
                sessionMapper, passwordHasher);

        LoginDTO request = new LoginDTO();
        request.setUsername("operator");
        request.setPassword("password-1");

        assertThat(authService.login(request).getUser().getUsername()).isEqualTo("operator");
    }

    private RmqStudioSession activeSession(String id, String userId, LocalDateTime lastSeenAt) {
        RmqStudioSession session = new RmqStudioSession();
        session.setId(id);
        session.setUserId(userId);
        session.setLastSeenAt(lastSeenAt);
        session.setExpiresAt(LocalDateTime.parse("2026-08-14T00:00:00"));
        return session;
    }

    private RmqStudioUser user(String id, String username, boolean admin, boolean enabled, String password) {
        RmqStudioUser user = new RmqStudioUser();
        user.setId(id);
        user.setUsername(username);
        user.setAdmin(admin);
        user.setEnabled(enabled);
        user.setPasswordHash(passwordHasher.hash(password));
        return user;
    }
}
