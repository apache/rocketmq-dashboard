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

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.rocketmq.studio.persistence.entity.RmqStudioUser;
import org.apache.rocketmq.studio.persistence.mapper.RmqStudioUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The status {@code POST /api/auth/password} answers when the caller's own current password does not
 * match. The request is already authenticated at that point, so the failure is about the payload, and
 * the Studio client keys its session handling on the status: any 401 outside {@code /auth/login} and
 * {@code /auth/status} makes it clear the stored session and redirect to the login page
 * ({@code web/src/api/client.ts}), which logs an operator out for mistyping this one field. The same
 * field already answers 400 when it is blank ({@code ChangePasswordDTO} validation), so the two
 * payload problems must agree.
 *
 * <p>A real call is needed rather than a controller slice: the status is produced by
 * {@code GlobalExceptionHandler}, which maps the {@code BusinessException} code onto the HTTP status.
 */
@SpringBootTest(properties = "studio.auth.login-required=true")
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AuthPasswordChangeStatusIntegrationTest {

    private static final String USERNAME = "password-change-status-it";
    private static final String CURRENT_PASSWORD = "current-password-1";
    private static final String NEW_PASSWORD = "new-password-2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private RmqStudioUserMapper userMapper;

    private String authorization;

    @BeforeEach
    void seedUserAndLogIn() {
        userMapper.delete(new QueryWrapper<RmqStudioUser>().eq("username", USERNAME));
        authService.createUser(USERNAME, CURRENT_PASSWORD, false);
        LoginDTO request = new LoginDTO();
        request.setUsername(USERNAME);
        request.setPassword(CURRENT_PASSWORD);
        authorization = "Bearer " + authService.login(request).getToken();
    }

    @AfterEach
    void deleteSeededUser() {
        userMapper.delete(new QueryWrapper<RmqStudioUser>().eq("username", USERNAME));
    }

    @Test
    void aWrongCurrentPasswordIsAPayloadErrorAndLeavesTheSessionUsableTest() throws Exception {
        mockMvc.perform(post("/api/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"not-the-current-password\","
                                + "\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));

        mockMvc.perform(get("/api/auth/status").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authenticated").value(true));
    }

    @Test
    void aBlankCurrentPasswordIsRejectedAsAPayloadErrorTest() throws Exception {
        mockMvc.perform(post("/api/auth/password")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}