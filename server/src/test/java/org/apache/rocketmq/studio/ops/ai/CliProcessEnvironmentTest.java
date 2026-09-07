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
package org.apache.rocketmq.studio.ops.ai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CliProcessEnvironmentTest {

    @Test
    void bindsCommaSeparatedAdditionalNames() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "studio.llm.cli-allowed-environment", "CUSTOM_CONFIG,CUSTOM_HOME")));

        LlmProperties properties = binder.bind("studio.llm", Bindable.of(LlmProperties.class))
                .orElseThrow(() -> new AssertionError("LLM properties were not bound"));

        assertThat(properties.getCliAllowedEnvironment()).containsExactly("CUSTOM_CONFIG", "CUSTOM_HOME");
    }

    @Test
    void keepsOnlyRequiredRuntimeEnvironment() {
        CliProcessEnvironment policy = new CliProcessEnvironment(List.of());
        Map<String, String> parent = new LinkedHashMap<>();
        parent.put("PATH", "/usr/local/bin:/usr/bin");
        parent.put("HOME", "/home/studio");
        parent.put("XDG_CONFIG_HOME", "/home/studio/.config");
        parent.put("TMPDIR", "/tmp/studio");
        parent.put("LANG", "en_US.UTF-8");
        parent.put("HTTPS_PROXY", "http://proxy.example:8080");
        parent.put("SSL_CERT_FILE", "/etc/ssl/custom.pem");
        parent.put("SPRING_DATASOURCE_PASSWORD", "database-secret");
        parent.put("STUDIO_AUTH_ADMIN_PASSWORD", "admin-secret");
        parent.put("CLOUD_ACCESS_KEY_SECRET", "cloud-secret");
        parent.put("NODE_OPTIONS", "--require=/tmp/hook.js");

        Map<String, String> child = policy.build(parent, Map.of());

        assertThat(child).containsEntry("PATH", "/usr/local/bin:/usr/bin")
                .containsEntry("HOME", "/home/studio")
                .containsEntry("XDG_CONFIG_HOME", "/home/studio/.config")
                .containsEntry("TMPDIR", "/tmp/studio")
                .containsEntry("LANG", "en_US.UTF-8")
                .containsEntry("HTTPS_PROXY", "http://proxy.example:8080")
                .containsEntry("SSL_CERT_FILE", "/etc/ssl/custom.pem")
                .doesNotContainKeys("SPRING_DATASOURCE_PASSWORD", "STUDIO_AUTH_ADMIN_PASSWORD",
                        "CLOUD_ACCESS_KEY_SECRET", "NODE_OPTIONS");
    }

    @Test
    void retainsOnlyConfiguredAdditionalNames() {
        CliProcessEnvironment policy = new CliProcessEnvironment(
                List.of("CUSTOM_CLI_CONFIG", "  CUSTOM_RUNTIME_HOME  "));
        Map<String, String> parent = Map.of(
                "CUSTOM_CLI_CONFIG", "profile-a",
                "CUSTOM_RUNTIME_HOME", "/opt/provider",
                "UNRELATED_SECRET", "secret");

        assertThat(policy.build(parent, Map.of()))
                .containsEntry("CUSTOM_CLI_CONFIG", "profile-a")
                .containsEntry("CUSTOM_RUNTIME_HOME", "/opt/provider")
                .doesNotContainKey("UNRELATED_SECRET");
    }

    @Test
    void providerEnvironmentOverridesAllowedParentValue() {
        CliProcessEnvironment policy = new CliProcessEnvironment(List.of("ANTHROPIC_AUTH_TOKEN"));
        Map<String, String> child = policy.build(
                Map.of("ANTHROPIC_AUTH_TOKEN", "stale-parent-token", "PATH", "/usr/bin"),
                Map.of("ANTHROPIC_AUTH_TOKEN", "request-token", "ANTHROPIC_BASE_URL", "https://llm.example"));

        assertThat(child).containsEntry("PATH", "/usr/bin")
                .containsEntry("ANTHROPIC_AUTH_TOKEN", "request-token")
                .containsEntry("ANTHROPIC_BASE_URL", "https://llm.example");
    }

    @Test
    void ignoresInvalidConfiguredAndProviderNames() {
        CliProcessEnvironment policy = new CliProcessEnvironment(
                List.of("", "   ", "INVALID-NAME", "HAS=VALUE", "VALID_EXTRA"));
        Map<String, String> provider = new LinkedHashMap<>();
        provider.put("BAD-NAME", "bad");
        provider.put("GOOD_NAME", "good");
        provider.put("NULL_VALUE", null);

        Map<String, String> child = policy.build(
                Map.of("INVALID-NAME", "bad", "HAS=VALUE", "bad", "VALID_EXTRA", "kept"), provider);

        assertThat(policy.allowedNames()).contains("VALID_EXTRA")
                .doesNotContain("INVALID-NAME", "HAS=VALUE");
        assertThat(child).containsEntry("VALID_EXTRA", "kept")
                .containsEntry("GOOD_NAME", "good")
                .doesNotContainKeys("INVALID-NAME", "HAS=VALUE", "BAD-NAME", "NULL_VALUE");
    }

    @Test
    void applyReplacesTheBuilderEnvironment() {
        CliProcessEnvironment policy = new CliProcessEnvironment(List.of());
        ProcessBuilder builder = new ProcessBuilder("sh", "-c", "true");
        builder.environment().clear();
        builder.environment().put("PATH", "/usr/bin");
        builder.environment().put("SERVER_SECRET", "must-not-cross-boundary");

        policy.apply(builder, Map.of("PROVIDER_TOKEN", "provider-secret"));

        assertThat(builder.environment()).containsOnly(
                Map.entry("PATH", "/usr/bin"),
                Map.entry("PROVIDER_TOKEN", "provider-secret"));
    }
}
