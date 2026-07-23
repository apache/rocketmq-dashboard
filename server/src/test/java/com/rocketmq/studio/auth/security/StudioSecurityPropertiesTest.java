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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudioSecurityPropertiesTest {
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

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(PropertiesConfiguration.class)
        .withPropertyValues(
            "studio.security.user-file=",
            "studio.security.session-ttl=24h",
            "studio.security.max-sessions-per-user=5",
            "studio.security.max-users=1000",
            "studio.security.registry-check-interval=1s",
            "studio.security.max-concurrent-password-checks=8"
        );

    @Test
    void bindsDefaultEquivalentValues() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(StudioSecurityProperties.class);

            StudioSecurityProperties properties = context.getBean(StudioSecurityProperties.class);
            assertThat(properties.userFile()).isEmpty();
            assertThat(properties.sessionTtl()).isEqualTo(Duration.ofHours(24));
            assertThat(properties.maxSessionsPerUser()).isEqualTo(5);
            assertThat(properties.maxUsers()).isEqualTo(1000);
            assertThat(properties.registryCheckInterval()).isEqualTo(Duration.ofSeconds(1));
            assertThat(properties.maxConcurrentPasswordChecks()).isEqualTo(8);
        });
    }

    @ParameterizedTest(name = "session TTL boundary {0}")
    @CsvSource({
        "5m, 300",
        "24h, 86400"
    })
    void acceptsInclusiveSessionTtlBoundaries(String value, long expectedSeconds) {
        contextRunner.withPropertyValues("studio.security.session-ttl=" + value)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(StudioSecurityProperties.class).sessionTtl())
                    .isEqualTo(Duration.ofSeconds(expectedSeconds));
            });
    }

    @ParameterizedTest(name = "invalid session TTL {0}")
    @CsvSource({
        "299s",
        "86401s",
        "300001ms"
    })
    void rejectsOutOfRangeOrFractionalSessionTtl(String value) {
        assertBindingFailure(
            "studio.security.session-ttl=" + value,
            SESSION_TTL_MESSAGE
        );
    }

    @Test
    void rejectsNullSessionTtl() {
        assertThatThrownBy(() -> properties(null, Duration.ofSeconds(1)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(SESSION_TTL_MESSAGE);
    }

    @ParameterizedTest(name = "max sessions per user boundary {0}")
    @CsvSource({
        "1",
        "20"
    })
    void acceptsInclusiveMaxSessionsPerUserBoundaries(int value) {
        contextRunner.withPropertyValues("studio.security.max-sessions-per-user=" + value)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(StudioSecurityProperties.class).maxSessionsPerUser())
                    .isEqualTo(value);
            });
    }

    @ParameterizedTest(name = "invalid max sessions per user {0}")
    @CsvSource({
        "0",
        "21"
    })
    void rejectsMaxSessionsPerUserOutsideBounds(int value) {
        assertBindingFailure(
            "studio.security.max-sessions-per-user=" + value,
            MAX_SESSIONS_MESSAGE
        );
    }

    @ParameterizedTest(name = "max users boundary {0}")
    @CsvSource({
        "1",
        "10000"
    })
    void acceptsInclusiveMaxUsersBoundaries(int value) {
        contextRunner.withPropertyValues("studio.security.max-users=" + value)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(StudioSecurityProperties.class).maxUsers())
                    .isEqualTo(value);
            });
    }

    @ParameterizedTest(name = "invalid max users {0}")
    @CsvSource({
        "0",
        "10001"
    })
    void rejectsMaxUsersOutsideBounds(int value) {
        assertBindingFailure(
            "studio.security.max-users=" + value,
            MAX_USERS_MESSAGE
        );
    }

    @ParameterizedTest(name = "registry interval boundary {0}")
    @CsvSource({
        "250ms, 250",
        "10s, 10000"
    })
    void acceptsInclusiveRegistryCheckIntervalBoundaries(String value, long expectedMillis) {
        contextRunner.withPropertyValues("studio.security.registry-check-interval=" + value)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(StudioSecurityProperties.class).registryCheckInterval())
                    .isEqualTo(Duration.ofMillis(expectedMillis));
            });
    }

    @ParameterizedTest(name = "invalid registry interval {0}")
    @CsvSource({
        "249ms",
        "10001ms"
    })
    void rejectsRegistryCheckIntervalOutsideBounds(String value) {
        assertBindingFailure(
            "studio.security.registry-check-interval=" + value,
            REGISTRY_INTERVAL_MESSAGE
        );
    }

    @Test
    void rejectsNullRegistryCheckInterval() {
        assertThatThrownBy(() -> properties(Duration.ofHours(24), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(REGISTRY_INTERVAL_MESSAGE);
    }

    @ParameterizedTest(name = "max password checks boundary {0}")
    @CsvSource({
        "1",
        "64"
    })
    void acceptsInclusiveMaxConcurrentPasswordChecksBoundaries(int value) {
        contextRunner.withPropertyValues("studio.security.max-concurrent-password-checks=" + value)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(StudioSecurityProperties.class).maxConcurrentPasswordChecks())
                    .isEqualTo(value);
            });
    }

    @ParameterizedTest(name = "invalid max password checks {0}")
    @CsvSource({
        "0",
        "65"
    })
    void rejectsMaxConcurrentPasswordChecksOutsideBounds(int value) {
        assertBindingFailure(
            "studio.security.max-concurrent-password-checks=" + value,
            MAX_PASSWORD_CHECKS_MESSAGE
        );
    }

    @Test
    void treatsNullAndBlankUserFilesAsNotConfigured() {
        assertThat(properties(null).configuredUserFile()).isEmpty();
        assertThat(properties(" \t ").configuredUserFile()).isEmpty();
    }

    @Test
    void preservesConfiguredUserFileWithoutNormalization() {
        String configuredValue = "  config/security-users.yml  ";

        assertThat(properties(configuredValue).configuredUserFile()).contains(configuredValue);
    }

    @Test
    void acceptsOperatingSystemIllegalPathText() {
        String illegalPathText = "security\u0000-users.yml";

        assertThatCode(() -> properties(illegalPathText)).doesNotThrowAnyException();
        assertThat(properties(illegalPathText).configuredUserFile()).contains(illegalPathText);
    }

    @Test
    void blankUserFileDoesNotCauseBindingFailure() {
        contextRunner.withPropertyValues("studio.security.user-file=   ")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(StudioSecurityProperties.class).configuredUserFile())
                    .isEmpty();
            });
    }

    private void assertBindingFailure(String propertyValue, String expectedMessage) {
        contextRunner.withPropertyValues(propertyValue)
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class)
                    .hasRootCauseMessage(expectedMessage);
            });
    }

    private StudioSecurityProperties properties(String userFile) {
        return new StudioSecurityProperties(
            userFile,
            Duration.ofHours(24),
            5,
            1000,
            Duration.ofSeconds(1),
            8
        );
    }

    private StudioSecurityProperties properties(Duration sessionTtl, Duration registryCheckInterval) {
        return new StudioSecurityProperties(
            null,
            sessionTtl,
            5,
            1000,
            registryCheckInterval,
            8
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(StudioSecurityProperties.class)
    static class PropertiesConfiguration {
    }
}
