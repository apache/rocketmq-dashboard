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

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

final class AuthCookie {

    private static final String TOKEN_PREFIX = "Bearer ";
    private static final String DEFAULT_COOKIE_NAME = "rmq_studio_session";
    private static final String DEFAULT_SAME_SITE = "Strict";
    static final String SESSION_DELIVERY_HEADER = "X-RocketMQ-Studio-Session-Delivery";
    private static final String BEARER_SESSION_DELIVERY = "bearer";

    private AuthCookie() {
    }

    static String authorization(HttpServletRequest request, AuthProperties properties) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (hasBearerToken(authorization)) {
            return authorization;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieName(properties).equals(cookie.getName())
                        && !cookie.getValue().isBlank()) {
                    return TOKEN_PREFIX + cookie.getValue();
                }
            }
        }
        // API clients that explicitly requested a bearer token at login authenticate with this header.
        return authorization;
    }

    private static boolean hasBearerToken(String authorization) {
        return authorization != null
                && authorization.regionMatches(true, 0, TOKEN_PREFIX, 0, TOKEN_PREFIX.length())
                && !authorization.substring(TOKEN_PREFIX.length()).isBlank();
    }

    static boolean requestsBearerToken(HttpServletRequest request) {
        return BEARER_SESSION_DELIVERY.equalsIgnoreCase(request.getHeader(SESSION_DELIVERY_HEADER));
    }

    static void write(HttpServletResponse response, AuthProperties properties, String token,
                      Duration maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(cookieName(properties), token)
                .httpOnly(true)
                .secure(properties.isSessionCookieSecure())
                .sameSite(sameSite(properties))
                .path("/")
                .maxAge(maxAge)
                .build()
                .toString());
    }

    static void clear(HttpServletResponse response, AuthProperties properties) {
        write(response, properties, "", Duration.ZERO);
    }

    private static String cookieName(AuthProperties properties) {
        String name = properties.getSessionCookieName();
        return name == null || name.isBlank() ? DEFAULT_COOKIE_NAME : name;
    }

    private static String sameSite(AuthProperties properties) {
        String sameSite = properties.getSessionCookieSameSite();
        return sameSite == null || sameSite.isBlank() ? DEFAULT_SAME_SITE : sameSite;
    }
}
