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
    void usesHttpOnlySecureCookieAndPrefersItOverAuthorizationHeader() {
        AuthProperties properties = new AuthProperties();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("rmq_studio_session", "cookie-token"));
        request.addHeader("Authorization", "Bearer header-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AuthCookie.write(response, properties, "cookie-token", Duration.ofMinutes(30));

        assertThat(AuthCookie.authorization(request, properties)).isEqualTo("Bearer cookie-token");
        assertThat(response.getHeader("Set-Cookie"))
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict");
    }
}
