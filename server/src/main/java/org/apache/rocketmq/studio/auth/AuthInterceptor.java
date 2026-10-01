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
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class AuthInterceptor implements HandlerInterceptor {

    private static final String TOOL_EXECUTION_PREFIX = "/api/ai/tools/";
    private static final String TOOL_EXECUTION_SUFFIX = "/execute";

    /**
     * POST paths a reader may call. Deliberately does <strong>not</strong> grow to cover the AI
     * conversation surface — {@link #requiresAdmin} explains why.
     */
    private static final Set<String> READER_POST_PATHS = Set.of(
            "/api/auth/logout",
            "/api/auth/password",
            "/api/metrics/query",
            "/api/metrics/query/datasource");

    private final AuthProperties authProperties;
    private final AuthService authService;
    private final SettingsRepository settingsRepository;
    private final ToolCatalog toolCatalog;

    public AuthInterceptor(AuthProperties authProperties, AuthService authService,
                           SettingsRepository settingsRepository, ToolCatalog toolCatalog) {
        this.authProperties = authProperties;
        this.authService = authService;
        this.settingsRepository = settingsRepository;
        this.toolCatalog = toolCatalog;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        AuthenticatedUserContext.clear();
        if (!isLoginRequired() || CorsUtils.isPreFlightRequest(request)
                || isPublicPath(requestPath(request))) {
            return true;
        }
        String authorization = AuthCookie.authorization(request, authProperties);
        var authenticatedUser = authService.getAuthenticatedUser(authorization).orElse(null);
        if (authenticatedUser == null) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Unauthorized");
            return false;
        }

        if (requiresAdmin(request, requestPath(request))
                && !authenticatedUser.isAdmin()) {
            writeError(response, HttpStatus.FORBIDDEN, "Admin permission required");
            return false;
        }
        // The principal is published only once the request is admitted. When preHandle returns false
        // Spring never invokes this interceptor's own afterCompletion, so a context set before a
        // rejection would stay on the pooled thread until that thread's next /api/** request.
        AuthenticatedUserContext.setUser(
                authenticatedUser.getUserId(),
                authenticatedUser.getUsername(),
                authenticatedUser.isAdmin());
        return true;
    }

    /**
     * Login enforcement comes from the static {@code studio.auth.login-required} property OR the
     * runtime "requireLogin" toggle persisted in the settings database, so toggling it in the
     * settings UI actually changes the enforced policy.
     */
    private boolean isLoginRequired() {
        if (authProperties.isLoginRequired()) {
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

    /**
     * Whether the request needs an administrator. Reads are open to every authenticated operator except
     * the credential views listed in {@link #isAdminOnlyGetPath}; writes are admin-only except the POST
     * paths in {@link #READER_POST_PATHS} and the low-risk read-only tools.
     *
     * <h2>The AI conversation surface is a write surface, and stays admin-only</h2>
     * {@code POST /api/ai/conversations}, {@code POST /api/ai/conversations/{id}/messages},
     * {@code POST /api/ai/runs/{id}/stop} and {@code PATCH}/{@code DELETE} on a conversation all fall
     * through to the admin requirement below. That is a decision, not an oversight of the reader
     * allow-list, and the reasoning matters because the endpoints replaced one a reader could call:
     * {@code POST /api/ai/chat} ran a CLI with every built-in tool disabled and no MCP server attached,
     * so it was a text generator. {@code POST …/messages} drives an agent whose tool calls are signed
     * with the <em>instance credential the server resolves</em> ({@code InstanceCredentialResolver}),
     * not with the caller's identity, so a reader who could start a run could perform exactly the L2
     * mutations {@link #isReaderAccessibleToolPath} exists to refuse that reader in the Tool Playground.
     * "The old endpoint was reader-accessible, therefore the new one may be" does not hold.
     *
     * <p>What a reader keeps is every GET, {@code GET /api/ai/runs/{runId}/stream} included: reading
     * one's own conversations, transcripts and an answer still in flight drives no tool. Owner scoping
     * ({@code AiConversationService.requireOwned}, which answers 404 for somebody else's id) applies to
     * all of them regardless of role, so role and ownership are two independent filters.
     *
     * <p>{@code /api/mcp/**} never reaches this method: {@code AuthWebConfig} excludes it from the
     * interceptor and it authenticates with its own HMAC filter, where holding the instance credential
     * is itself the authorisation.
     */
    private boolean requiresAdmin(HttpServletRequest request, String path) {
        String method = request.getMethod();
        if (HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method)
                || HttpMethod.OPTIONS.matches(method)) {
            // Read endpoints stay open to readers, except credential views that expose secrets.
            return isAdminOnlyGetPath(path);
        }
        String normalizedPath = normalizePath(stripPathParameters(path));
        if (isToolExecutionPath(normalizedPath)) {
            return !isReaderAccessibleToolPath(normalizedPath);
        }
        return !HttpMethod.POST.matches(method) || !READER_POST_PATHS.contains(normalizePath(path));
    }

    private boolean isToolExecutionPath(String path) {
        return path.startsWith(TOOL_EXECUTION_PREFIX)
                && path.endsWith(TOOL_EXECUTION_SUFFIX);
    }

    private boolean isReaderAccessibleToolPath(String path) {
        String encodedName = path.substring(
                TOOL_EXECUTION_PREFIX.length(),
                path.length() - TOOL_EXECUTION_SUFFIX.length());
        if (encodedName.isBlank()) {
            return false;
        }
        try {
            String toolName = URLDecoder.decode(encodedName, StandardCharsets.UTF_8);
            return toolCatalog.find(toolName)
                    .map(ToolDefinition::isLowRiskReadOnly)
                    .orElse(false);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isAdminOnlyGetPath(String path) {
        String normalizedPath = normalizePath(stripPathParameters(path));
        return "/api/llm/config".equals(normalizedPath)
                || "/api/llm/models".equals(normalizedPath)
                || isStudioUserPath(normalizedPath)
                || isCloudCatalogPath(normalizedPath)
                || "/api/acl/remote/rules".equals(normalizedPath)
                || isCredentialRevealPath(normalizedPath, "/api/acl/users/")
                || isCredentialRevealPath(normalizedPath, "/api/cloud-credentials/");
    }

    private boolean isStudioUserPath(String path) {
        return "/api/studio-users".equals(path) || path.startsWith("/api/studio-users/");
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
                || path.equals("/livez")
                || path.equals("/readyz")
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
