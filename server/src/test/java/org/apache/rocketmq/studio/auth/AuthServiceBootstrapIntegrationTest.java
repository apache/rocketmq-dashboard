/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.rocketmq.studio.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.rocketmq.studio.persistence.entity.RmqStudioUser;
import org.apache.rocketmq.studio.persistence.mapper.RmqStudioUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "studio.auth.login-required=true",
    "studio.auth.users[0].username=bootstrap-admin",
    "studio.auth.users[0].password=password-123",
    "studio.auth.users[0].admin=true"
})
class AuthServiceBootstrapIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private RmqStudioUserMapper userMapper;

    @Test
    void firstLoginSeedsConfiguredUserWithNumericIdTest() {
        LoginDTO request = new LoginDTO();
        request.setUsername("bootstrap-admin");
        request.setPassword("password-123");

        LoginVO login = authService.login(request);

        assertThat(login.getUser().getUsername()).isEqualTo("bootstrap-admin");
        assertThat(login.getUser().isAdmin()).isTrue();
        assertThat(login.getUser().getUserId()).isNotNull();

        RmqStudioUser persisted = userMapper.selectOne(new QueryWrapper<RmqStudioUser>()
                .eq("username", "bootstrap-admin"));
        assertThat(persisted).isNotNull();
        assertThat(persisted.getId()).isEqualTo(login.getUser().getUserId());

        // The persisted session authenticates subsequent requests via its token hash.
        assertThat(authService.isAuthenticated("Bearer " + login.getToken())).isTrue();
    }
}
