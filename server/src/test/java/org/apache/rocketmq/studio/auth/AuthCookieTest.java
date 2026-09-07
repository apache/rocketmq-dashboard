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
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AuthCookieTest {

    @Test
    void usesHttpOnlySecureCookie() {
        AuthProperties properties = new AuthProperties();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AuthCookie.write(response, properties, "cookie-token", Duration.ofMinutes(30));

        assertThat(response.getHeader("Set-Cookie"))
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict");
    }

    @Test
    void prefersExplicitBearerTokenOverStaleCookie() {
        AuthProperties properties = new AuthProperties();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("rmq_studio_session", "stale-cookie-token"));
        request.addHeader("Authorization", "bearer current-header-token");

        assertThat(AuthCookie.authorization(request, properties))
                .isEqualTo("bearer current-header-token");
    }

    @Test
    void fallsBackToCookieForEmptyOrNonBearerAuthorization() {
        AuthProperties properties = new AuthProperties();
        MockHttpServletRequest emptyHeaderRequest = new MockHttpServletRequest();
        emptyHeaderRequest.setCookies(new Cookie("rmq_studio_session", "cookie-token"));
        emptyHeaderRequest.addHeader("Authorization", "Bearer   ");
        MockHttpServletRequest nonBearerRequest = new MockHttpServletRequest();
        nonBearerRequest.setCookies(new Cookie("rmq_studio_session", "cookie-token"));
        nonBearerRequest.addHeader("Authorization", "Basic credentials");

        assertThat(AuthCookie.authorization(emptyHeaderRequest, properties))
                .isEqualTo("Bearer cookie-token");
        assertThat(AuthCookie.authorization(nonBearerRequest, properties))
                .isEqualTo("Bearer cookie-token");
    }
}
