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
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.apache.rocketmq.studio.persistence.entity.RmqStudioSession;
import org.apache.rocketmq.studio.persistence.entity.RmqStudioUser;
import org.apache.rocketmq.studio.persistence.mapper.RmqStudioSessionMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqStudioUserMapper;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A session token must not stay usable for its whole absolute lifetime when it has not been used:
 * the deadline the console was missing is an idle window, so a leaked token from an abandoned
 * browser is worthless after that window instead of a day later.
 */
class AuthServiceIdleTimeoutTest {

    private static final Instant NOW = Instant.parse("2026-08-13T12:00:00Z");
    private static final LocalDateTime NOW_UTC = LocalDateTime.parse("2026-08-13T12:00:00");
    private static final LocalDateTime ABSOLUTE_EXPIRY = LocalDateTime.parse("2026-08-14T00:00:00");
    private static final String TOKEN = "studio-jwt-idle-window";

    private AuthProperties authProperties;
    private RmqStudioUserMapper userMapper;
    private RmqStudioSessionMapper sessionMapper;
    private AuthService authService;

    @BeforeEach
    void setUpDatabaseBackedService() {
        authProperties = new AuthProperties();
        SettingsRepository settingsRepository = mock(SettingsRepository.class);
        when(settingsRepository.loadGeneralSettings())
                .thenReturn(GeneralSettingsVO.builder().sessionTimeout(1440).build());
        userMapper = mock(RmqStudioUserMapper.class);
        sessionMapper = mock(RmqStudioSessionMapper.class);
        when(userMapper.selectById(1L)).thenReturn(enabledUser(1L));
        authService = new AuthService(authProperties, settingsRepository,
                Clock.fixed(NOW, ZoneOffset.UTC), userMapper, sessionMapper, new PasswordHasher());
    }

    @Test
    void tokenUnusedBeyondTheIdleWindowIsRejectedAndRevokedBeforeItsAbsoluteExpiry() {
        givenActiveSession(10L, NOW_UTC.minusMinutes(60));

        assertThat(authService.getAuthenticatedUser("Bearer " + TOKEN)).isEmpty();

        UpdateWrapper<RmqStudioSession> revoked = theOnlySessionUpdate();
        assertThat(revoked.getSqlSet()).contains("revoked_at");
        assertThat(revoked.getSqlSegment()).contains("id");
    }

    @Test
    void tokenUsedInsideTheIdleWindowStaysAuthenticatedAndRefreshesLastSeen() {
        givenActiveSession(11L, NOW_UTC.minusMinutes(10));

        assertThat(authService.getAuthenticatedUser("Bearer " + TOKEN)).isPresent();

        assertThat(theOnlySessionUpdate().getSqlSet()).contains("last_seen_at");
    }

    @Test
    void tokenInsideTheLastSeenThrottleIsNeitherRewrittenNorRevoked() {
        givenActiveSession(12L, NOW_UTC.minusMinutes(4));

        assertThat(authService.getAuthenticatedUser("Bearer " + TOKEN)).isPresent();

        verify(sessionMapper, never()).update(isNull(), any(Wrapper.class));
    }

    @Test
    void idleTimeoutZeroLeavesTheAbsoluteTimeoutAsTheOnlyDeadline() {
        configureIdleTimeoutMinutes(0);
        givenActiveSession(13L, NOW_UTC.minusHours(6));

        assertThat(authService.getAuthenticatedUser("Bearer " + TOKEN)).isPresent();

        assertThat(theOnlySessionUpdate().getSqlSet()).contains("last_seen_at");
    }

    @Test
    void anIdleWindowBelowTheLastSeenThrottleResolvesToTheMinimumOfTenMinutes() {
        configureIdleTimeoutMinutes(1);
        givenActiveSession(14L, NOW_UTC.minusMinutes(7));

        assertThat(authService.getAuthenticatedUser("Bearer " + TOKEN)).isPresent();

        givenActiveSession(15L, NOW_UTC.minusMinutes(11));

        assertThat(authService.getAuthenticatedUser("Bearer " + TOKEN)).isEmpty();

        List<UpdateWrapper<RmqStudioSession>> updates = sessionUpdates();
        assertThat(updates).hasSize(2);
        assertThat(updates.get(0).getSqlSet()).contains("last_seen_at");
        assertThat(updates.get(1).getSqlSet()).contains("revoked_at");
    }

