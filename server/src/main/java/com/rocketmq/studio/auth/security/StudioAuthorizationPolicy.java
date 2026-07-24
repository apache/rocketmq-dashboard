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

import java.util.List;
import java.util.Objects;

import com.rocketmq.studio.auth.security.StudioUserRegistry.Role;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

@Component
public final class StudioAuthorizationPolicy {
    private static final PathPattern HEALTH_PATH = new PathPatternParser()
        .parse("/actuator/health");
    private static final PathPattern HEALTH_SUBTREE_PATH = new PathPatternParser()
        .parse("/actuator/health/**");
    private static final List<Route> ROUTES = List.of(
        route(HttpMethod.POST, "/api/ai/chat", Access.USER),
        route(HttpMethod.POST, "/api/ai/execute", Access.ADMIN),
        route(HttpMethod.GET, "/api/ai/tools", Access.USER),
        route(HttpMethod.GET, "/api/audit-logs", Access.ADMIN),
        route(HttpMethod.POST, "/api/audit-logs/cleanup", Access.ADMIN),
        route(HttpMethod.GET, "/api/dashboard", Access.USER),
        route(HttpMethod.GET, "/api/alert-rules", Access.USER),
        route(HttpMethod.POST, "/api/alert-rules/create", Access.ADMIN),
        route(HttpMethod.POST, "/api/alert-rules/update", Access.ADMIN),
        route(HttpMethod.POST, "/api/alert-rules/toggle", Access.ADMIN),
        route(HttpMethod.POST, "/api/alert-rules/delete", Access.ADMIN),
        route(HttpMethod.GET, "/api/system-alerts", Access.USER),
        route(HttpMethod.POST, "/api/system-alerts/acknowledge", Access.ADMIN),
        route(HttpMethod.POST, "/api/system-alerts/clear-acknowledged", Access.ADMIN),
        route(HttpMethod.GET, "/api/acl/rules", Access.ADMIN),
        route(HttpMethod.POST, "/api/acl/rules/create", Access.ADMIN),
        route(HttpMethod.POST, "/api/acl/rules/delete", Access.ADMIN),
        route(HttpMethod.GET, "/api/acl/users", Access.ADMIN),
        route(HttpMethod.POST, "/api/acl/users/create", Access.ADMIN),
        route(HttpMethod.POST, "/api/acl/users/delete", Access.ADMIN),
        route(HttpMethod.GET, "/api/settings/general", Access.ADMIN),
        route(HttpMethod.POST, "/api/settings/general/save", Access.ADMIN),
        route(HttpMethod.GET, "/api/settings/datasources", Access.ADMIN),
        route(HttpMethod.POST, "/api/settings/datasources/create", Access.ADMIN),
        route(HttpMethod.POST, "/api/settings/datasources/update", Access.ADMIN),
        route(HttpMethod.POST, "/api/settings/datasources/delete", Access.ADMIN),
        route(HttpMethod.POST, "/api/settings/datasources/test", Access.ADMIN),
        route(HttpMethod.POST, "/api/auth/login", Access.PUBLIC),
        route(HttpMethod.POST, "/api/auth/logout", Access.USER),
        route(HttpMethod.GET, "/api/instances", Access.USER),
        route(HttpMethod.POST, "/api/instances/create", Access.ADMIN),
        route(HttpMethod.POST, "/api/instances/update", Access.ADMIN),
        route(HttpMethod.POST, "/api/instances/delete", Access.ADMIN),
        route(HttpMethod.GET, "/api/clients", Access.USER),
        route(HttpMethod.GET, "/api/dlq", Access.USER),
        route(HttpMethod.POST, "/api/dlq/resend", Access.ADMIN),
        route(HttpMethod.GET, "/api/groups", Access.USER),
        route(HttpMethod.GET, "/api/groups/{name}", Access.USER),
        route(HttpMethod.GET, "/api/groups/{name}/progress", Access.USER),
        route(HttpMethod.GET, "/api/groups/{name}/subscriptions", Access.USER),
        route(HttpMethod.POST, "/api/groups/create", Access.ADMIN),
        route(HttpMethod.POST, "/api/groups/delete", Access.ADMIN),
        route(HttpMethod.POST, "/api/groups/reset-offset", Access.ADMIN),
        route(HttpMethod.GET, "/api/namespaces", Access.USER),
        route(HttpMethod.POST, "/api/nameservers/create", Access.ADMIN),
        route(HttpMethod.POST, "/api/nameservers/update", Access.ADMIN),
        route(HttpMethod.POST, "/api/nameservers/restart", Access.ADMIN),
        route(HttpMethod.POST, "/api/nameservers/upgrade", Access.ADMIN),
        route(HttpMethod.POST, "/api/nameservers/delete", Access.ADMIN),
        route(HttpMethod.GET, "/api/k8s-certs", Access.ADMIN),
        route(HttpMethod.POST, "/api/k8s-certs/create", Access.ADMIN),
        route(HttpMethod.POST, "/api/k8s-certs/update", Access.ADMIN),
        route(HttpMethod.POST, "/api/k8s-certs/renew", Access.ADMIN),
        route(HttpMethod.POST, "/api/k8s-certs/delete", Access.ADMIN),
        route(HttpMethod.GET, "/api/messages", Access.USER),
        route(HttpMethod.GET, "/api/messages/{msgId}/trace", Access.USER),
        route(HttpMethod.GET, "/api/topics", Access.USER),
        route(HttpMethod.POST, "/api/topics/create", Access.ADMIN),
        route(HttpMethod.POST, "/api/topics/update", Access.ADMIN),
        route(HttpMethod.POST, "/api/topics/delete", Access.ADMIN),
        route(HttpMethod.GET, "/api/topics/{name}/routes", Access.USER),
        route(HttpMethod.GET, "/api/topics/{name}/consumers", Access.USER),
        route(HttpMethod.POST, "/api/topics/send", Access.ADMIN),
        route(HttpMethod.GET, "/api/clusters", Access.USER),
        route(HttpMethod.GET, "/api/clusters/{id}", Access.USER),
        route(HttpMethod.POST, "/api/clusters/config/update", Access.ADMIN),
        route(
            HttpMethod.POST,
            "/api/clusters/{clusterId}/brokers/{name}/restart",
            Access.ADMIN
        ),
        route(HttpMethod.POST, "/api/proxies/restart", Access.ADMIN),
        route(HttpMethod.POST, "/api/metrics/query", Access.USER),
        route(HttpMethod.GET, "/api/proxy/homePage.query", Access.ADMIN),
        route(HttpMethod.GET, "/api/producer/connection", Access.ADMIN),
        route(HttpMethod.POST, "/api/ops/updateIsVIPChannel", Access.ADMIN),
        route(HttpMethod.POST, "/api/llm/config/test", Access.ADMIN),
        route(HttpMethod.GET, "/api/ops/homePage", Access.ADMIN),
        route(HttpMethod.GET, "/api/liteTopic/session/{sessionId}", Access.ADMIN),
        route(HttpMethod.GET, "/api/liteTopic/quota", Access.ADMIN),
        route(HttpMethod.GET, "/api/liteTopic/list", Access.ADMIN),
        route(HttpMethod.POST, "/api/ops/updateNameSvrAddr", Access.ADMIN),
        route(HttpMethod.POST, "/api/proxy/addProxyAddr.do", Access.ADMIN),
        route(
            HttpMethod.GET,
            "/api/groups/{name}/instances/{clientId}/stack",
            Access.ADMIN
        ),
        route(HttpMethod.GET, "/api/alert/rules", Access.ADMIN),
        route(HttpMethod.GET, "/api/llm/config", Access.ADMIN),
        route(HttpMethod.POST, "/api/ops/addNameSvrAddr", Access.ADMIN),
        route(HttpMethod.GET, "/api/llm/models", Access.ADMIN),
        route(HttpMethod.GET, "/api/liteTopic/capability", Access.ADMIN),
        route(HttpMethod.POST, "/api/acl/users/update", Access.ADMIN),
        route(HttpMethod.POST, "/api/ai/tools/{name}/execute", Access.ADMIN),
        route(HttpMethod.POST, "/api/liteTopic/extendTTL", Access.ADMIN),
        route(HttpMethod.POST, "/api/llm/config", Access.ADMIN),
        route(HttpMethod.POST, "/api/ops/updateUseTLS", Access.ADMIN),
        route(HttpMethod.POST, "/api/acl/rules/update", Access.ADMIN)
    );

