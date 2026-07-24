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
package com.rocketmq.studio.auth.security;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public interface StudioSessionStore {
    IssuedSession issue(StudioUserRegistry.User user, long registryRevision);

    Optional<Session> resolve(String rawToken);

    void revoke(UUID sessionId);

    void revokeByUser(String username);

    record IssuedSession(String token, Session session) {
        public IssuedSession {
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(session, "session");
        }

        @Override
        public String toString() {
            return "IssuedSession[token=<redacted>, session=" + session + "]";
        }
    }

    record Session(
        UUID id,
        String username,
        StudioUserRegistry.Role role,
        String userFingerprint,
        long registryRevision,
        Instant issuedAt,
        Instant expiresAt,
        long sequence
    ) {
        public Session {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(userFingerprint, "userFingerprint");
            Objects.requireNonNull(issuedAt, "issuedAt");
            Objects.requireNonNull(expiresAt, "expiresAt");
        }

        @Override
        public String toString() {
            return "Session[id=" + id
                + ", username=" + username
                + ", role=" + role
                + ", userFingerprint=<redacted>"
                + ", registryRevision=" + registryRevision
                + ", issuedAt=" + issuedAt
                + ", expiresAt=" + expiresAt
                + ", sequence=" + sequence
                + "]";
        }
    }
}
