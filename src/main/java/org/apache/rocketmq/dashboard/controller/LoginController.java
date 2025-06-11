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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.model.LoginInfo;
import org.apache.rocketmq.dashboard.model.LoginResult;
import org.apache.rocketmq.dashboard.model.User;
import org.apache.rocketmq.dashboard.model.UserInfo;
import org.apache.rocketmq.dashboard.service.UserService;
import org.apache.rocketmq.dashboard.support.JsonResult;
import org.apache.rocketmq.dashboard.util.WebUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Objects;

@RestController
@RequestMapping("/login")
@RequiredArgsConstructor
@Slf4j
public class LoginController {

    private final RMQConfigure configure;
    private final UserService userService;

    @Value("${server.servlet.context-path:/}")
    private String contextPath;

    @GetMapping(value = "/check.query")
    public ResponseEntity<Object> check(HttpServletRequest request) {
        LoginInfo loginInfo = new LoginInfo();
        loginInfo.setLogined(WebUtil.getValueFromSession(request, WebUtil.USER_NAME) != null);
        loginInfo.setLoginRequired(configure.isLoginRequired());

        return ResponseEntity.ok(loginInfo);
    }

    @PostMapping(value = "/login.do")
    public ResponseEntity<Object> login(@RequestParam("username") String username,
                        @RequestParam(value = "password") String password,
                        HttpServletRequest request,
                        HttpServletResponse response) throws Exception {
        log.info("user:{} login", username);
        User user = userService.queryByUsernameAndPassword(username, password);

        if (Objects.isNull(user)) {
            throw new IllegalArgumentException("Bad username or password!");
        } else {
            user.setPassword(null);
            UserInfo userInfo = WebUtil.setLoginInfo(request, response, user);
            WebUtil.setSessionValue(request, WebUtil.USER_INFO, userInfo);
            WebUtil.setSessionValue(request, WebUtil.USER_NAME, username);
            userInfo.setSessionId(WebUtil.getSessionId(request));
            return ResponseEntity.ok(new LoginResult(username, user.getType(), contextPath));
        }
    }

    @PostMapping(value = "/logout.do")
    public JsonResult<String> logout(HttpServletRequest request) {
        WebUtil.removeSession(request);
        return new JsonResult<>(contextPath);
    }
}
