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
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.persistence.entity.RmqStudioSession;
import org.apache.rocketmq.studio.persistence.entity.RmqStudioUser;
import org.apache.rocketmq.studio.persistence.mapper.RmqStudioSessionMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqStudioUserMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@ActiveProfiles("dev")
class AuthServiceLoginConcurrencyIntegrationTest {

    private static final String OLD_PASSWORD = "original-password-1";
    private static final String NEW_PASSWORD = "replacement-password-2";

    @Autowired
    private AuthService authService;

    @Autowired
    private RmqStudioUserMapper userMapper;

    @Autowired
    private RmqStudioSessionMapper sessionMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoSpyBean
    private PasswordHasher passwordHasher;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void passwordChangeMustInvalidateAnInFlightOldPasswordLoginTest(boolean requireCurrentPassword) throws Exception {
        RmqStudioUser user = authService.createUser("login-reset-" + UUID.randomUUID(), OLD_PASSWORD, false);
        CountDownLatch passwordVerified = new CountDownLatch(1);
        CountDownLatch resumeLogin = new CountDownLatch(1);
        AtomicBoolean pauseOnce = new AtomicBoolean(true);
        doAnswer(invocation -> {
            boolean matches = (boolean) invocation.callRealMethod();
            if (matches && pauseOnce.compareAndSet(true, false)) {
                passwordVerified.countDown();
                assertThat(resumeLogin.await(20, TimeUnit.SECONDS)).isTrue();
            }
            return matches;
        }).when(passwordHasher).matches(eq(OLD_PASSWORD), anyString());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<LoginVO> login = executor.submit(() -> authService.login(loginRequest(user, OLD_PASSWORD)));
            assertThat(passwordVerified.await(10, TimeUnit.SECONDS)).isTrue();

            CompletableFuture<Integer> resetSessionId = new CompletableFuture<>();
            Future<?> reset = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                resetSessionId.complete(jdbcTemplate.queryForObject("SELECT SESSION_ID()", Integer.class));
                authService.changePassword(user.getId(), OLD_PASSWORD, NEW_PASSWORD, requireCurrentPassword);
            }));
            awaitResetCompletionOrRowLock(reset, resetSessionId.get(10, TimeUnit.SECONDS));
            resumeLogin.countDown();

            LoginVO issued = login.get(10, TimeUnit.SECONDS);
            reset.get(10, TimeUnit.SECONDS);

            assertThat(authService.isAuthenticated("Bearer " + issued.getToken()))
                    .as("a completed password change must invalidate the overlapping old-password login")
                    .isFalse();
            assertThatThrownBy(() -> authService.login(loginRequest(user, OLD_PASSWORD)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("Invalid username or password");
            LoginVO replacement = authService.login(loginRequest(user, NEW_PASSWORD));
            assertThat(authService.isAuthenticated("Bearer " + replacement.getToken())).isTrue();
        } finally {
            resumeLogin.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            sessionMapper.delete(new QueryWrapper<RmqStudioSession>().eq("user_id", user.getId()));
            userMapper.deleteById(user.getId());
        }
    }

    private void awaitResetCompletionOrRowLock(Future<?> reset, int sessionId) throws InterruptedException {
        // With the bug, reset completes before login inserts its session. With the fix, reset
        // waits for login's user-row lock. Observe either state before releasing login, so a
        // slow reset cannot accidentally hide the race. H2 is used only to coordinate the test;
        // the assertion above checks the real persisted bearer session after both calls finish.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!reset.isDone()) {
            Integer blockers = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE SESSION_ID = ? AND BLOCKER_ID IS NOT NULL",
                    Integer.class, sessionId);
            if (blockers != null && blockers > 0) {
                return;
            }
            assertThat(System.nanoTime()).as("reset must complete or wait for the login row lock").isLessThan(deadline);
            TimeUnit.MILLISECONDS.sleep(10);
        }
    }

    private LoginDTO loginRequest(RmqStudioUser user, String password) {
        LoginDTO request = new LoginDTO();
        request.setUsername(user.getUsername());
        request.setPassword(password);
        return request;
    }
}
