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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AuthProperties}: the configured static users and the session-cookie
 * defaults that shape local authentication.
 */
class AuthPropertiesTest {

    private static AuthProperties.User user(String username, String password) {
        AuthProperties.User user = new AuthProperties.User();
        user.setUsername(username);
        user.setPassword(password);
        return user;
    }

    @Test
    void configuredUsersKeepsOnlyCompleteCredentials() {
        AuthProperties properties = new AuthProperties();
        properties.setUsers(List.of(
                user("admin", "admin-secret"),
                user("  ", "secret-without-name"),
                user("reader", null),
                user("", "")));

        assertThat(properties.configuredUsers())
                .extracting(AuthProperties.User::getUsername)
                .containsExactly("admin");
    }

    @Test
    void sessionCookieDefaultsAreSecure() {
        AuthProperties properties = new AuthProperties();

        assertThat(properties.getSessionCookieName()).isEqualTo("rmq_studio_session");
        assertThat(properties.isSessionCookieSecure()).isTrue();
        assertThat(properties.getSessionCookieSameSite()).isEqualTo("Strict");
        assertThat(properties.isLoginRequired()).isFalse();
    }
}
