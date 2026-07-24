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

import com.rocketmq.studio.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int TOKEN_TTL_SECONDS = 86400;
    private static final String TOKEN_PREFIX = "Bearer ";

    private final AuthProperties authProperties;
    private final Map<String, AuthSession> activeTokens = new ConcurrentHashMap<>();

    public LoginVO login(LoginDTO request) {
        log.info("Login attempt for user: {}", request.getUsername());

        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new BusinessException(400, "Username is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BusinessException(400, "Password is required");
        }

        LoginVO.UserInfo user = authenticate(request);
        String token = "studio-jwt-" + UUID.randomUUID();
        activeTokens.put(token, new AuthSession(user, System.currentTimeMillis()
                + TOKEN_TTL_SECONDS * 1000L));

        LoginVO response = LoginVO.builder()
                .token(token)
                .expiresIn(TOKEN_TTL_SECONDS)
                .user(user)
                .build();

        log.info("User {} logged in successfully, admin={}", user.getUsername(), user.isAdmin());
        return response;
    }

    public boolean isAuthenticated(String authorization) {
        Optional<String> token = tokenFromAuthorization(authorization);
        if (token.isEmpty()) {
            return false;
        }
        AuthSession session = activeTokens.get(token.get());
        if (session == null) {
            return false;
        }
        if (session.expiresAtMillis() <= System.currentTimeMillis()) {
            activeTokens.remove(token.get());
            return false;
        }
        return true;
    }

    public void logout(String authorization) {
        tokenFromAuthorization(authorization).ifPresent(activeTokens::remove);
        log.info("User logged out");
    }

    private LoginVO.UserInfo authenticate(LoginDTO request) {
        var configuredUsers = authProperties.configuredUsers();
        if (configuredUsers.isEmpty()) {
            return userInfo(request.getUsername(), "admin".equals(request.getUsername()));
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
        if (authorization == null || !authorization.startsWith(TOKEN_PREFIX)) {
            return Optional.empty();
        }
        String token = authorization.substring(TOKEN_PREFIX.length()).trim();
        return token.isBlank() ? Optional.empty() : Optional.of(token);
    }

    private record AuthSession(LoginVO.UserInfo user, long expiresAtMillis) {
    }
}
