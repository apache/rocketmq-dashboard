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
package org.apache.rocketmq.dashboard.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.rocketmq.dashboard.service.LoginService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class AuthInterceptorTest {

    @InjectMocks
    private AuthInterceptor authInterceptor;

    @Mock
    private LoginService loginService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Test
    public void testPreHandleOptionsRequest() throws Exception {
        when(request.getMethod()).thenReturn("OPTIONS");
        Assert.assertTrue(authInterceptor.preHandle(request, response, new Object()));
        verify(loginService, never()).login(request, response);
    }

    @Test
    public void testPreHandleCsrfTokenRequest() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURL())
                .thenReturn(new StringBuffer("http://localhost:8080/rocketmq-dashboard/csrf-token"));
        Assert.assertTrue(authInterceptor.preHandle(request, response, new Object()));
        verify(loginService, never()).login(request, response);
    }

    @Test
    public void testPreHandleDelegatesToLoginService() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURL())
                .thenReturn(new StringBuffer("http://localhost:8080/topic/list.query"));
        when(loginService.login(request, response)).thenReturn(true);
        Assert.assertTrue(authInterceptor.preHandle(request, response, new Object()));

        when(loginService.login(request, response)).thenReturn(false);
        Assert.assertFalse(authInterceptor.preHandle(request, response, new Object()));
    }
}