    private final List<CompiledRoute> compiledRoutes;
    private final AuthorizationManager<RequestAuthorizationContext> authorizationManager;

    public StudioAuthorizationPolicy() {
        PathPatternParser parser = new PathPatternParser();
        compiledRoutes = ROUTES.stream()
            .map(route -> new CompiledRoute(route, parser.parse(route.mvcPattern())))
            .toList();
        authorizationManager = (authentication, context) -> {
            Access required = access(context.getRequest());
            if (required == Access.PUBLIC) {
                return new AuthorizationDecision(true);
            }
            Object principal = authentication.get().getPrincipal();
            if (!(principal instanceof StudioPrincipal studioPrincipal)) {
                return new AuthorizationDecision(false);
            }
            boolean granted = required == Access.USER || studioPrincipal.role() == Role.ADMIN;
            return new AuthorizationDecision(granted);
        };
    }

    public List<Route> routes() {
        return ROUTES;
    }

    public Access access(HttpServletRequest request) {
        Objects.requireNonNull(request, "request");
        if (CorsUtils.isPreFlightRequest(request)) {
            return Access.PUBLIC;
        }
        try {
            if (isPublicHealthProbe(request)) {
                return Access.PUBLIC;
            }
            HttpMethod method = HttpMethod.valueOf(request.getMethod());
            PathContainer path = requestPath(request).pathWithinApplication();
            return compiledRoutes.stream()
                .filter(route -> route.route().method() == method)
                .filter(route -> route.pattern().matches(path))
                .map(route -> route.route().access())
                .findFirst()
                .orElse(Access.ADMIN);
        } catch (RuntimeException exception) {
            return Access.ADMIN;
        }
    }

    public AuthorizationManager<RequestAuthorizationContext> authorizationManager() {
        return authorizationManager;
    }

    private static RequestPath requestPath(HttpServletRequest request) {
        if (ServletRequestPathUtils.hasParsedRequestPath(request)) {
            return ServletRequestPathUtils.getParsedRequestPath(request);
        }
        return ServletRequestPathUtils.parseAndCache(request);
    }

    private static boolean isPublicHealthProbe(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        PathContainer path = requestPath(request).pathWithinApplication();
        return HEALTH_PATH.matches(path) || HEALTH_SUBTREE_PATH.matches(path);
    }

    private static Route route(HttpMethod method, String mvcPattern, Access access) {
        return new Route(method, mvcPattern, access);
    }

    public enum Access {
        PUBLIC,
        USER,
        ADMIN
    }

    public record Route(HttpMethod method, String mvcPattern, Access access) {
        public Route {
            Objects.requireNonNull(method, "method");
            Objects.requireNonNull(mvcPattern, "mvcPattern");
            Objects.requireNonNull(access, "access");
            if (access == Access.USER && mvcPattern.contains("/**")) {
                throw new IllegalArgumentException("USER routes must be exact MVC patterns");
            }
        }
    }

    private record CompiledRoute(Route route, PathPattern pattern) {
    }
}
