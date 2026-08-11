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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final Set<String> READER_POST_PATHS = Set.of(
            "/api/auth/logout",
            "/api/ai/chat",
            "/api/metrics/query",
            "/api/metrics/query/datasource");

    private final AuthProperties authProperties;
    private final AuthService authService;
    private final SettingsRepository settingsRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        AuthenticatedUserContext.clear();
        if (!isLoginRequired() || CorsUtils.isPreFlightRequest(request)
                || isPublicPath(requestPath(request))) {
            return true;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!authService.isAuthenticated(authorization)) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Unauthorized");
            return false;
        }
        authService.getAuthenticatedUser(authorization)
                .ifPresent(user -> AuthenticatedUserContext.setUsername(user.getUsername()));
        if (requiresAdmin(request, requestPath(request)) && !authService.isAdmin(authorization)) {
            writeError(response, HttpStatus.FORBIDDEN, "Admin permission required");
            return false;
        }
        return true;
    }

    /**
     * Login enforcement comes from the static {@code studio.auth.login-required} property OR the
     * runtime "requireLogin" toggle persisted in the settings database, so toggling it in the
     * settings UI actually changes the enforced policy.
     */
    private boolean isLoginRequired() {
        if (authProperties != null && authProperties.isLoginRequired()) {
            return true;
        }
        if (settingsRepository == null) {
            return true;
        }
        try {
            GeneralSettingsVO settings = settingsRepository.loadGeneralSettings();
            return settings == null || settings.isRequireLogin();
        } catch (Exception exception) {
            // Fail closed: when the policy cannot be read, default to requiring login.
            return true;
        }
    }

    private boolean requiresAdmin(HttpServletRequest request, String path) {
        String method = request.getMethod();
        if (HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method)
                || HttpMethod.OPTIONS.matches(method)) {
            // Read endpoints stay open to readers, except credential views that expose secrets.
            return isAdminOnlyGetPath(path);
        }
        return !HttpMethod.POST.matches(method) || !READER_POST_PATHS.contains(normalizePath(path));
    }

    private boolean isAdminOnlyGetPath(String path) {
        String normalizedPath = normalizePath(stripPathParameters(path));
        return "/api/llm/models".equals(normalizedPath)
                || isCloudCatalogPath(normalizedPath)
                || isCredentialRevealPath(normalizedPath, "/api/acl/users/")
                || isCredentialRevealPath(normalizedPath, "/api/cloud-credentials/");
    }

    private boolean isCloudCatalogPath(String path) {
        return path.startsWith("/api/cloud/aliyun/")
                || path.startsWith("/api/cloud/tencent/");
    }

    private boolean isCredentialRevealPath(String path, String prefix) {
        return path != null && path.startsWith(prefix) && path.endsWith("/credentials");
    }

    private String stripPathParameters(String path) {
        if (path == null || path.indexOf(';') < 0) {
            return path;
        }
        StringBuilder stripped = new StringBuilder(path.length());
        boolean insideParameters = false;
        for (int index = 0; index < path.length(); index++) {
            char character = path.charAt(index);
            if (character == ';') {
                insideParameters = true;
            } else if (character == '/') {
                insideParameters = false;
                stripped.append(character);
            } else if (!insideParameters) {
                stripped.append(character);
            }
        }
        return stripped.toString();
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message)
            throws Exception {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":" + status.value()
                + ",\"message\":\"" + message + "\",\"data\":null}");
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) throws Exception {
        AuthenticatedUserContext.clear();
    }

    private boolean isPublicPath(String path) {
        path = normalizePath(path);
        return path.equals("/api/auth/login")
                || path.equals("/api/auth/status")
                || path.startsWith("/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/actuator/health");
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        if (path.equals("/")) {
            return path;
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private String requestPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isBlank()) {
            return servletPath;
        }
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();
        if (contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }
}
