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
import org.apache.rocketmq.studio.persistence.entity.RmqStudioSession;
import org.apache.rocketmq.studio.persistence.entity.RmqStudioUser;
import org.apache.rocketmq.studio.persistence.mapper.RmqStudioSessionMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqStudioUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the session overview aggregate against the real database, because the bucket boundaries
 * are bound as wrapper parameters inside the SELECT list and only an executed statement can prove
 * the driver accepts them.
 */
@SpringBootTest(properties = "studio.auth.login-required=false")
class AuthServiceSessionOverviewIntegrationTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired
    private AuthService authService;

    @Autowired
    private RmqStudioUserMapper userMapper;

    @Autowired
    private RmqStudioSessionMapper sessionMapper;

    @Test
    void sessionOverviewAggregatesEveryBucketFromOneExecutedQueryTest() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        RmqStudioUser user = new RmqStudioUser();
        user.setUsername("session-overview-it-" + System.nanoTime());
        user.setPasswordHash("not-a-real-password-hash");
        user.setAdmin(false);
        user.setEnabled(true);
        user.setPasswordChangedAt(now);
        userMapper.insert(user);
        // The in-memory dev database is shared across test classes in one JVM, so assert on the
        // delta this user's sessions add instead of on absolute counts.
        StudioUserSessionOverviewVO baseline = authService.getSessionOverview();
        List<RmqStudioSession> inserted = new ArrayList<>();
        try {
            // Two of the four active sessions expire inside the 5 minute window, and two of them
            // have not been seen for longer than the 15 minute stale threshold.
            inserted.add(session(user.getId(), now.plusMinutes(30), now, null));
            inserted.add(session(user.getId(), now.plusMinutes(2), now, null));
            inserted.add(session(user.getId(), now.plusMinutes(30), now.minusMinutes(20), null));
            inserted.add(session(user.getId(), now.plusMinutes(2), now.minusMinutes(20), null));
            // A revoked session and an already expired one must not be counted at all.
            inserted.add(session(user.getId(), now.plusMinutes(30), now, now));
            inserted.add(session(user.getId(), now.minusMinutes(1), now.minusMinutes(30), null));

            StudioUserSessionOverviewVO overview = authService.getSessionOverview();

            assertThat(overview.getActiveSessionCount()).isEqualTo(baseline.getActiveSessionCount() + 4);
            assertThat(overview.getActiveUserCount()).isEqualTo(baseline.getActiveUserCount() + 1);
            assertThat(overview.getExpiringSoonSessionCount())
                    .isEqualTo(baseline.getExpiringSoonSessionCount() + 2);
            assertThat(overview.getStaleSessionCount()).isEqualTo(baseline.getStaleSessionCount() + 2);
        } finally {
            for (RmqStudioSession session : inserted) {
                sessionMapper.deleteById(session.getId());
            }
            userMapper.deleteById(user.getId());
        }
    }

    @Test
    void listActiveSessionsForUserReturnsOnlyThatUsersActiveSessionDetailsTest() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        RmqStudioUser user = studioUser("session-detail-it-" + System.nanoTime(), now);
        RmqStudioUser otherUser = studioUser("other-session-detail-it-" + System.nanoTime(), now);
        userMapper.insert(user);
        userMapper.insert(otherUser);
        List<RmqStudioSession> inserted = new ArrayList<>();
        try {
            RmqStudioSession fresh = session(user.getId(), now.plusMinutes(30),
                    now.minusMinutes(1), null);
            RmqStudioSession expiringAndStale = session(user.getId(), now.plusMinutes(2),
                    now.minusMinutes(20), null);
            inserted.add(fresh);
            inserted.add(expiringAndStale);
            inserted.add(session(user.getId(), now.plusMinutes(30), now.minusMinutes(2), now));
            inserted.add(session(user.getId(), now.minusMinutes(1), now.minusMinutes(30), null));
            inserted.add(session(otherUser.getId(), now.plusMinutes(30), now, null));

            List<StudioUserSessionDetailVO> details =
                    authService.listActiveSessionsForUser(user.getId());

            assertThat(details)
                    .extracting(StudioUserSessionDetailVO::getId)
                    .containsExactly(fresh.getId(), expiringAndStale.getId());
            assertThat(details)
                    .extracting(StudioUserSessionDetailVO::getUserId)
                    .containsOnly(user.getId());
            assertThat(details.get(0).isExpiringSoon()).isFalse();
            assertThat(details.get(0).isStale()).isFalse();
            assertThat(details.get(0).getIdleSeconds()).isGreaterThanOrEqualTo(60L);
            assertThat(details.get(0).getRemainingSeconds()).isGreaterThan(0L);
            assertThat(details.get(1).isExpiringSoon()).isTrue();
            assertThat(details.get(1).isStale()).isTrue();
            assertThat(details.get(1).getIdleSeconds()).isGreaterThanOrEqualTo(20 * 60L);
            assertThat(details.get(1).getRemainingSeconds()).isGreaterThan(0L);
        } finally {
            for (RmqStudioSession session : inserted) {
                sessionMapper.deleteById(session.getId());
            }
            userMapper.deleteById(user.getId());
            userMapper.deleteById(otherUser.getId());
        }
    }

    @Test
    void listActiveSessionsForUserFailsWhenTheUserDoesNotExistTest() {
        assertThatThrownBy(() -> authService.listActiveSessionsForUser(Long.MIN_VALUE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("User not found");
    }

    private RmqStudioUser studioUser(String username, LocalDateTime now) {
        RmqStudioUser user = new RmqStudioUser();
        user.setUsername(username);
        user.setPasswordHash("not-a-real-password-hash");
        user.setAdmin(false);
        user.setEnabled(true);
        user.setPasswordChangedAt(now);
        return user;
    }

    private RmqStudioSession session(Long userId, LocalDateTime expiresAt, LocalDateTime lastSeenAt,
                                     LocalDateTime revokedAt) {
        RmqStudioSession session = new RmqStudioSession();
        session.setUserId(userId);
        session.setTokenHash(tokenHash());
        session.setExpiresAt(expiresAt);
        session.setLastSeenAt(lastSeenAt);
        session.setRevokedAt(revokedAt);
        sessionMapper.insert(session);
        return session;
    }

    /** Fills the CHAR(64) token_hash column with a unique SHA-256 shaped value. */
    private static String tokenHash() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
