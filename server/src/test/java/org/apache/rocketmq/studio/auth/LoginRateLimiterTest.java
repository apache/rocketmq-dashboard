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
    void boundsTrackedUsernamesWithoutEvictingActiveLocksTest() {
        limiter = new LoginRateLimiter(clock, 2);
        for (int attempt = 0; attempt < LoginRateLimiter.MAX_FAILED_ATTEMPTS; attempt++) {
            limiter.recordFailure("operator");
        }
        limiter.recordFailure("second-user");

        for (int suffix = 0; suffix < 20; suffix++) {
            limiter.recordFailure("attacker-" + suffix);
        }

        assertThat(limiter.trackedUsernameCount()).isEqualTo(2);
        assertThatThrownBy(() -> limiter.checkAllowed("operator"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void reclaimsExpiredAttemptsBeforeAdmittingNewUsernamesTest() {
        limiter = new LoginRateLimiter(clock, 2);
        limiter.recordFailure("first-user");
        limiter.recordFailure("second-user");
        clock.advance(LoginRateLimiter.FAILURE_WINDOW.plusSeconds(1));

        limiter.recordFailure("third-user");

        assertThat(limiter.trackedUsernameCount()).isEqualTo(1);
        for (int attempt = 1; attempt < LoginRateLimiter.MAX_FAILED_ATTEMPTS; attempt++) {
            limiter.recordFailure("third-user");
        }
        assertThatThrownBy(() -> limiter.checkAllowed("third-user"))
                .isInstanceOf(BusinessException.class);
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
