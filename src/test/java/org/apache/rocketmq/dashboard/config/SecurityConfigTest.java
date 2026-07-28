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
package org.apache.rocketmq.dashboard.config;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

@RunWith(MockitoJUnitRunner.Silent.class)
public class SecurityConfigTest {

    private SecurityConfig securityConfig;

    @Before
    public void setUp() {
        securityConfig = new SecurityConfig();
        ReflectionTestUtils.setField(securityConfig, "allowedOriginPatterns",
            new String[] {"http://localhost:*", "https://example.com"});
    }

    @Test
    public void testCorsConfigurationSource() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        assertNotNull(source);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/topic/list.query");
        CorsConfiguration configuration = source.getCorsConfiguration(request);
        assertNotNull(configuration);
        assertEquals(2, configuration.getAllowedOriginPatterns().size());
        assertTrue(configuration.getAllowedOriginPatterns().contains("http://localhost:*"));
        assertTrue(configuration.getAllowedMethods().contains("GET"));
        assertTrue(configuration.getAllowedMethods().contains("DELETE"));
        assertTrue(configuration.getAllowedHeaders().contains("Authorization"));
        assertTrue(configuration.getAllowedHeaders().contains("X-XSRF-TOKEN"));
        assertEquals(Boolean.TRUE, configuration.getAllowCredentials());
        assertEquals(Long.valueOf(3600L), configuration.getMaxAge());
    }

    @Test
    public void testCsrfTokenRepository() {
        CsrfTokenRepository repository = securityConfig.csrfTokenRepository();
        assertNotNull(repository);
        assertTrue(repository instanceof CookieCsrfTokenRepository);
    }

    @Test
    public void testSecurityFilterChainConfiguresHttpSecurity() throws Exception {
        HttpSecurity http = mock(HttpSecurity.class,
            withSettings().defaultAnswer(org.mockito.Mockito.RETURNS_SELF));
        SecurityFilterChain expectedChain = mock(DefaultSecurityFilterChain.class);
        doReturn(expectedChain).when(http).build();

        SecurityFilterChain chain = securityConfig.securityFilterChain(http);
        assertSame(expectedChain, chain);
        verify(http).cors(any());
        verify(http).csrf(any());
        verify(http).authorizeHttpRequests(any());
        verify(http).httpBasic(any());
        verify(http).build();
    }
}
