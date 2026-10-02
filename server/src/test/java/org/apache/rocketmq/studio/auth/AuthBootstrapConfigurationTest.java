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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthBootstrapConfigurationTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "dev", "prod"})
    void missingBootstrapCredentialsKeepLoginRequiredWithoutCreatingAUserTest(String profile) throws IOException {
        AuthProperties properties = configuration(Map.of(), profile);

        assertThat(properties.isLoginRequired()).isTrue();
        assertThat(properties.configuredUsers()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"STUDIO_AUTH_ADMIN_USERNAME", "STUDIO_AUTH_ADMIN_PASSWORD"})
    void aSingleBootstrapCredentialDoesNotAcquireADefaultCounterpartTest(String name) throws IOException {
        AuthProperties properties = configuration(Map.of(name, "operator-supplied"), "");

        assertThat(properties.isLoginRequired()).isTrue();
        assertThat(properties.configuredUsers()).isEmpty();
    }

    @Test
    void explicitlyBlankCredentialsMatchTheComposeDefaultTest() throws IOException {
        AuthProperties properties = configuration(Map.of(
                "STUDIO_AUTH_ADMIN_USERNAME", "",
                "STUDIO_AUTH_ADMIN_PASSWORD", ""), "prod");

        assertThat(properties.isLoginRequired()).isTrue();
        assertThat(properties.configuredUsers()).isEmpty();
    }

    @Test
    void explicitCredentialsStillConfigureAnAdministratorTest() throws IOException {
        AuthProperties properties = configuration(Map.of(
                "STUDIO_AUTH_ADMIN_USERNAME", "operator",
                "STUDIO_AUTH_ADMIN_PASSWORD", "operator-supplied-test-password"), "prod");

        assertThat(properties.isLoginRequired()).isTrue();
        assertThat(properties.configuredUsers()).singleElement().satisfies(user -> {
            assertThat(user.getUsername()).isEqualTo("operator");
            assertThat(user.getPassword()).isEqualTo("operator-supplied-test-password");
            assertThat(user.isAdmin()).isTrue();
        });
    }

    @Test
    void explicitLocalLoginOptOutDoesNotCreateBootstrapCredentialsTest() throws IOException {
        AuthProperties properties = configuration(Map.of("STUDIO_AUTH_LOGIN_REQUIRED", "false"), "dev");

        assertThat(properties.isLoginRequired()).isFalse();
        assertThat(properties.configuredUsers()).isEmpty();
    }

    private AuthProperties configuration(Map<String, Object> overrides, String profile) throws IOException {
        // Do not inherit real process credentials or system properties in this regression test.
        MockEnvironment environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("overrides", overrides));
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        if (!profile.isEmpty()) {
            addProperties(environment, loader, "application-" + profile + ".yml");
        }
        addProperties(environment, loader, "application.yml");
        return Binder.get(environment).bind("studio.auth", AuthProperties.class).get();
    }

    private void addProperties(MockEnvironment environment, YamlPropertySourceLoader loader, String name)
            throws IOException {
        for (PropertySource<?> source : loader.load(name, new ClassPathResource(name))) {
            environment.getPropertySources().addLast(source);
        }
    }
}
