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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class AuthService {

    public LoginVO login(LoginDTO request) {
        log.info("Login attempt for user: {}", request.getUsername());

        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new BusinessException(400, "Username is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BusinessException(400, "Password is required");
        }

        // Mock authentication — accept any non-empty credentials
        String token = "mock-jwt-" + UUID.randomUUID();
        boolean isAdmin = "admin".equals(request.getUsername());

        LoginVO response = LoginVO.builder()
                .token(token)
                .expiresIn(86400)
                .user(LoginVO.UserInfo.builder()
                        .username(request.getUsername())
                        .admin(isAdmin)
                        .build())
                .build();

        log.info("User {} logged in successfully, admin={}", request.getUsername(), isAdmin);
        return response;
    }

    public void logout() {
        log.info("User logged out");
    }
}
