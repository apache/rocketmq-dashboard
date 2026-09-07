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
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.persistence.entity.RmqStudioUser;
import org.apache.rocketmq.studio.persistence.mapper.RmqStudioUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "studio.auth.login-required=true")
class AuthServiceConcurrencyIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private RmqStudioUserMapper userMapper;

    @Autowired
    private PasswordHasher passwordHasher;

    @Test
    void concurrentDisablesKeepAtLeastOneEnabledAdministratorTest() throws Exception {
        // The in-memory dev database is shared across test classes in one JVM, so park any
        // pre-existing enabled administrators and restore them afterwards to keep the race
        // deterministic.
        List<RmqStudioUser> parked = userMapper.selectList(new QueryWrapper<RmqStudioUser>()
                .eq("admin", true)
                .eq("enabled", true));
        RmqStudioUser first = adminUser("race-admin-one");
        RmqStudioUser second = adminUser("race-admin-two");
        userMapper.insert(first);
        userMapper.insert(second);
        for (RmqStudioUser parkedUser : parked) {
            userMapper.update(null, new UpdateWrapper<RmqStudioUser>()
                    .eq("id", parkedUser.getId())
                    .set("enabled", false));
        }
        try {
            int[] outcomes = new int[2];
            Exception[] threadErrors = new Exception[2];
            CyclicBarrier barrier = new CyclicBarrier(2);
            Thread firstThread = disableThread(first.getId(), barrier, outcomes, threadErrors, 0);
            Thread secondThread = disableThread(second.getId(), barrier, outcomes, threadErrors, 1);
            firstThread.start();
            secondThread.start();
            firstThread.join(TimeUnit.SECONDS.toMillis(30));
            secondThread.join(TimeUnit.SECONDS.toMillis(30));

            assertThat(firstThread.isAlive()).isFalse();
            assertThat(secondThread.isAlive()).isFalse();
            assertThat(threadErrors[0]).isNull();
            assertThat(threadErrors[1]).isNull();
            // Exactly one disable may win the race; the other must fail with a conflict.
            assertThat(outcomes[0] + outcomes[1]).isEqualTo(1);
            long enabledAdmins = userMapper.selectCount(new QueryWrapper<RmqStudioUser>()
                    .eq("admin", true)
                    .eq("enabled", true));
            assertThat(enabledAdmins).isEqualTo(1);
        } finally {
            userMapper.deleteById(first.getId());
            userMapper.deleteById(second.getId());
            for (RmqStudioUser parkedUser : parked) {
                userMapper.update(null, new UpdateWrapper<RmqStudioUser>()
                        .eq("id", parkedUser.getId())
                        .set("enabled", true));
            }
        }
    }

    private Thread disableThread(Long userId, CyclicBarrier barrier, int[] outcomes,
                                 Exception[] threadErrors, int slot) {
        return new Thread(() -> {
            try {
                barrier.await();
                try {
                    authService.setUserEnabled(userId, false);
                    outcomes[slot] = 1;
                } catch (BusinessException exception) {
                    if (exception.getCode() != 409) {
                        throw exception;
                    }
                }
            } catch (Exception exception) {
                threadErrors[slot] = exception;
            }
        }, "auth-disable-" + userId);
    }

    private RmqStudioUser adminUser(String username) {
        RmqStudioUser user = new RmqStudioUser();
        user.setUsername(username);
        user.setPasswordHash(passwordHasher.hash("password-1"));
        user.setAdmin(true);
        user.setEnabled(true);
        user.setPasswordChangedAt(LocalDateTime.now());
        return user;
    }
}
