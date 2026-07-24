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

package com.rocketmq.studio.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AuthInterceptorTest {

    @Test
    void shouldAllowRequestsWhenLoginIsDisabled() throws Exception {
        AuthProperties properties = new AuthProperties();
        AuthInterceptor interceptor = new AuthInterceptor(properties, new AuthService(properties));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clusters");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
    }

    @Test
    void shouldRejectProtectedApiWithoutTokenWhenLoginIsEnabled() throws Exception {
        AuthProperties properties = new AuthProperties();
        properties.setLoginRequired(true);
        AuthInterceptor interceptor = new AuthInterceptor(properties, new AuthService(properties));
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
        AuthService authService = new AuthService(properties);
        AuthInterceptor interceptor = new AuthInterceptor(properties, authService);
        LoginDTO login = new LoginDTO();
        login.setUsername("admin");
        login.setPassword("secret");
        String token = authService.login(login).getToken();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clusters");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
    }

    @Test
    void shouldAllowLoginEndpointWhenLoginIsEnabled() throws Exception {
        AuthProperties properties = new AuthProperties();
        properties.setLoginRequired(true);
        AuthInterceptor interceptor = new AuthInterceptor(properties, new AuthService(properties));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
    }
}
