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
import org.apache.rocketmq.studio.persistence.entity.RmqStudioUser;
import org.apache.rocketmq.studio.persistence.mapper.RmqStudioSessionMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqStudioUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:auth-admin-concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "studio.auth.login-required=true"
})
class AuthServiceConcurrencyIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private RmqStudioUserMapper userMapper;

    @Autowired
    private RmqStudioSessionMapper sessionMapper;

    private Long firstAdminId;
    private Long secondAdminId;

    @BeforeEach
    void setUp() {
        clearDatabase();
        firstAdminId = insertAdministrator("concurrent-admin-a");
        secondAdminId = insertAdministrator("concurrent-admin-b");
    }

    @AfterEach
    void tearDown() {
        clearDatabase();
    }

    @RepeatedTest(5)
    void concurrentDisableRequestsPreserveOneEnabledAdministrator() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> first = executor.submit(() -> disableAfterStart(firstAdminId, ready, start));
            Future<Throwable> second = executor.submit(() -> disableAfterStart(secondAdminId, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            Throwable firstResult = first.get(5, TimeUnit.SECONDS);
            Throwable secondResult = second.get(5, TimeUnit.SECONDS);

            assertThat(new Throwable[] {firstResult, secondResult})
                    .filteredOn(result -> result == null)
                    .hasSize(1);
            assertThat(new Throwable[] {firstResult, secondResult})
                    .filteredOn(BusinessException.class::isInstance)
                    .singleElement()
                    .satisfies(result -> assertThat(((BusinessException) result).getCode()).isEqualTo(409));
            assertThat(userMapper.selectCount(new QueryWrapper<RmqStudioUser>()
                    .eq("admin", true)
                    .eq("enabled", true))).isEqualTo(1L);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Throwable disableAfterStart(Long userId, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                return new IllegalStateException("Timed out waiting to start concurrent disable request");
            }
            authService.setUserEnabled(userId, false);
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    private Long insertAdministrator(String username) {
        RmqStudioUser user = new RmqStudioUser();
        user.setUsername(username);
        user.setPasswordHash("test-password-hash");
        user.setAdmin(true);
        user.setEnabled(true);
        user.setPasswordChangedAt(LocalDateTime.now());
        userMapper.insert(user);
        return user.getId();
    }

    private void clearDatabase() {
        sessionMapper.delete(null);
        userMapper.delete(null);
    }
}
