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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService();
    }

    @Test
    void loginShouldReturnTokenForValidCredentials() {
        LoginDTO request = new LoginDTO();
        request.setUsername("testuser");
        request.setPassword("testpass");

        LoginVO response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).startsWith("mock-jwt-");
        assertThat(response.getExpiresIn()).isEqualTo(86400);
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getUsername()).isEqualTo("testuser");
        assertThat(response.getUser().isAdmin()).isFalse();
    }

    @Test
    void loginShouldReturnAdminFlagForAdminUser() {
        LoginDTO request = new LoginDTO();
        request.setUsername("admin");
        request.setPassword("adminpass");

        LoginVO response = authService.login(request);

        assertThat(response.getUser().getUsername()).isEqualTo("admin");
        assertThat(response.getUser().isAdmin()).isTrue();
    }

    @Test
    void loginShouldThrowWhenUsernameIsNull() {
        LoginDTO request = new LoginDTO();
        request.setUsername(null);
        request.setPassword("password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Username is required");
    }

    @Test
    void loginShouldThrowWhenUsernameIsBlank() {
        LoginDTO request = new LoginDTO();
        request.setUsername("   ");
        request.setPassword("password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Username is required");
    }

    @Test
    void loginShouldThrowWhenPasswordIsNull() {
        LoginDTO request = new LoginDTO();
        request.setUsername("testuser");
        request.setPassword(null);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Password is required");
    }

    @Test
    void loginShouldThrowWhenPasswordIsBlank() {
        LoginDTO request = new LoginDTO();
        request.setUsername("testuser");
        request.setPassword("   ");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Password is required");
    }

    @Test
    void logoutShouldCompleteWithoutError() {
        authService.logout();
    }
}
