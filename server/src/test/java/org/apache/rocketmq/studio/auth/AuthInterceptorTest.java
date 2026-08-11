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

import org.junit.jupiter.api.AfterEach;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthInterceptorTest {

    @AfterEach
    void clearAuthenticatedUser() {
        AuthenticatedUserContext.clear();
    }

    @Test
    void shouldAllowRequestsWhenLoginIsDisabled() throws Exception {
        AuthProperties properties = new AuthProperties();
        AuthInterceptor interceptor = new AuthInterceptor(properties, authService(properties), settingsRepository());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clusters");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
        assertThat(AuthenticatedUserContext.currentUsernameOrSystem())
                .isEqualTo(AuthenticatedUserContext.SYSTEM_ACTOR);
    }

    @Test
    void shouldRejectProtectedApiWithoutTokenWhenLoginIsEnabled() throws Exception {
        AuthProperties properties = new AuthProperties();
        properties.setLoginRequired(true);
        AuthInterceptor interceptor = new AuthInterceptor(properties, authService(properties), settingsRepository());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clusters");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Unauthorized");
    }

    @Test
    void shouldAllowProtectedApiWithActiveTokenWhenLoginIsEnabled() throws Exception {
        AuthProperties properties = new AuthProperties();
        properties.setLoginRequired(true);
        AuthProperties.User user = new AuthProperties.User();
        user.setUsername("admin");
        user.setPassword("secret");
        user.setAdmin(true);
        properties.setUsers(List.of(user));
        AuthService authService = authService(properties);
        AuthInterceptor interceptor = new AuthInterceptor(properties, authService, settingsRepository());
        LoginDTO login = new LoginDTO();
        login.setUsername("admin");
        login.setPassword("secret");
        String token = authService.login(login).getToken();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clusters");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        MockHttpServletResponse response = new MockHttpServletResponse();
        Object handler = new Object();
        boolean allowed = interceptor.preHandle(request, response, handler);

        assertThat(allowed).isTrue();
        assertThat(AuthenticatedUserContext.currentUsernameOrSystem()).isEqualTo("admin");

        interceptor.afterCompletion(request, response, handler, null);

        assertThat(AuthenticatedUserContext.currentUsernameOrSystem())
                .isEqualTo(AuthenticatedUserContext.SYSTEM_ACTOR);
    }

    @Test
    void shouldEnforceLoginWhenDatabaseRequiresItEvenIfPropertyIsDisabled() throws Exception {
        AuthProperties properties = new AuthProperties();
        AuthInterceptor interceptor = new AuthInterceptor(properties, authService(properties),
                settingsRepositoryRequiringLogin());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clusters");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void shouldAllowLoginEndpointWhenLoginIsEnabled() throws Exception {
        AuthProperties properties = new AuthProperties();
        properties.setLoginRequired(true);
        AuthInterceptor interceptor = new AuthInterceptor(properties, authService(properties), settingsRepository());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
    }

    @Test
    void shouldAllowLoginEndpointWithTrailingSlashWhenLoginIsEnabled() throws Exception {
        AuthProperties properties = new AuthProperties();
        properties.setLoginRequired(true);
        AuthInterceptor interceptor = new AuthInterceptor(properties, authService(properties), settingsRepository());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login/");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
    }

    @Test
    void shouldAllowAuthStatusEndpointWhenLoginIsEnabled() throws Exception {
        AuthProperties properties = new AuthProperties();
        properties.setLoginRequired(true);
        AuthInterceptor interceptor = new AuthInterceptor(properties, authService(properties), settingsRepository());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/status");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
    }

    @Test
    void shouldAllowAuthStatusEndpointWithTrailingSlashWhenLoginIsEnabled() throws Exception {
        AuthProperties properties = new AuthProperties();
        properties.setLoginRequired(true);
        AuthInterceptor interceptor = new AuthInterceptor(properties, authService(properties), settingsRepository());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/status/");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
    }

    private AuthService authService(AuthProperties properties) {
        return new AuthService(properties, settingsRepositoryWithTimeout(30));
    }

    private SettingsRepository settingsRepositoryWithTimeout(int sessionTimeout) {
        SettingsRepository settingsRepository = mock(SettingsRepository.class);
        when(settingsRepository.loadGeneralSettings()).thenReturn(GeneralSettingsVO.builder()
                .sessionTimeout(sessionTimeout)
                .build());
        return settingsRepository;
    }

    private SettingsRepository settingsRepository() {
        SettingsRepository settingsRepository = mock(SettingsRepository.class);
        when(settingsRepository.loadGeneralSettings()).thenReturn(GeneralSettingsVO.builder()
                .requireLogin(false)
                .build());
        return settingsRepository;
    }

    private SettingsRepository settingsRepositoryRequiringLogin() {
        SettingsRepository settingsRepository = mock(SettingsRepository.class);
        when(settingsRepository.loadGeneralSettings()).thenReturn(GeneralSettingsVO.builder()
                .requireLogin(true)
                .build());
        return settingsRepository;
    }

    @Test
    void shouldAllowReadOnlyGetForNonAdminUser() throws Exception {
        TestSession session = login(false);
        MockHttpServletRequest request = authenticatedRequest(
                "GET", "/api/clusters", session.token());

        boolean allowed = session.interceptor().preHandle(
                request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
    }

    @Test
    void shouldRejectMutatingPostForNonAdminUser() throws Exception {
        TestSession session = login(false);
        MockHttpServletRequest request = authenticatedRequest(
                "POST", "/api/ops/updateUseTLS", session.token());
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = session.interceptor().preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Admin permission required");
    }

    @Test
    void shouldAllowMutatingPostForAdminUser() throws Exception {
        TestSession session = login(true);
        MockHttpServletRequest request = authenticatedRequest(
                "POST", "/api/ops/updateUseTLS", session.token());

        boolean allowed = session.interceptor().preHandle(
                request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
    }

    @Test
    void shouldAllowReadOnlyPostForNonAdminUser() throws Exception {
        TestSession session = login(false);
        MockHttpServletRequest request = authenticatedRequest(
                "POST", "/api/metrics/query/", session.token());

        boolean allowed = session.interceptor().preHandle(
                request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
    }

    @Test
    void shouldRejectDataSourceTestForNonAdminUser() throws Exception {
        TestSession session = login(false);
        MockHttpServletRequest request = authenticatedRequest(
                "POST", "/api/settings/datasources/test", session.token());
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = session.interceptor().preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void shouldRejectNameServerConnectionTestForNonAdminUser() throws Exception {
        TestSession session = login(false);
        MockHttpServletRequest request = authenticatedRequest(
                "POST", "/api/clusters/test-connection", session.token());
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = session.interceptor().preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Admin permission required");
    }

    @Test
    void shouldAllowNameServerConnectionTestForAdminUser() throws Exception {
        TestSession session = login(true);
        MockHttpServletRequest request = authenticatedRequest(
                "POST", "/api/clusters/test-connection", session.token());

        boolean allowed = session.interceptor().preHandle(
                request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
    }

    @Test
    void shouldAllowLogoutForNonAdminUser() throws Exception {
        TestSession session = login(false);
        MockHttpServletRequest request = authenticatedRequest(
                "POST", "/api/auth/logout", session.token());

        boolean allowed = session.interceptor().preHandle(
                request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
    }

    @Test
    void shouldRejectCredentialsViewForNonAdminUser() throws Exception {
        TestSession session = login(false);
        MockHttpServletRequest request = authenticatedRequest(
                "GET", "/api/acl/users/user-1/credentials", session.token());
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = session.interceptor().preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Admin permission required");
    }

    @Test
    void shouldAllowCredentialsViewForAdminUser() throws Exception {
        TestSession session = login(true);
        MockHttpServletRequest request = authenticatedRequest(
                "GET", "/api/acl/users/user-1/credentials", session.token());

        boolean allowed = session.interceptor().preHandle(
                request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
    }

    private TestSession login(boolean admin) {
        AuthProperties properties = new AuthProperties();
        properties.setLoginRequired(true);
        AuthProperties.User user = new AuthProperties.User();
        user.setUsername("test-user");
        user.setPassword("secret");
        user.setAdmin(admin);
        properties.setUsers(List.of(user));
        AuthService authService = new AuthService(properties, settingsRepositoryWithTimeout(30));
        LoginDTO login = new LoginDTO();
        login.setUsername("test-user");
        login.setPassword("secret");
        String token = authService.login(login).getToken();
        return new TestSession(new AuthInterceptor(properties, authService, settingsRepository()), token);
    }

    private MockHttpServletRequest authenticatedRequest(String method, String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }

    private record TestSession(AuthInterceptor interceptor, String token) {
    }

}
