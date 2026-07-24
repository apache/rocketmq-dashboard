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

import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.rocketmq.studio.auth.security.StudioAuthorizationPolicy.Access;
import com.rocketmq.studio.auth.security.StudioSessionStore.Session;
import com.rocketmq.studio.auth.security.StudioUserRegistry.Snapshot;
import com.rocketmq.studio.auth.security.StudioUserRegistry.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class StudioBearerAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER = "Bearer";
    private static final int TOKEN_LENGTH = 43;
    private static final int AUTHORIZATION_LENGTH = BEARER.length() + 1 + TOKEN_LENGTH;

    private final StudioAuthorizationPolicy policy;
    private final StudioSessionStore sessions;
    private final StudioUserRegistry registry;
    private final StudioSecurityResponses responses;

    public StudioBearerAuthenticationFilter(
        StudioAuthorizationPolicy policy,
        StudioSessionStore sessions,
        StudioUserRegistry registry,
        StudioSecurityResponses responses
    ) {
        this.policy = policy;
        this.sessions = sessions;
        this.registry = registry;
        this.responses = responses;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return policy.access(request) == Access.PUBLIC;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        AuthorizationHeader header = authorization(request);
        if (!header.present()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (header.token() == null) {
            reject(response);
            return;
        }

        Session session = resolve(header.token());
        if (session == null) {
            reject(response);
            return;
        }
        if (!current(session)) {
            revoke(session);
            reject(response);
            return;
        }

        StudioPrincipal principal = new StudioPrincipal(
            session.id(),
            session.username(),
            session.role()
        );
        UsernamePasswordAuthenticationToken authentication =
            UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + session.role().name()))
            );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        filterChain.doFilter(request, response);
    }

    private static AuthorizationHeader authorization(HttpServletRequest request) {
        Enumeration<String> values;
        try {
            values = request.getHeaders(HttpHeaders.AUTHORIZATION);
        } catch (RuntimeException exception) {
            return AuthorizationHeader.malformed();
        }
        if (values == null) {
            return AuthorizationHeader.absent();
        }

        int count = 0;
        String onlyValue = null;
        try {
            while (values.hasMoreElements()) {
                String value = values.nextElement();
                count++;
                if (count == 1) {
                    onlyValue = value;
                }
            }
        } catch (RuntimeException exception) {
            return AuthorizationHeader.malformed();
        }
        if (count == 0) {
            return AuthorizationHeader.absent();
        }
        if (count != 1) {
            return AuthorizationHeader.malformed();
        }
        return new AuthorizationHeader(true, token(onlyValue));
    }

    private static String token(String header) {
        if (header == null
            || header.length() != AUTHORIZATION_LENGTH
            || header.indexOf(',') >= 0
            || header.indexOf('\r') >= 0
            || header.indexOf('\n') >= 0
            || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())
            || header.charAt(BEARER.length()) != ' ') {
            return null;
        }
        String token = header.substring(BEARER.length() + 1);
        for (int index = 0; index < token.length(); index++) {
            char current = token.charAt(index);
            boolean base64Url = current >= 'A' && current <= 'Z'
                || current >= 'a' && current <= 'z'
                || current >= '0' && current <= '9'
                || current == '-'
                || current == '_';
            if (!base64Url) {
                return null;
            }
        }
        return token;
    }

    private Session resolve(String token) {
        try {
            Optional<Session> resolved = sessions.resolve(token);
            return resolved == null ? null : resolved.orElse(null);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private boolean current(Session session) {
        try {
            Snapshot snapshot = registry.snapshot();
            if (snapshot == null
                || !snapshot.available()
                || snapshot.revision() != session.registryRevision()) {
                return false;
            }
            Map<String, User> users = snapshot.users();
            User user = users == null ? null : users.get(session.username());
            return user != null
                && Objects.equals(user.username(), session.username())
                && user.role() == session.role()
                && Objects.equals(user.fingerprint(), session.userFingerprint());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void revoke(Session session) {
        try {
            sessions.revoke(session.id());
        } catch (RuntimeException exception) {
            // Authentication still fails closed if session cleanup is unavailable.
        }
    }

    private void reject(HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        responses.unauthorized(response);
    }

    private record AuthorizationHeader(boolean present, String token) {
        private static AuthorizationHeader absent() {
            return new AuthorizationHeader(false, null);
        }

        private static AuthorizationHeader malformed() {
            return new AuthorizationHeader(true, null);
        }
    }
}
