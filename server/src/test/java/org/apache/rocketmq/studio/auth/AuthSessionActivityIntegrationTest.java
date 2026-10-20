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

import org.apache.rocketmq.studio.persistence.entity.RmqStudioSession;
import org.apache.rocketmq.studio.persistence.entity.RmqStudioUser;
import org.apache.rocketmq.studio.persistence.mapper.RmqStudioSessionMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqStudioUserMapper;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.AdditionalAnswers.delegatesTo;

@SpringBootTest
@ActiveProfiles("dev")
class AuthSessionActivityIntegrationTest {
    @Autowired
    private RmqStudioUserMapper userMapper;
    @Autowired
    private RmqStudioSessionMapper sessionMapper;
    @Autowired
    private SettingsRepository settingsRepository;
    @Autowired
    private PasswordHasher passwordHasher;

    @ParameterizedTest
    @ValueSource(ints = {300, 600})
    void delayedRefreshCannotOverwriteNewerSessionActivityTest(int activityAgeSeconds) throws Exception {
        Instant earlier = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant later = earlier.plusSeconds(60);
        RmqStudioUser user = new RmqStudioUser();
        user.setUsername("activity-" + UUID.randomUUID());
        user.setPasswordHash("unused-by-token-authentication");
        user.setEnabled(true);
        user.setAdmin(false);
        userMapper.insert(user);
        RmqStudioSession session = new RmqStudioSession();
        String token = UUID.randomUUID().toString();
        session.setUserId(user.getId());
        session.setTokenHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8))));
        session.setExpiresAt(LocalDateTime.ofInstant(later.plusSeconds(3600), ZoneOffset.UTC));
        LocalDateTime initialActivity = LocalDateTime.ofInstant(earlier.minusSeconds(activityAgeSeconds),
                ZoneOffset.UTC);
        session.setLastSeenAt(initialActivity);
        sessionMapper.insert(session);
        try {
            assertThat(sessionMapper.selectById(session.getId()).getLastSeenAt()).isEqualTo(initialActivity);
            RmqStudioSessionMapper delayedMapper = mock(RmqStudioSessionMapper.class, delegatesTo(sessionMapper));
            AuthService olderRequest = serviceAtTest(earlier, delayedMapper);
            AuthService newerRequest = serviceAtTest(later, sessionMapper);
            doAnswer(invocation -> {
                // Pause the older refresh just before SQL execution and let a newer
                // request read the same session and commit its refresh first.
                assertThat(newerRequest.getAuthenticatedUser("Bearer " + token)).isPresent();
                return sessionMapper.update(invocation.getArgument(0), invocation.getArgument(1));
            }).when(delayedMapper).update(isNull(), any());

            assertThat(olderRequest.getAuthenticatedUser("Bearer " + token)).isPresent();

            assertThat(sessionMapper.selectById(session.getId()).getLastSeenAt())
                    .isEqualTo(LocalDateTime.ofInstant(later, ZoneOffset.UTC));
            assertThat(newerRequest.listActiveSessionSummaries(java.util.List.of(user.getId()))
                    .get(user.getId()).getLastSessionSeenAt())
                    .isEqualTo(LocalDateTime.ofInstant(later, ZoneOffset.UTC));
        } finally {
            sessionMapper.deleteById(session.getId());
            userMapper.deleteById(user.getId());
        }
    }

    private AuthService serviceAtTest(Instant instant, RmqStudioSessionMapper sessions) {
        return new AuthService(new AuthProperties(), settingsRepository, Clock.fixed(instant, ZoneOffset.UTC),
                userMapper, sessions, passwordHasher);
    }
}
