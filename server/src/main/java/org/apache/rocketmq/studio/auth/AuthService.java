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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

@Slf4j
@Service
public class AuthService {

    private static final int DEFAULT_SESSION_TIMEOUT_MINUTES = 30;
    private static final int MIN_SESSION_TIMEOUT_MINUTES = 5;
    private static final int MAX_SESSION_TIMEOUT_MINUTES = 1440;
    private static final String TOKEN_PREFIX = "Bearer ";

    private final AuthProperties authProperties;
    private final SettingsRepository settingsRepository;
    private final Clock clock;
    private final Map<String, AuthSession> activeTokens = new ConcurrentHashMap<>();

    @Autowired
    public AuthService(AuthProperties authProperties, SettingsRepository settingsRepository) {
        this(authProperties, settingsRepository, Clock.systemUTC());
    }

    AuthService(AuthProperties authProperties, SettingsRepository settingsRepository, Clock clock) {
        this.authProperties = authProperties;
        this.settingsRepository = settingsRepository;
        this.clock = clock;
    }

    public LoginVO login(LoginDTO request) {
        if (request == null) {
            throw new BusinessException(400, "Login request is required");
        }

        log.info("Login attempt for user: {}", request.getUsername());

        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new BusinessException(400, "Username is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BusinessException(400, "Password is required");
        }

        LoginVO.UserInfo user = authenticate(request);
        long now = clock.millis();
        purgeExpiredSessions(now);
        int tokenTtlSeconds = sessionTimeoutSeconds();
        String token = "studio-jwt-" + UUID.randomUUID();
        activeTokens.put(token, new AuthSession(user, now + tokenTtlSeconds * 1000L));

        LoginVO response = LoginVO.builder()
                .token(token)
                .expiresIn(tokenTtlSeconds)
                .user(user)
                .build();

        log.info("User {} logged in successfully, admin={}", user.getUsername(), user.isAdmin());
        return response;
    }

    public boolean isAuthenticated(String authorization) {
        return getAuthenticatedUser(authorization).isPresent();
    }

    public Optional<LoginVO.UserInfo> getAuthenticatedUser(String authorization) {
        Optional<String> token = tokenFromAuthorization(authorization);
        if (token.isEmpty()) {
            return Optional.empty();
        }
        AuthSession session = activeTokens.get(token.get());
        if (session == null) {
            return Optional.empty();
        }
        if (session.expiresAtMillis() <= clock.millis()) {
            activeTokens.remove(token.get());
            return Optional.empty();
        }
        return Optional.of(session.user());
    }

    public boolean isAdmin(String authorization) {
        Optional<String> token = tokenFromAuthorization(authorization);
        if (token.isEmpty()) {
            return false;
        }
        AuthSession session = activeTokens.get(token.get());
        if (session == null || session.expiresAtMillis() <= clock.millis()) {
            activeTokens.remove(token.get());
            return false;
        }
        return session.user().isAdmin();
    }

    public void logout(String authorization) {
        tokenFromAuthorization(authorization).ifPresent(activeTokens::remove);
        log.info("User logged out");
    }

    @Scheduled(fixedDelayString = "${studio.auth.session-cleanup-interval:PT5M}")
    public void purgeExpiredSessions() {
        purgeExpiredSessions(clock.millis());
    }

    private void purgeExpiredSessions(long now) {
        activeTokens.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
    }

    private int sessionTimeoutSeconds() {
        GeneralSettingsVO settings = settingsRepository.loadGeneralSettings();
        int minutes = settings == null ? DEFAULT_SESSION_TIMEOUT_MINUTES : settings.getSessionTimeout();
        if (minutes < MIN_SESSION_TIMEOUT_MINUTES || minutes > MAX_SESSION_TIMEOUT_MINUTES) {
            log.warn("Ignoring invalid persisted session timeout: {} minutes", minutes);
            minutes = DEFAULT_SESSION_TIMEOUT_MINUTES;
        }
        return Math.toIntExact(Duration.ofMinutes(minutes).toSeconds());
    }

    private LoginVO.UserInfo authenticate(LoginDTO request) {
        var configuredUsers = authProperties.configuredUsers();
        if (configuredUsers.isEmpty()) {
            throw new BusinessException(503, "No valid login users are configured");
        }

        return configuredUsers.stream()
                .filter(user -> user.getUsername().equals(request.getUsername()))
                .filter(user -> user.getPassword().equals(request.getPassword()))
                .findFirst()
                .map(user -> userInfo(user.getUsername(), user.isAdmin()))
                .orElseThrow(() -> new BusinessException(401, "Invalid username or password"));
    }

    private LoginVO.UserInfo userInfo(String username, boolean admin) {
        return LoginVO.UserInfo.builder()
                .username(username)
                .admin(admin)
                .build();
    }

    private Optional<String> tokenFromAuthorization(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, TOKEN_PREFIX, 0,
                TOKEN_PREFIX.length())) {
            return Optional.empty();
        }
        String token = authorization.substring(TOKEN_PREFIX.length()).trim();
        return token.isBlank() ? Optional.empty() : Optional.of(token);
    }

    private record AuthSession(LoginVO.UserInfo user, long expiresAtMillis) {
    }
}
