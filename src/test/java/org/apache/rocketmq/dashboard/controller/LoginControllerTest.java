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
package org.apache.rocketmq.dashboard.controller;

import org.apache.rocketmq.dashboard.model.User;
import org.apache.rocketmq.dashboard.service.impl.UserServiceImpl;
import org.apache.rocketmq.dashboard.service.strategy.UserContext;
import org.apache.rocketmq.dashboard.service.strategy.UserStrategy;
import org.apache.rocketmq.dashboard.support.GlobalExceptionHandler;
import org.apache.rocketmq.dashboard.util.WebUtil;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class LoginControllerTest extends BaseControllerTest {

    @InjectMocks
    private LoginController loginController;

    @Mock
    private UserServiceImpl userService;

    @Spy
    private UserContext userContext;

    @Spy
    private UserStrategy userStrategy;

    private String contextPath = "/rocketmq-console";

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        super.mockRmqConfigure();
        when(configure.isLoginRequired()).thenReturn(true);
        when(configure.getRocketMqDashboardDataPath()).thenReturn("");
        Field contextPathField = ReflectionUtils.findField(LoginController.class, "contextPath");
        ReflectionUtils.makeAccessible(contextPathField);
        ReflectionUtils.setField(contextPathField, loginController, contextPath);
        mockMvc = MockMvcBuilders.standaloneSetup(loginController).setControllerAdvice(GlobalExceptionHandler.class).build();
    }

    @Test
    public void testCheck() throws Exception {
        final String url = "/login/check.query";
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.sessionAttr(WebUtil.USER_NAME, "admin");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.logined").value(true))
                .andExpect(jsonPath("$.loginRequired").value(true));

    }


    @Test
    public void testLogin() throws Exception {
        final String url = "/login/login.do";
        final String username = "admin";
        final String rightPwd = "admin";
        final String wrongPwd = "rocketmq";

        // 模拟 userService.queryByName 方法返回一个用户
        User user = new User("admin", "admin", 1);
        user.setPassword(rightPwd);


        // 1、login fail
        perform = mockMvc.perform(post(url)
                .param("username", username)
                .param("password", wrongPwd));
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.status").value(-1))
                .andExpect(jsonPath("$.errMsg").value("Bad username or password!"));

        when(userService.queryByUsernameAndPassword(username, rightPwd)).thenReturn(user);

        // 2、login success
        perform = mockMvc.perform(post(url)
                .param("username", username)
                .param("password", rightPwd));
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.contextPath").value(contextPath));
    }


    @Test
    public void testLogout() throws Exception {
        final String url = "/login/logout.do";
        requestBuilder = post(url);
        requestBuilder.sessionAttr(WebUtil.USER_NAME, "root");
        perform = mockMvc.perform(requestBuilder);
        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(contextPath));
    }

    @Override
    protected Object getTestController() {
        return loginController;
    }
}
