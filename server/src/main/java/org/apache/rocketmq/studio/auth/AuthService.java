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

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.persistence.entity.RmqStudioSession;
import org.apache.rocketmq.studio.persistence.entity.RmqStudioUser;
import org.apache.rocketmq.studio.persistence.mapper.RmqStudioSessionMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqStudioUserMapper;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authenticates Studio users and owns bearer session lifecycle.
 *
 * <p>The configuration users remain a bootstrap mechanism for a fresh database only. Once a
 * Studio user has been created, the database is the source of truth for credentials and account
 * status.</p>
 */
@Slf4j
@Service
public class AuthService {

    private static final int DEFAULT_SESSION_TIMEOUT_MINUTES = 30;
    private static final int MIN_SESSION_TIMEOUT_MINUTES = 5;
    private static final int MAX_SESSION_TIMEOUT_MINUTES = 1440;
    private static final Duration LAST_SEEN_UPDATE_INTERVAL = Duration.ofMinutes(5);
    private static final String TOKEN_PREFIX = "Bearer ";
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private final AuthProperties authProperties;
    private final SettingsRepository settingsRepository;
    private final Clock clock;
    private final RmqStudioUserMapper userMapper;
    private final RmqStudioSessionMapper sessionMapper;
    private final PasswordHasher passwordHasher;
    private final LoginRateLimiter loginRateLimiter;

    // Retained only for narrow unit tests that construct the legacy service directly.
    private final Map<String, AuthSession> activeTokens = new ConcurrentHashMap<>();

    @Autowired
    public AuthService(AuthProperties authProperties, SettingsRepository settingsRepository,
                       RmqStudioUserMapper userMapper, RmqStudioSessionMapper sessionMapper,
                       PasswordHasher passwordHasher, LoginRateLimiter loginRateLimiter) {
        this(authProperties, settingsRepository, Clock.systemUTC(), userMapper, sessionMapper,
                passwordHasher, loginRateLimiter);
    }

    public AuthService(AuthProperties authProperties, SettingsRepository settingsRepository) {
        this(authProperties, settingsRepository, Clock.systemUTC());
    }

    AuthService(AuthProperties authProperties, SettingsRepository settingsRepository, Clock clock) {
        this(authProperties, settingsRepository, clock, null, null, new PasswordHasher(),
                new LoginRateLimiter(clock));
    }

    AuthService(AuthProperties authProperties, SettingsRepository settingsRepository, Clock clock,
                RmqStudioUserMapper userMapper, RmqStudioSessionMapper sessionMapper,
                PasswordHasher passwordHasher) {
        this(authProperties, settingsRepository, clock, userMapper, sessionMapper, passwordHasher,
                new LoginRateLimiter(clock));
    }

    AuthService(AuthProperties authProperties, SettingsRepository settingsRepository, Clock clock,
                RmqStudioUserMapper userMapper, RmqStudioSessionMapper sessionMapper,
                PasswordHasher passwordHasher, LoginRateLimiter loginRateLimiter) {
        this.authProperties = authProperties;
        this.settingsRepository = settingsRepository;
        this.clock = clock;
        this.userMapper = userMapper;
        this.sessionMapper = sessionMapper;
        this.passwordHasher = passwordHasher;
        this.loginRateLimiter = loginRateLimiter;
    }

    public LoginVO login(LoginDTO request) {
        validateLogin(request);
        loginRateLimiter.checkAllowed(request.getUsername());
        try {
            LoginVO login = databaseBacked() ? loginDatabaseUser(request) : loginConfiguredUser(request);
            loginRateLimiter.recordSuccess(request.getUsername());
            return login;
        } catch (BusinessException exception) {
            if (exception.getCode() == 401) {
                loginRateLimiter.recordFailure(request.getUsername());
            }
            throw exception;
        }
    }

    public boolean isAuthenticated(String authorization) {
        return getAuthenticatedUser(authorization).isPresent();
    }

    public Optional<LoginVO.UserInfo> getAuthenticatedUser(String authorization) {
        Optional<String> token = tokenFromAuthorization(authorization);
        if (token.isEmpty()) {
            return Optional.empty();
        }
        return databaseBacked() ? databaseUserForToken(token.get()) : inMemoryUserForToken(token.get());
    }

    public boolean isAdmin(String authorization) {
        return getAuthenticatedUser(authorization).map(LoginVO.UserInfo::isAdmin).orElse(false);
    }

    public void logout(String authorization) {
        tokenFromAuthorization(authorization).ifPresent(token -> {
            if (databaseBacked()) {
                sessionMapper.update(null, new UpdateWrapper<RmqStudioSession>()
                        .eq("token_hash", tokenHash(token))
                        .isNull("revoked_at")
                        .set("revoked_at", now()));
            } else {
                activeTokens.remove(token);
            }
        });
    }

    public List<RmqStudioUser> listUsers() {
        requireDatabaseBacked();
        return userMapper.selectList(new QueryWrapper<RmqStudioUser>().orderByAsc("username"));
    }

