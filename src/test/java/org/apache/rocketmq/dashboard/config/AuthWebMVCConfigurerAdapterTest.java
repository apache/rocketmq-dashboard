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

import org.apache.rocketmq.dashboard.BaseTest;
import org.apache.rocketmq.dashboard.interceptor.AuthInterceptor;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;

import java.util.List;

public class AuthWebMVCConfigurerAdapterTest extends BaseTest {

    @InjectMocks
    private AuthWebMVCConfigurerAdapter authWebMVCConfigurerAdapter;

    @Mock
    private RMQConfigure configure;

    @Mock
    private AuthInterceptor authInterceptor;

    @Before
    public void init() throws Exception {
        MockitoAnnotations.initMocks(this);
    }


    @Test
    public void addInterceptors() {
        Mockito.when(configure.isLoginRequired()).thenReturn(true);
        InterceptorRegistry registry = new InterceptorRegistry();
        Assertions.assertDoesNotThrow(() -> authWebMVCConfigurerAdapter.addInterceptors(registry));
    }

    @Test
    public void addArgumentResolvers() {
        List<HandlerMethodArgumentResolver> argumentResolvers = Lists.newArrayList();
        authWebMVCConfigurerAdapter.addArgumentResolvers(argumentResolvers);
        Assertions.assertEquals(1, argumentResolvers.size());
    }

    @Test
    public void addViewControllers() {
        ViewControllerRegistry registry = new ViewControllerRegistry(new ClassPathXmlApplicationContext());
        Assertions.assertDoesNotThrow(() -> authWebMVCConfigurerAdapter.addViewControllers(registry));
    }
}