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
package org.apache.rocketmq.studio.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the Studio session entity {@link RmqStudioSession}: it maps to the
 * {@code rmq_studio_session} table and every column round-trips - the server-side session with
 * its hashed token (the raw token never persists), expiry, revocation and last-seen
 * timestamps.
 */
class RmqStudioSessionTest {

    @Test
    void theEntityMapsToTheSessionTable() {
        TableName annotation = RmqStudioSession.class.getAnnotation(TableName.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("rmq_studio_session");
    }

    @Test
    void everyColumnRoundTrips() {
        RmqStudioSession session = new RmqStudioSession();
        session.setId(7L);
        session.setUserId(3L);
        session.setTokenHash("sha-256-of-token");
        session.setExpiresAt(LocalDateTime.of(2026, 1, 3, 3, 4));
        session.setRevokedAt(LocalDateTime.of(2026, 1, 2, 5, 0));
        session.setLastSeenAt(LocalDateTime.of(2026, 1, 2, 4, 59));

        assertThat(session.getId()).isEqualTo(7L);
        assertThat(session.getUserId()).isEqualTo(3L);
        assertThat(session.getTokenHash()).isEqualTo("sha-256-of-token");
        assertThat(session.getExpiresAt()).isEqualTo(LocalDateTime.of(2026, 1, 3, 3, 4));
        assertThat(session.getRevokedAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 5, 0));
        assertThat(session.getLastSeenAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 4, 59));
    }

    @Test
    void aFreshSessionIsNeitherRevokedNorSeen() {
        RmqStudioSession session = new RmqStudioSession();
        assertThat(session.getRevokedAt()).isNull();
        assertThat(session.getLastSeenAt()).isNull();
    }
}
