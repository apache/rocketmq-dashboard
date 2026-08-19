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

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "studio.auth")
public class AuthProperties {
    private boolean loginRequired;
    private String sessionCookieName = "rmq_studio_session";
    private boolean sessionCookieSecure = true;
    private String sessionCookieSameSite = "Strict";
    private List<User> users = new ArrayList<>();

    public List<User> configuredUsers() {
        if (users == null) {
            return List.of();
        }
        return users.stream()
                .filter(Objects::nonNull)
                .filter(user -> StringUtils.hasText(user.getUsername()))
                .filter(user -> StringUtils.hasText(user.getPassword()))
                .map(AuthProperties::normalizedUser)
                .toList();
    }

    private static User normalizedUser(User configured) {
        User normalized = new User();
        normalized.setUsername(configured.getUsername().trim());
        normalized.setPassword(configured.getPassword());
        normalized.setAdmin(configured.isAdmin());
        return normalized;
    }

    @Getter
    @Setter
    public static class User {
        private String username;
        private String password;
        private boolean admin;
    }
}
