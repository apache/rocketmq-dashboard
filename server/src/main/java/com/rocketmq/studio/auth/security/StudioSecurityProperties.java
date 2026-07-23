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

import java.time.Duration;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("studio.security")
public record StudioSecurityProperties(
    String userFile,
    Duration sessionTtl,
    int maxSessionsPerUser,
    int maxUsers,
    Duration registryCheckInterval,
    int maxConcurrentPasswordChecks
) {
    private static final String SESSION_TTL_MESSAGE =
        "studio.security.session-ttl must be between 5m and 24h in whole seconds";
    private static final String MAX_SESSIONS_MESSAGE =
        "studio.security.max-sessions-per-user must be between 1 and 20";
    private static final String MAX_USERS_MESSAGE =
        "studio.security.max-users must be between 1 and 10000";
    private static final String REGISTRY_INTERVAL_MESSAGE =
        "studio.security.registry-check-interval must be between 250ms and 10s";
    private static final String MAX_PASSWORD_CHECKS_MESSAGE =
        "studio.security.max-concurrent-password-checks must be between 1 and 64";

    public StudioSecurityProperties {
        if (sessionTtl == null
            || sessionTtl.compareTo(Duration.ofMinutes(5)) < 0
            || sessionTtl.compareTo(Duration.ofHours(24)) > 0
            || sessionTtl.getNano() != 0) {
            throw new IllegalArgumentException(SESSION_TTL_MESSAGE);
        }
        if (maxSessionsPerUser < 1 || maxSessionsPerUser > 20) {
            throw new IllegalArgumentException(MAX_SESSIONS_MESSAGE);
        }
        if (maxUsers < 1 || maxUsers > 10_000) {
            throw new IllegalArgumentException(MAX_USERS_MESSAGE);
        }
        if (registryCheckInterval == null
            || registryCheckInterval.compareTo(Duration.ofMillis(250)) < 0
            || registryCheckInterval.compareTo(Duration.ofSeconds(10)) > 0) {
            throw new IllegalArgumentException(REGISTRY_INTERVAL_MESSAGE);
        }
        if (maxConcurrentPasswordChecks < 1 || maxConcurrentPasswordChecks > 64) {
            throw new IllegalArgumentException(MAX_PASSWORD_CHECKS_MESSAGE);
        }
    }

    public Optional<String> configuredUserFile() {
        return Optional.ofNullable(userFile).filter(value -> !value.isBlank());
    }
}
