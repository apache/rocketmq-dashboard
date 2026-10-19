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

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.persistence.entity.RmqStudioUser;
import org.apache.rocketmq.studio.persistence.mapper.RmqStudioSessionMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqStudioUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Changing a password is two writes: the new hash, then the revocation of every session the account
 * still has. This class pins the property that the two writes are one unit, by letting the second
 * one fail against a real database.
 *
 * <p>The {@code dev} profile is deliberate: it is H2 in MySQL compatibility mode fed by
 * {@code classpath:db/schema.sql}, so the row really is written and the rollback really is a
 * database rollback. A mock-only test cannot observe this at all - without a Spring-demarcated
 * transaction there is no rollback to assert on, and with a mocked session mapper the account row
 * would still be written, but the test could not read it back.
 */
@SpringBootTest
@ActiveProfiles("dev")
class AuthServicePasswordChangeIntegrationTest {

    private static final String USERNAME = "password-change-atomicity-it";
    private static final String ORIGINAL_PASSWORD = "original-password-1";
    private static final String REPLACEMENT_PASSWORD = "replacement-password-2";

    @MockitoBean
    private RmqStudioSessionMapper sessionMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private RmqStudioUserMapper userMapper;

    @Autowired
    private PasswordHasher passwordHasher;

    @BeforeEach
    void removeAnyUserLeftByAnEarlierRun() {
        userMapper.delete(new QueryWrapper<RmqStudioUser>().eq("username", USERNAME));
    }

    @AfterEach
    void deleteSeededUser() {
        userMapper.delete(new QueryWrapper<RmqStudioUser>().eq("username", USERNAME));
    }

    @Test
    void aFailedSessionRevokeMustNotReplaceTheStoredPasswordTest() {
        RmqStudioUser user = authService.createUser(USERNAME, ORIGINAL_PASSWORD, false);
        doThrow(new IllegalStateException("session store unavailable"))
                .when(sessionMapper).update(isNull(), any(Wrapper.class));

        assertThatThrownBy(() -> authService.changePassword(
                user.getId(), ORIGINAL_PASSWORD, REPLACEMENT_PASSWORD, true))
                .isInstanceOf(IllegalStateException.class);

        verify(sessionMapper).update(isNull(), any(Wrapper.class));
        RmqStudioUser reloaded = userMapper.selectById(user.getId());
        assertThat(passwordHasher.matches(REPLACEMENT_PASSWORD, reloaded.getPasswordHash()))
                .as("a password change whose session revoke failed must not be half applied")
                .isFalse();
        assertThat(passwordHasher.matches(ORIGINAL_PASSWORD, reloaded.getPasswordHash())).isTrue();
    }

    @Test
    void aSuccessfulPasswordChangeStillReplacesTheHashAndRevokesSessionsTest() {
        RmqStudioUser user = authService.createUser(USERNAME, ORIGINAL_PASSWORD, false);

        authService.changePassword(user.getId(), ORIGINAL_PASSWORD, REPLACEMENT_PASSWORD, true);

        verify(sessionMapper).update(isNull(), any(Wrapper.class));
        RmqStudioUser reloaded = userMapper.selectById(user.getId());
        assertThat(passwordHasher.matches(REPLACEMENT_PASSWORD, reloaded.getPasswordHash())).isTrue();
        assertThat(passwordHasher.matches(ORIGINAL_PASSWORD, reloaded.getPasswordHash())).isFalse();
    }

    /**
     * Pins the row, not the response: the status shape of this rejection belongs to
     * {@code AuthPasswordChangeStatusIntegrationTest}, whereas the property this class is about is
     * that a change rejected before any write leaves the stored hash exactly as it was.
     */
    @Test
    void aWrongCurrentPasswordStillRejectsTheChangeAndKeepsTheStoredHashTest() {
        RmqStudioUser user = authService.createUser(USERNAME, ORIGINAL_PASSWORD, false);

        assertThatThrownBy(() -> authService.changePassword(
                user.getId(), "not-the-current-password", REPLACEMENT_PASSWORD, true))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Current password is incorrect");

        RmqStudioUser reloaded = userMapper.selectById(user.getId());
        assertThat(passwordHasher.matches(ORIGINAL_PASSWORD, reloaded.getPasswordHash())).isTrue();
    }
}
