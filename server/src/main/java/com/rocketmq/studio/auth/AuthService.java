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

package com.rocketmq.studio.auth;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import com.rocketmq.studio.auth.security.LoginAttemptLimiter;
import com.rocketmq.studio.auth.security.LoginAttemptLimiter.Decision;
import com.rocketmq.studio.auth.security.LoginAttemptLimiter.Permit;
import com.rocketmq.studio.auth.security.StudioLoginException;
import com.rocketmq.studio.auth.security.StudioSecurityProperties;
import com.rocketmq.studio.auth.security.StudioSessionStore;
import com.rocketmq.studio.auth.security.StudioSessionStore.IssuedSession;
import com.rocketmq.studio.auth.security.StudioUserRegistry;
import com.rocketmq.studio.auth.security.StudioUserRegistry.Snapshot;
import com.rocketmq.studio.auth.security.StudioUserRegistry.User;
import com.rocketmq.studio.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuthService {
    private static final Pattern VALID_USERNAME = Pattern.compile("[A-Za-z0-9._@-]{1,128}");
    private static final int BCRYPT_PASSWORD_BYTE_LIMIT = 72;
    private static final String INVALID_LOGIN_REQUEST = "Invalid login request";
    private static final String DUMMY_PASSWORD_HASH =
        "{bcrypt}$2y$12$DHdmkmAtJpr2JHVMZJ6bE.hO2fyGjpQOr6s5xzxFWO7H1bCdFl1yq";

    private final StudioUserRegistry registry;
    private final PasswordEncoder passwordEncoder;
    private final StudioSessionStore sessions;
    private final LoginAttemptLimiter limiter;
    private final StudioSecurityProperties properties;

    public AuthService(
        StudioUserRegistry registry,
        PasswordEncoder passwordEncoder,
        StudioSessionStore sessions,
        LoginAttemptLimiter limiter,
        StudioSecurityProperties properties
    ) {
        this.registry = Objects.requireNonNull(registry);
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder);
        this.sessions = Objects.requireNonNull(sessions);
        this.limiter = Objects.requireNonNull(limiter);
        this.properties = Objects.requireNonNull(properties);
    }

    public LoginVO login(LoginDTO request, String remoteAddress) {
        Credentials credentials = validate(request);
        String username = credentials.username();
        Decision decision = limiter.beforeAttempt(username, remoteAddress);
        if (!decision.allowed()) {
            throw StudioLoginException.rateLimited(decision.retryAfterSeconds());
        }

        Permit permit = limiter.acquirePasswordPermit();
        try {
            if (!permit.acquired()) {
                throw StudioLoginException.rateLimited(1);
            }
            Snapshot snapshot = readSnapshot();
            User user = snapshot.available() ? snapshot.users().get(username) : null;
            String passwordHash = user == null ? DUMMY_PASSWORD_HASH : user.passwordHash();
            boolean matches = passwordEncoder.matches(credentials.password(), passwordHash);
            boolean dummyHashCollision = user != null
                && DUMMY_PASSWORD_HASH.equals(user.passwordHash());
            if (!matches || user == null || !snapshot.available() || dummyHashCollision) {
                limiter.recordFailure(decision.key());
                log.info("Studio login rejected");
                throw StudioLoginException.invalidCredentials();
            }

            IssuedSession issued = sessions.issue(user, snapshot.revision());
            limiter.recordSuccess(decision.key());
            log.info("Studio login succeeded");
            return LoginVO.builder()
                .token(issued.token())
                .expiresIn(Math.toIntExact(properties.sessionTtl().toSeconds()))
                .user(LoginVO.UserInfo.builder()
                    .username(user.username())
                    .admin(user.role() == StudioUserRegistry.Role.ADMIN)
                    .build())
                .build();
        } finally {
            permit.close();
        }
    }

    public void logout() {
        log.info("User logged out");
    }

    private static Credentials validate(LoginDTO request) {
        if (request == null
            || request.getUsername() == null
            || !VALID_USERNAME.matcher(request.getUsername()).matches()) {
            log.info("Studio login rejected before credential verification");
            throw new BusinessException(400, INVALID_LOGIN_REQUEST);
        }
        String password = request.getPassword();
        if (password == null
            || password.length() > BCRYPT_PASSWORD_BYTE_LIMIT
            || password.isBlank()
            || password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_PASSWORD_BYTE_LIMIT) {
            log.info("Studio login rejected before credential verification");
            throw new BusinessException(400, INVALID_LOGIN_REQUEST);
        }
        return new Credentials(request.getUsername(), password);
    }

    private Snapshot readSnapshot() {
        try {
            Snapshot snapshot = registry.snapshot();
            if (snapshot != null) {
                return snapshot;
            }
        } catch (RuntimeException exception) {
            // Registry failures share the same generic unavailable path.
        }
        log.warn("Studio user registry is unavailable during login");
        return new Snapshot(0, false, Map.of());
    }

    private record Credentials(String username, String password) {
        @Override
        public String toString() {
            return "Credentials[username=<redacted>, password=<redacted>]";
        }
    }
}