    public RmqStudioUser createUser(String username, String password, boolean admin) {
        requireDatabaseBacked();
        validateUsername(username);
        validatePassword(password);
        if (findUserByUsername(username).isPresent()) {
            throw new BusinessException(409, "Username is already in use");
        }
        RmqStudioUser user = new RmqStudioUser();
        user.setUsername(username.trim());
        user.setPasswordHash(passwordHasher.hash(password));
        user.setAdmin(admin);
        user.setEnabled(true);
        user.setPasswordChangedAt(now());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "Username is already in use");
        }
        return user;
    }

    @Transactional
    public RmqStudioUser setUserEnabled(Long userId, boolean enabled) {
        requireDatabaseBacked();
        RmqStudioUser user = getUser(userId);
        if (!enabled && Boolean.TRUE.equals(user.getAdmin())) {
            // Lock the enabled administrator rows so concurrent disables serialize. A plain
            // count would let two requests both observe a count of 2 and disable everyone.
            List<RmqStudioUser> enabledAdmins = userMapper.selectList(new QueryWrapper<RmqStudioUser>()
                    .eq("admin", true)
                    .eq("enabled", true)
                    .last("FOR UPDATE"));
            boolean targetStillEnabled = enabledAdmins.stream()
                    .anyMatch(admin -> userId.equals(admin.getId()));
            if (targetStillEnabled && enabledAdmins.size() <= 1) {
                throw new BusinessException(409, "The last enabled administrator cannot be disabled");
            }
        }
        userMapper.updateById(userWithEnabled(user, enabled));
        if (!enabled) {
            revokeUserSessions(user.getId());
        }
        user.setEnabled(enabled);
        return user;
    }

    public void changePassword(Long userId, String currentPassword, String newPassword,
                               boolean requireCurrentPassword) {
        requireDatabaseBacked();
        RmqStudioUser user = getUser(userId);
        if (requireCurrentPassword && !passwordHasher.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException(401, "Current password is incorrect");
        }
        validatePassword(newPassword);
        userMapper.update(null, new UpdateWrapper<RmqStudioUser>()
                .eq("id", user.getId())
                .set("password_hash", passwordHasher.hash(newPassword))
                .set("password_changed_at", now()));
        revokeUserSessions(user.getId());
    }

    @Scheduled(fixedDelayString = "${studio.auth.session-cleanup-interval:PT5M}")
    public void purgeExpiredSessions() {
        if (databaseBacked()) {
            sessionMapper.delete(new QueryWrapper<RmqStudioSession>().lt("expires_at", now()));
        } else {
            purgeExpiredSessions(clock.millis());
        }
    }

    private LoginVO loginDatabaseUser(LoginDTO request) {
        ensureBootstrapUsers();
        RmqStudioUser user = findUserByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(401, "Invalid username or password"));
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BusinessException(403, "User account is disabled");
        }
        if (!passwordHasher.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(401, "Invalid username or password");
        }
        int tokenTtlSeconds = sessionTimeoutSeconds();
        String token = newBearerToken();
        LocalDateTime current = now();
        RmqStudioSession session = new RmqStudioSession();
        session.setUserId(user.getId());
        session.setTokenHash(tokenHash(token));
        session.setLastSeenAt(current);
        session.setExpiresAt(current.plusSeconds(tokenTtlSeconds));
        sessionMapper.insert(session);
        return loginResponse(userInfo(user), token, tokenTtlSeconds);
    }

    private LoginVO loginConfiguredUser(LoginDTO request) {
        LoginVO.UserInfo user = authenticateConfiguredUser(request);
        long current = clock.millis();
        purgeExpiredSessions(current);
        int tokenTtlSeconds = sessionTimeoutSeconds();
        String token = "studio-jwt-" + UUID.randomUUID();
        activeTokens.put(token, new AuthSession(user, current + tokenTtlSeconds * 1000L));
        return loginResponse(user, token, tokenTtlSeconds);
    }

    private LoginVO loginResponse(LoginVO.UserInfo user, String token, int tokenTtlSeconds) {
        log.info("User {} logged in successfully, admin={}", user.getUsername(), user.isAdmin());
        return LoginVO.builder().token(token).expiresIn(tokenTtlSeconds).user(user).build();
    }

    private Optional<LoginVO.UserInfo> databaseUserForToken(String token) {
        RmqStudioSession session = sessionMapper.selectOne(new QueryWrapper<RmqStudioSession>()
                .eq("token_hash", tokenHash(token)));
        if (session == null || session.getRevokedAt() != null || !session.getExpiresAt().isAfter(now())) {
            return Optional.empty();
        }
        RmqStudioUser user = userMapper.selectById(session.getUserId());
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            return Optional.empty();
        }
        LocalDateTime current = now();
        if (session.getLastSeenAt() == null
                || !session.getLastSeenAt().plus(LAST_SEEN_UPDATE_INTERVAL).isAfter(current)) {
            sessionMapper.update(null, new UpdateWrapper<RmqStudioSession>()
                    .eq("id", session.getId())
                    .set("last_seen_at", current));
        }
        return Optional.of(userInfo(user));
    }

    private Optional<LoginVO.UserInfo> inMemoryUserForToken(String token) {
        AuthSession session = activeTokens.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (session.expiresAtMillis() <= clock.millis()) {
            activeTokens.remove(token);
            return Optional.empty();
        }
        return Optional.of(session.user());
    }

    private void ensureBootstrapUsers() {
        if (userMapper.selectCount(null) > 0) {
            return;
        }
        if (authProperties.configuredUsers().isEmpty()) {
            throw new BusinessException(503, "No Studio users are configured");
        }
        Set<String> seeded = new HashSet<>();
        for (AuthProperties.User configuredUser : authProperties.configuredUsers()) {
            if (!seeded.add(configuredUser.getUsername())) {
                log.warn("Skipping duplicate bootstrap username: {}", configuredUser.getUsername());
                continue;
            }
            RmqStudioUser user = new RmqStudioUser();
            user.setUsername(configuredUser.getUsername());
            user.setPasswordHash(passwordHasher.hash(configuredUser.getPassword()));
            user.setAdmin(configuredUser.isAdmin());
            user.setEnabled(true);
            user.setPasswordChangedAt(now());
            try {
                userMapper.insert(user);
            } catch (DuplicateKeyException exception) {
                // Another Studio instance can initialize the same configured user concurrently.
                log.debug("Bootstrap user {} was created concurrently", configuredUser.getUsername());
            }
        }
    }

    private Optional<RmqStudioUser> findUserByUsername(String username) {
        return Optional.ofNullable(userMapper.selectOne(new QueryWrapper<RmqStudioUser>()
                .eq("username", username.trim())));
    }

    private RmqStudioUser getUser(Long userId) {
        RmqStudioUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "User not found");
        }
        return user;
    }

    private RmqStudioUser userWithEnabled(RmqStudioUser user, boolean enabled) {
        RmqStudioUser update = new RmqStudioUser();
        update.setId(user.getId());
        update.setEnabled(enabled);
        return update;
    }

    private void revokeUserSessions(Long userId) {
        sessionMapper.update(null, new UpdateWrapper<RmqStudioSession>()
                .eq("user_id", userId)
                .isNull("revoked_at")
                .set("revoked_at", now()));
    }

    private void validateLogin(LoginDTO request) {
        if (request == null) {
            throw new BusinessException(400, "Login request is required");
        }
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new BusinessException(400, "Username is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BusinessException(400, "Password is required");
        }
    }

    private void validateUsername(String username) {
        if (username == null || username.isBlank() || username.trim().length() > 128) {
            throw new BusinessException(400, "Username must contain 1 to 128 characters");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 256) {
            throw new BusinessException(400, "Password must contain 8 to 256 characters");
        }
    }

    private LoginVO.UserInfo authenticateConfiguredUser(LoginDTO request) {
        var configuredUsers = authProperties.configuredUsers();
        if (configuredUsers.isEmpty()) {
            throw new BusinessException(503, "No valid login users are configured");
        }
        return configuredUsers.stream()
                .filter(user -> user.getUsername().equals(request.getUsername()))
                .filter(user -> user.getPassword().equals(request.getPassword()))
                .findFirst()
                .map(user -> userInfo(null, user.getUsername(), user.isAdmin()))
                .orElseThrow(() -> new BusinessException(401, "Invalid username or password"));
    }

    private LoginVO.UserInfo userInfo(RmqStudioUser user) {
        return userInfo(user.getId(), user.getUsername(), Boolean.TRUE.equals(user.getAdmin()));
    }

    private LoginVO.UserInfo userInfo(Long userId, String username, boolean admin) {
        return LoginVO.UserInfo.builder().userId(userId).username(username).admin(admin).build();
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

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(clock.millis()), ZoneOffset.UTC);
    }

    private String newBearerToken() {
        byte[] bytes = new byte[32];
        TOKEN_RANDOM.nextBytes(bytes);
        return "studio-jwt-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String tokenHash(String token) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash session token", exception);
        }
    }

    private Optional<String> tokenFromAuthorization(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, TOKEN_PREFIX, 0,
                TOKEN_PREFIX.length())) {
            return Optional.empty();
        }
        String token = authorization.substring(TOKEN_PREFIX.length()).trim();
        return token.isBlank() ? Optional.empty() : Optional.of(token);
    }

    private void purgeExpiredSessions(long current) {
        activeTokens.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= current);
    }

    private boolean databaseBacked() {
        return userMapper != null && sessionMapper != null;
    }

    private void requireDatabaseBacked() {
        if (!databaseBacked()) {
            throw new IllegalStateException("Studio user management requires database persistence");
        }
    }

    private record AuthSession(LoginVO.UserInfo user, long expiresAtMillis) {
    }
}
