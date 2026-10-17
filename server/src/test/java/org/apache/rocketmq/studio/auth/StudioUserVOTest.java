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

import org.apache.rocketmq.studio.persistence.entity.RmqStudioUser;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the mapping of {@link StudioUserVO#from}: every user column is carried, a null admin or
 * enabled flag normalises to false, and a missing session summary reads as zero active sessions
 * with no session timestamps.
 */
class StudioUserVOTest {

    private RmqStudioUser user() {
        RmqStudioUser user = new RmqStudioUser();
        user.setId(7L);
        user.setUsername("alice");
        user.setAdmin(null);
        user.setEnabled(Boolean.FALSE);
        user.setPasswordChangedAt(LocalDateTime.of(2026, 1, 2, 3, 4));
        user.setGmtCreate(LocalDateTime.of(2025, 12, 31, 23, 59));
        user.setGmtModified(LocalDateTime.of(2026, 2, 1, 8, 0));
        return user;
    }

    @Test
    void aUserWithoutASessionSummaryReadsAsZeroActiveSessions() {
        StudioUserVO vo = StudioUserVO.from(user());
        assertThat(vo.getId()).isEqualTo(7L);
        assertThat(vo.getUsername()).isEqualTo("alice");
        assertThat(vo.isAdmin()).isFalse();
        assertThat(vo.isEnabled()).isFalse();
        assertThat(vo.getActiveSessionCount()).isZero();
        assertThat(vo.getLastSessionSeenAt()).isNull();
        assertThat(vo.getNearestSessionExpiresAt()).isNull();
        assertThat(vo.getPasswordChangedAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 3, 4));
        assertThat(vo.getGmtCreate()).isEqualTo(LocalDateTime.of(2025, 12, 31, 23, 59));
        assertThat(vo.getGmtModified()).isEqualTo(LocalDateTime.of(2026, 2, 1, 8, 0));
    }

    @Test
    void aSessionSummaryCarriesItsFieldsIntoTheView() {
        StudioUserSessionSummaryVO summary = StudioUserSessionSummaryVO.builder()
                .userId(7L)
                .activeSessionCount(3)
                .lastSessionSeenAt(LocalDateTime.of(2026, 2, 2, 10, 0))
                .nearestSessionExpiresAt(LocalDateTime.of(2026, 2, 2, 11, 30))
                .build();
        StudioUserVO vo = StudioUserVO.from(user(), summary);
        assertThat(vo.getActiveSessionCount()).isEqualTo(3);
        assertThat(vo.getLastSessionSeenAt()).isEqualTo(LocalDateTime.of(2026, 2, 2, 10, 0));
        assertThat(vo.getNearestSessionExpiresAt()).isEqualTo(LocalDateTime.of(2026, 2, 2, 11, 30));
    }

    @Test
    void aNullEnabledFlagAlsoNormalisesToFalse() {
        RmqStudioUser user = user();
        user.setAdmin(Boolean.TRUE);
        user.setEnabled(null);
        StudioUserVO vo = StudioUserVO.from(user);
        assertThat(vo.isAdmin()).isTrue();
        assertThat(vo.isEnabled()).isFalse();
    }
}
