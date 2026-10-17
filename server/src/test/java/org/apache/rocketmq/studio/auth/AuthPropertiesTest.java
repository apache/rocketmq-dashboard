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
 * Pins the auth defaults and the configured-user filter of {@link AuthProperties}: a zero-config
 * deployment does not require login, serves its session cookie securely with Strict same-site,
 * and {@code configuredUsers()} only surfaces users that actually carry a username and password -
 * half-configured entries are silently ignored rather than half-working.
 */
class AuthPropertiesTest {

    @Test
    void theZeroConfigDefaultsAreSecure() {
        AuthProperties properties = new AuthProperties();
        assertThat(properties.isLoginRequired()).isFalse();
        assertThat(properties.getSessionCookieName()).isEqualTo("rmq_studio_session");
        assertThat(properties.isSessionCookieSecure()).isTrue();
        assertThat(properties.getSessionCookieSameSite()).isEqualTo("Strict");
    }

    @Test
    void configuredUsersFiltersHalfConfiguredEntries() {
        AuthProperties.User complete = new AuthProperties.User();
        complete.setUsername("alice");
        complete.setPassword("secret");
        complete.setAdmin(true);

        AuthProperties.User noPassword = new AuthProperties.User();
        noPassword.setUsername("bob");

        AuthProperties.User noUsername = new AuthProperties.User();
        noUsername.setPassword("secret");

        AuthProperties properties = new AuthProperties();
        properties.setUsers(List.of(complete, noPassword, noUsername));

        assertThat(properties.configuredUsers()).hasSize(1);
        assertThat(properties.configuredUsers().get(0).getUsername()).isEqualTo("alice");
        assertThat(properties.configuredUsers().get(0).isAdmin()).isTrue();
    }

    @Test
    void anEmptyConfigurationHasNoUsers() {
        assertThat(new AuthProperties().configuredUsers()).isEmpty();
    }
}
