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
 *
 * <h2>Two layers, and only the first one is filtered</h2>
 * <ol>
 *   <li><strong>Inherited</strong>: the allow-list below, copied out of the server process. This is
 *       the isolation boundary — it is what keeps {@code SPRING_DATASOURCE_PASSWORD},
 *       {@code STUDIO_AUTH_ADMIN_PASSWORD} or a cloud AK/SK that happens to sit in the container
 *       environment from reaching a subprocess we do not control.</li>
 *   <li><strong>Provider-supplied</strong>: applied afterwards and <em>not</em> filtered through the
 *       allow-list, so a provider entry also wins over an inherited value of the same name. That is
 *       deliberate rather than a hole: these are values this request produced (the Anthropic token,
 *       the per-run rmqctl credential) and they exist nowhere in the parent to be inherited from.
 *       They are still rejected unless the name is a valid environment identifier and the value is
 *       non-null.</li>
 * </ol>
 *
 * <p>Consequence worth stating explicitly, because it is easy to "fix" in the wrong direction: the
 * rmqctl credential variables ({@code RMQ_AI_ACCESS_KEY} / {@code RMQ_AI_SECRET_KEY}) are
 * <strong>not</strong> added to the allow-list. They arrive as provider entries per run, which is both
 * sufficient and safer — allow-listing them would additionally copy any value the server process
 * happens to carry under those names into every child. The same reasoning rules out
 * {@code RMQCTL_CONFIG}: the workspace passes {@code --config} explicitly, so allow-listing it would
 * be pure exposure. {@code CliProcessEnvironmentTest} pins the allow-list to catch a silent widening.
 *
 * <p>{@code HOME} <em>is</em> allow-listed, and a stable HOME is what makes {@code claude --resume}
 * work. But a container often does not define HOME at all, and the hosted agent needs a
 * <em>per-conversation</em> HOME rather than the server's, so callers that care set it as a provider
 * entry (see {@code RmqctlWorkspace}) instead of relying on inheritance.
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
     * Replaces the builder's inherited environment with the isolated child environment.
     *
     * <p>Package-private and non-final on purpose: it is the seam the isolation tests override to
     * record exactly what crossed the process boundary. Production callers inside this package use
     * it; callers elsewhere use {@link #applyIsolated(ProcessBuilder, Map)}.
     */
    void apply(ProcessBuilder builder, Map<String, String> providerEnvironment) {
        applyIsolated(builder, providerEnvironment);
    }

    /**
     * Public form of {@link #apply(ProcessBuilder, Map)} for callers outside this package, e.g. the
     * {@code rmqctl} availability probe.
     *
     * <p>Provider-specific values are applied last and are <strong>not</strong> filtered through the
     * allow-list. That is deliberate, not an oversight: the allow-list protects the child from
     * inheriting the server's environment, whereas provider entries are values this request itself
     * produced (an ANTHROPIC token, the per-run rmqctl credentials) and they exist nowhere in the
     * parent to be inherited from. They still have to satisfy {@link #isValidName(String)} and be
     * non-null, and because they are applied after the allow-list copy they win over any parent value
     * with the same name.
     */
    public void applyIsolated(ProcessBuilder builder, Map<String, String> providerEnvironment) {
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
