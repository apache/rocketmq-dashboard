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

import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.model.LoginInfo;
import org.apache.rocketmq.dashboard.model.LoginResult;
import org.apache.rocketmq.dashboard.model.User;
import org.apache.rocketmq.dashboard.model.UserInfo;
import org.apache.rocketmq.dashboard.service.UserService;
import org.apache.rocketmq.dashboard.support.JsonResult;
import org.apache.rocketmq.dashboard.util.WebUtil;
import org.casbin.casdoor.entity.CasdoorUser;
import org.casbin.casdoor.service.CasdoorAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/login")
public class LoginController {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Resource
    private RMQConfigure configure;

    @Autowired
    private UserService userService;

    @Autowired
    private CasdoorAuthService casdoorAuthService;

    @Value("${server.servlet.context-path:/}")
    private String contextPath;

    @RequestMapping(value = "/check.query", method = RequestMethod.GET)
    @ResponseBody
    public Object check(HttpServletRequest request) {
        LoginInfo loginInfo = new LoginInfo();

        loginInfo.setLogined(WebUtil.getValueFromSession(request, WebUtil.USER_NAME) != null);
        loginInfo.setLoginRequired(configure.isLoginRequired());

        return loginInfo;
    }

    @RequestMapping(value = "/login.do", method = RequestMethod.POST)
    @ResponseBody
    public Object login(@RequestParam("username") String username,
        @RequestParam(value = "password") String password,
        HttpServletRequest request,
        HttpServletResponse response) throws Exception {
        logger.info("user:{} login", username);
        User user = userService.queryByUsernameAndPassword(username, password);

        if (user == null) {
            throw new IllegalArgumentException("Bad username or password!");
        } else {
            user.setPassword(null);
            UserInfo userInfo = WebUtil.setLoginInfo(request, response, user);
            WebUtil.setSessionValue(request, WebUtil.USER_INFO, userInfo);
            WebUtil.setSessionValue(request, WebUtil.USER_NAME, username);
            userInfo.setSessionId(WebUtil.getSessionId(request));
            LoginResult result = new LoginResult(username, user.getType(), contextPath);
            return result;
        }
    }

    @RequestMapping(value = "/casdoor-login-url", method = RequestMethod.POST)
    @ResponseBody
    public JsonResult<String> getCasdoorUrl(HttpServletRequest request,
                                            HttpServletResponse response) {
        String origin = request.getParameter("origin");
        return new JsonResult<>(casdoorAuthService.getSigninUrl(origin));
    }

    @RequestMapping(value = "/casdoor-login", method = RequestMethod.POST)
    @ResponseBody
    public Object CasdoorLogin(HttpServletRequest request,
                                            HttpServletResponse response) {
        String code = request.getParameter("code");
        String state = request.getParameter("state");
        String token = casdoorAuthService.getOAuthToken(code, state);
        CasdoorUser casdoorUser = casdoorAuthService.parseJwtToken(token);
        int type = 0;
        if (casdoorUser.isAdmin()) {
            type = 1;
        }
        User user = new User(casdoorUser.getName(),null,type);
        UserInfo userInfo = WebUtil.setLoginInfo(request, response, user);
        WebUtil.setSessionValue(request, WebUtil.USER_INFO, userInfo);
        WebUtil.setSessionValue(request, WebUtil.USER_NAME, user.getName());
        userInfo.setSessionId(WebUtil.getSessionId(request));
        LoginResult result = new LoginResult(user.getName(), user.getType(), contextPath);
        return result;
    }

    @RequestMapping(value = "/logout.do", method = RequestMethod.POST)
    @ResponseBody
    public JsonResult<String> logout(HttpServletRequest request) {
        WebUtil.removeSession(request);
        return new JsonResult<>(contextPath);
    }
}