    @Test
    void sessionsWithoutLastSeenKeepOnlyTheAbsoluteDeadline() {
        RmqStudioSession session = new RmqStudioSession();
        session.setId(16L);
        session.setUserId(1L);
        session.setExpiresAt(ABSOLUTE_EXPIRY);
        when(sessionMapper.selectOne(any(Wrapper.class))).thenReturn(session);

        assertThat(authService.getAuthenticatedUser("Bearer " + TOKEN)).isPresent();

        assertThat(theOnlySessionUpdate().getSqlSet()).contains("last_seen_at");
    }

    @Test
    void scheduledCleanupRevokesIdleSessionsAndStillDeletesAbsolutelyExpiredRows() {
        authService.purgeExpiredSessions();

        UpdateWrapper<RmqStudioSession> revoked = theOnlySessionUpdate();
        assertThat(revoked.getSqlSet()).contains("revoked_at");
        assertThat(revoked.getSqlSegment())
                .contains("revoked_at IS NULL", "last_seen_at", "expires_at");
        assertThat(revoked.getParamNameValuePairs().values()).contains(NOW_UTC.minusMinutes(30));

        ArgumentCaptor<QueryWrapper<RmqStudioSession>> expired =
                ArgumentCaptor.forClass(QueryWrapper.class);
        verify(sessionMapper).delete(expired.capture());
        assertThat(expired.getValue().getSqlSegment()).contains("expires_at");
    }

    @Test
    void inMemorySessionsAlsoExpireOnceTheirTokenStaysIdle() {
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(NOW.toEpochMilli());
        AuthService inMemoryService = inMemoryService(clock);
        String token = loginInMemory(inMemoryService);
        assertThat(inMemoryService.isAuthenticated("Bearer " + token)).isTrue();

        when(clock.millis()).thenReturn(NOW.plus(Duration.ofMinutes(31)).toEpochMilli());

        assertThat(inMemoryService.isAuthenticated("Bearer " + token)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void inMemoryCleanupDropsTokensThatIdledOut() {
        Clock clock = mock(Clock.class);
        when(clock.millis()).thenReturn(NOW.toEpochMilli());
        AuthService inMemoryService = inMemoryService(clock);
        loginInMemory(inMemoryService);

        when(clock.millis()).thenReturn(NOW.plus(Duration.ofMinutes(31)).toEpochMilli());
        inMemoryService.purgeExpiredSessions();

        assertThat((Map<String, ?>) ReflectionTestUtils.getField(inMemoryService, "activeTokens"))
                .isEmpty();
    }

    private AuthService inMemoryService(Clock clock) {
        AuthProperties.User configuredUser = new AuthProperties.User();
        configuredUser.setUsername("operator");
        configuredUser.setPassword("password-1");
        authProperties.setUsers(List.of(configuredUser));
        return new AuthService(authProperties, mock(SettingsRepository.class), clock);
    }

    private String loginInMemory(AuthService service) {
        LoginDTO request = new LoginDTO();
        request.setUsername("operator");
        request.setPassword("password-1");
        return service.login(request).getToken();
    }

    private void givenActiveSession(Long id, LocalDateTime lastSeenAt) {
        RmqStudioSession session = new RmqStudioSession();
        session.setId(id);
        session.setUserId(1L);
        session.setLastSeenAt(lastSeenAt);
        session.setExpiresAt(ABSOLUTE_EXPIRY);
        when(sessionMapper.selectOne(any(Wrapper.class))).thenReturn(session);
    }

    @SuppressWarnings("unchecked")
    private UpdateWrapper<RmqStudioSession> theOnlySessionUpdate() {
        ArgumentCaptor<UpdateWrapper<RmqStudioSession>> captor =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(sessionMapper, times(1)).update(isNull(), captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<UpdateWrapper<RmqStudioSession>> sessionUpdates() {
        ArgumentCaptor<UpdateWrapper<RmqStudioSession>> captor =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(sessionMapper, atLeastOnce()).update(isNull(), captor.capture());
        return captor.getAllValues();
    }

    private void configureIdleTimeoutMinutes(int minutes) {
        try {
            ReflectionTestUtils.setField(authProperties, "sessionIdleTimeoutMinutes", minutes);
        } catch (RuntimeException error) {
            throw new AssertionError("AuthProperties has no session idle timeout setting", error);
        }
    }

    private static RmqStudioUser enabledUser(Long id) {
        RmqStudioUser user = new RmqStudioUser();
        user.setId(id);
        user.setUsername("operator");
        user.setEnabled(true);
        user.setAdmin(false);
        return user;
    }
}
