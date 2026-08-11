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
package org.apache.rocketmq.studio.common.util;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Shared SSRF guard for server-side HTTP endpoints that accept a caller-supplied URL
 * (data-source test/save/query paths and the LLM gateway configuration).
 *
 * <p>Rejects hosts that resolve to loopback, link-local (including the cloud metadata
 * range {@code 169.254.169.254}) or any-local addresses. Unlike the old per-service
 * checks, unresolvable hosts are rejected (fail-closed) instead of being handed to the
 * connection attempt, and the same rule is applied on every path — save, test and query —
 * so a host cannot be stored once and queried later.
 *
 * <p>Private site-local ranges (10.x, 172.16-31.x, 192.168.x) stay allowed: on-premise
 * Prometheus servers and internal LLM gateways legitimately live on the internal network.
 * When {@code allowLoopback} is set (LLM config, where a local {@code ollama} gateway is
 * a supported provider) loopback is admitted but link-local/metadata addresses are still
 * rejected.
 */
public final class UrlHostGuard {

    private UrlHostGuard() {
    }

    /**
     * Validates that {@code url} is an http(s) URL whose host passes the SSRF guard.
     *
     * @param url            the caller-supplied URL
     * @param allowLoopback  whether loopback hosts ({@code localhost}, 127.x.x.x, ::1)
     *                       are acceptable — used for local LLM gateways such as ollama
     * @throws IllegalArgumentException when the URL is missing, non-http(s), hostless or
     *                                  points at a disallowed address
     */
    public static void check(String url, boolean allowLoopback) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL is required");
        }
        String normalized = url.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        URI uri;
        try {
            uri = new URI(normalized);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("URL is not a valid URI");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("URL must start with http:// or https://");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("URL must include a host");
        }
        if (!isAllowedHost(uri.getHost(), allowLoopback)) {
            throw new IllegalArgumentException(
                    "URL must not point to a local, loopback or metadata address");
        }
    }

    /**
     * Whether {@code host} is allowed by the SSRF guard.
     *
     * @param host           the hostname or IP literal
     * @param allowLoopback  see {@link #check(String, boolean)}
     * @return {@code true} when the host is safe to connect to
     */
    public static boolean isAllowedHost(String host, boolean allowLoopback) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        // Strip a single trailing dot (fully-qualified names like "host.example.com.").
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if ("localhost".equals(normalized)) {
            return allowLoopback;
        }
        try {
            InetAddress address = InetAddress.getByName(normalized);
            if (address.isAnyLocalAddress() || address.isLinkLocalAddress()
                    || address.isMulticastAddress()) {
                return false;
            }
            if (address.isLoopbackAddress()) {
                return allowLoopback;
            }
            return true;
        } catch (UnknownHostException exception) {
            // Fail closed: an unresolvable host must not be handed to the connection layer.
            return false;
        }
    }
}
