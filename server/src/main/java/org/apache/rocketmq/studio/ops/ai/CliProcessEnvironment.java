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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Builds the environment for CLI-backed AI providers without exposing every
 * variable from the Studio server process.
 */
@Component
public class CliProcessEnvironment {

    private static final Pattern ENVIRONMENT_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /**
     * Runtime variables used to find executables and user-scoped CLI state,
     * create temporary files, select a locale, and reach the provider through
     * explicitly configured proxies or certificate stores.
     */
    private static final List<String> DEFAULT_ALLOWED_NAMES = List.of(
            "PATH",
            "HOME",
            "USERPROFILE",
            "XDG_CONFIG_HOME",
            "XDG_CACHE_HOME",
            "XDG_DATA_HOME",
            "TMPDIR",
            "TMP",
            "TEMP",
            "LANG",
            "LANGUAGE",
            "LC_ALL",
            "LC_CTYPE",
            "TERM",
            "SSL_CERT_FILE",
            "SSL_CERT_DIR",
            "NODE_EXTRA_CA_CERTS",
            "HTTP_PROXY",
            "HTTPS_PROXY",
            "NO_PROXY",
            "http_proxy",
            "https_proxy",
            "no_proxy",
            "SystemRoot",
            "ComSpec",
            "PATHEXT");

    private final Set<String> allowedNames;

    @Autowired
    public CliProcessEnvironment(LlmProperties properties) {
        this(properties == null ? List.of() : properties.getCliAllowedEnvironment());
    }

    CliProcessEnvironment(Collection<String> additionalAllowedNames) {
        LinkedHashSet<String> names = new LinkedHashSet<>(DEFAULT_ALLOWED_NAMES);
        if (additionalAllowedNames != null) {
            additionalAllowedNames.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .filter(CliProcessEnvironment::isValidName)
                    .forEach(names::add);
        }
        this.allowedNames = Collections.unmodifiableSet(names);
    }

    /**
     * Replaces the builder's inherited environment with the isolated child
     * environment. Provider-specific values are applied last so the selected
     * request configuration wins over any allowed parent value.
     */
    void apply(ProcessBuilder builder, Map<String, String> providerEnvironment) {
        Map<String, String> target = builder.environment();
        Map<String, String> isolated = build(target, providerEnvironment);
        target.clear();
        target.putAll(isolated);
    }

    Map<String, String> build(Map<String, String> parentEnvironment,
                              Map<String, String> providerEnvironment) {
        Map<String, String> result = new LinkedHashMap<>();
        if (parentEnvironment != null) {
            for (String name : allowedNames) {
                if (parentEnvironment.containsKey(name)) {
                    String value = parentEnvironment.get(name);
                    if (value != null) {
                        result.put(name, value);
                    }
                }
            }
        }
        if (providerEnvironment != null) {
            providerEnvironment.forEach((name, value) -> {
                if (isValidName(name) && value != null) {
                    result.put(name, value);
                }
            });
        }
        return result;
    }

    Set<String> allowedNames() {
        return allowedNames;
    }

    private static boolean isValidName(String name) {
        return name != null && ENVIRONMENT_NAME.matcher(name).matches();
    }
}
