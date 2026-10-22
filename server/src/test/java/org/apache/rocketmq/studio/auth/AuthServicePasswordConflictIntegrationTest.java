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
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.persistence.entity.RmqStudioSession;
import org.apache.rocketmq.studio.persistence.entity.RmqStudioUser;
import org.apache.rocketmq.studio.persistence.mapper.RmqStudioSessionMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqStudioUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@ActiveProfiles("dev")
class AuthServicePasswordConflictIntegrationTest {
    private static final String ORIGINAL = "original-password-1";
    private static final String STALE = "stale-password-2";
    private static final String RESET = "administrator-reset-3";

    @Autowired
    private AuthService authService;

    @Autowired
    private RmqStudioUserMapper userMapper;

    @Autowired
    private RmqStudioSessionMapper sessionMapper;

    @MockitoSpyBean
    private PasswordHasher passwordHasher;

    private RmqStudioUser user;

    @AfterEach
    void removeUserTest() {
        if (user != null) {
            sessionMapper.delete(new QueryWrapper<RmqStudioSession>().eq("user_id", user.getId()));
            userMapper.deleteById(user.getId());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void stalePasswordMutationPreservesCompletedResetAndItsSessionTest(boolean selfService) throws Exception {
        String username = "password-conflict-" + UUID.randomUUID();
        user = authService.createUser(username, ORIGINAL, false);
        CountDownLatch observedOldHash = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        // Hashing the replacement happens after reading/validating the old hash but before UPDATE.
        doAnswer(invocation -> {
            Object hash = invocation.callRealMethod();
            observedOldHash.countDown();
            if (!resume.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the competing reset");
            }
            return hash;
        }).when(passwordHasher).hash(STALE);

        var executor = Executors.newSingleThreadExecutor();
        var pending = executor.submit(() -> authService.changePassword(
                user.getId(), selfService ? ORIGINAL : null, STALE, selfService));
        try {
            assertThat(observedOldHash.await(20, TimeUnit.SECONDS)).isTrue();
            authService.changePassword(user.getId(), null, RESET, false);
            LoginDTO login = new LoginDTO();
            login.setUsername(username);
            login.setPassword(RESET);
            String authorization = "Bearer " + authService.login(login).getToken();
            resume.countDown();

            assertThatThrownBy(() -> pending.get(20, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause().isInstanceOf(BusinessException.class)
                    .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(409));
            String stored = userMapper.selectById(user.getId()).getPasswordHash();
            assertThat(passwordHasher.matches(RESET, stored)).isTrue();
            assertThat(passwordHasher.matches(STALE, stored)).isFalse();
            assertThat(authService.isAuthenticated(authorization)).isTrue();
        } finally {
            resume.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(25, TimeUnit.SECONDS)).isTrue();
        }
    }
}
