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

import java.util.ArrayList;
import java.util.List;
import org.apache.rocketmq.dashboard.interceptor.AuthInterceptor;
import org.apache.rocketmq.dashboard.model.UserInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class AuthWebMVCConfigurerAdapterTest {

    private AuthWebMVCConfigurerAdapter adapter;

    @Mock
    private AuthInterceptor authInterceptor;

    @Mock
    private RMQConfigure configure;

    @Before
    public void setUp() {
        adapter = new AuthWebMVCConfigurerAdapter();
        ReflectionTestUtils.setField(adapter, "authInterceptor", authInterceptor);
        ReflectionTestUtils.setField(adapter, "configure", configure);
    }

    @Test
    public void testAddInterceptorsWhenLoginRequired() {
        when(configure.isLoginRequired()).thenReturn(true);
        InterceptorRegistry registry = mock(InterceptorRegistry.class);
        when(registry.addInterceptor(authInterceptor))
            .thenReturn(new InterceptorRegistration(authInterceptor));

        adapter.addInterceptors(registry);

        verify(registry).addInterceptor(authInterceptor);
    }

    @Test
    public void testAddInterceptorsWhenLoginNotRequired() {
        when(configure.isLoginRequired()).thenReturn(false);
        InterceptorRegistry registry = mock(InterceptorRegistry.class);

        adapter.addInterceptors(registry);

        verifyNoInteractions(registry);
    }

    @Test
    public void testAddArgumentResolversSupportsParameter() {
        List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();
        adapter.addArgumentResolvers(resolvers);
        assertEquals(1, resolvers.size());

        HandlerMethodArgumentResolver resolver = resolvers.get(0);

        MethodParameter userInfoParameter = mock(MethodParameter.class);
        doReturn(UserInfo.class).when(userInfoParameter).getParameterType();
        assertTrue(resolver.supportsParameter(userInfoParameter));

        MethodParameter stringParameter = mock(MethodParameter.class);
        doReturn(String.class).when(stringParameter).getParameterType();
        assertFalse(resolver.supportsParameter(stringParameter));
    }

    @Test
    public void testResolveArgumentReturnsUserInfoFromSession() throws Exception {
        List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();
        adapter.addArgumentResolvers(resolvers);
        HandlerMethodArgumentResolver resolver = resolvers.get(0);

        UserInfo userInfo = new UserInfo();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute(UserInfo.USER_INFO, userInfo);

        Object resolved = resolver.resolveArgument(null, null, new ServletWebRequest(request), null);
        assertSame(userInfo, resolved);
    }

    @Test
    public void testResolveArgumentWithoutSessionThrows() {
        List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();
        adapter.addArgumentResolvers(resolvers);
        HandlerMethodArgumentResolver resolver = resolvers.get(0);

        MockHttpServletRequest request = new MockHttpServletRequest();
        try {
            resolver.resolveArgument(null, null, new ServletWebRequest(request), null);
            fail("Expected MissingServletRequestPartException");
        } catch (Exception e) {
            assertTrue(e instanceof MissingServletRequestPartException);
        }
    }

    @Test
    public void testAddViewControllers() {
        ViewControllerRegistry registry = mock(ViewControllerRegistry.class);
        when(registry.addViewController("*.htm"))
            .thenReturn(new ViewControllerRegistration("*.htm"));

        adapter.addViewControllers(registry);

        verify(registry).addViewController("*.htm");
    }
}
