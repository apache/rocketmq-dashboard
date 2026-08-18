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

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

    private static final Instant START = Instant.parse("2026-08-13T00:00:00Z");

    private MutableClock clock;
    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        limiter = new LoginRateLimiter(clock);
    }

    @Test
    void allowsAttemptsBelowThresholdTest() {
        for (int attempt = 1; attempt < LoginRateLimiter.MAX_FAILED_ATTEMPTS; attempt++) {
            limiter.recordFailure("operator");
        }

        assertThatCode(() -> limiter.checkAllowed("operator")).doesNotThrowAnyException();
    }

    @Test
    void locksUsernameAfterRepeatedFailuresTest() {
        for (int attempt = 0; attempt < LoginRateLimiter.MAX_FAILED_ATTEMPTS; attempt++) {
            limiter.recordFailure("operator");
        }

        assertThatThrownBy(() -> limiter.checkAllowed("operator"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getCode()).isEqualTo(429))
                .hasMessageStartingWith("Too many failed login attempts");
    }

    @Test
    void lockExpiresAfterLockDurationTest() {
        for (int attempt = 0; attempt < LoginRateLimiter.MAX_FAILED_ATTEMPTS; attempt++) {
            limiter.recordFailure("operator");
        }

        assertThatThrownBy(() -> limiter.checkAllowed("operator"))
                .isInstanceOf(BusinessException.class);

        clock.advance(LoginRateLimiter.LOCK_DURATION.plusSeconds(1));

        assertThatCode(() -> limiter.checkAllowed("operator")).doesNotThrowAnyException();

        // After an expired lock the failure counter starts over.
        limiter.recordFailure("operator");
        assertThatCode(() -> limiter.checkAllowed("operator")).doesNotThrowAnyException();
    }

    @Test
    void successResetsFailureCountTest() {
        for (int attempt = 1; attempt < LoginRateLimiter.MAX_FAILED_ATTEMPTS; attempt++) {
            limiter.recordFailure("operator");
        }
        limiter.recordSuccess("operator");

        for (int attempt = 1; attempt < LoginRateLimiter.MAX_FAILED_ATTEMPTS; attempt++) {
            limiter.recordFailure("operator");
        }

        assertThatCode(() -> limiter.checkAllowed("operator")).doesNotThrowAnyException();
    }

    @Test
    void failuresOutsideWindowDoNotAccumulateTest() {
        for (int attempt = 0; attempt < LoginRateLimiter.MAX_FAILED_ATTEMPTS - 1; attempt++) {
            limiter.recordFailure("operator");
        }

        clock.advance(LoginRateLimiter.FAILURE_WINDOW.plusSeconds(1));

        for (int attempt = 0; attempt < LoginRateLimiter.MAX_FAILED_ATTEMPTS - 1; attempt++) {
            limiter.recordFailure("operator");
        }

        assertThatCode(() -> limiter.checkAllowed("operator")).doesNotThrowAnyException();
    }

    @Test
    void lockAppliesRegardlessOfUsernameCaseAndWhitespaceTest() {
        for (int attempt = 0; attempt < LoginRateLimiter.MAX_FAILED_ATTEMPTS; attempt++) {
            limiter.recordFailure("  Operator ");
        }

        assertThatThrownBy(() -> limiter.checkAllowed("operator")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> limiter.checkAllowed("OPERATOR")).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsNewUsernamesWhenTrackingCapacityIsExhaustedTest() {
        for (int index = 0; index < LoginRateLimiter.MAX_TRACKED_USERNAMES; index++) {
            limiter.recordFailure("unknown-" + index);
        }

        assertThatThrownBy(() -> limiter.recordFailure("one-too-many"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getCode()).isEqualTo(429))
                .hasMessage("Too many login attempts are being tracked; try again later");
    }

    @Test
    void reclaimsExpiredUsernamesWhenTrackingCapacityIsExhaustedTest() {
        limiter = new LoginRateLimiter(clock, 2);
        limiter.recordFailure("stale-a");
        limiter.recordFailure("stale-b");
        clock.advance(LoginRateLimiter.FAILURE_WINDOW.plusSeconds(1));

        assertThatCode(() -> limiter.recordFailure("fresh-a")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.recordFailure("fresh-b")).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.recordFailure("one-too-many"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getCode()).isEqualTo(429));
    }

    @Test
    void existingUsernameCanReachLockThresholdAtTrackingCapacityTest() {
        limiter = new LoginRateLimiter(clock, 1);

        for (int attempt = 0; attempt < LoginRateLimiter.MAX_FAILED_ATTEMPTS; attempt++) {
            limiter.recordFailure("operator");
        }

        assertThatThrownBy(() -> limiter.checkAllowed("operator"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getCode()).isEqualTo(429));
    }

    @Test
    void concurrentNewUsernamesCannotExceedTrackingCapacityTest() throws Exception {
        int capacity = 8;
        int contenders = 64;
        limiter = new LoginRateLimiter(clock, capacity);
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < contenders; index++) {
                String username = "concurrent-" + index;
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        limiter.recordFailure(username);
                        accepted.incrementAndGet();
                    } catch (BusinessException exception) {
                        assertThat(exception.getCode()).isEqualTo(429);
                        rejected.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(accepted).hasValue(capacity);
        assertThat(rejected).hasValue(contenders - capacity);
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant start) {
            this.now = start;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
