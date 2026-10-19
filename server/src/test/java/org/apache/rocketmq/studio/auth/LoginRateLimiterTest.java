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

    @Test
    void capacityShouldNotDisableRateLimitingForAnUntrackedUsernameTest() {
        limiter = new LoginRateLimiter(clock, 2);
        limiter.recordFailure("decoy-one");
        limiter.recordFailure("decoy-two");

        for (int attempt = 0; attempt < LoginRateLimiter.MAX_FAILED_ATTEMPTS; attempt++) {
            assertThatCode(() -> limiter.checkAllowed("operator")).doesNotThrowAnyException();
            limiter.recordFailure("operator");
        }

        assertThat(limiter.trackedUsernameCount()).isEqualTo(2);
        assertThatThrownBy(() -> limiter.checkAllowed("operator"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getCode()).isEqualTo(429));
    }

    @Test
    void trackerCapacityShouldRemainBoundedWhenAllSlotsAreLockedTest() {
        limiter = new LoginRateLimiter(clock, 2);
        for (int attempt = 0; attempt < LoginRateLimiter.MAX_FAILED_ATTEMPTS; attempt++) {
            limiter.recordFailure("operator");
            limiter.recordFailure("second-user");
        }

        for (int attempt = 0; attempt < LoginRateLimiter.MAX_FAILED_ATTEMPTS; attempt++) {
            limiter.recordFailure("attacker");
        }
        assertThat(limiter.trackedUsernameCount()).isEqualTo(2);
        assertThatThrownBy(() -> limiter.checkAllowed("operator"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> limiter.checkAllowed("second-user"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> limiter.checkAllowed("attacker"))
                .isInstanceOf(BusinessException.class);
        assertThat(limiter.activeOverflowBucketCount()).isEqualTo(1);
    }

    @Test
    void failureAlreadyInFlightShouldNotClearAnActiveLockTest() {
        for (int attempt = 0; attempt < LoginRateLimiter.MAX_FAILED_ATTEMPTS; attempt++) {
            limiter.recordFailure("operator");
        }

        // A login request can pass checkAllowed before another request creates the lock,
        // then finish password verification and record its failure after the lock exists.
        limiter.recordFailure("operator");

        assertThatThrownBy(() -> limiter.checkAllowed("operator"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception ->
                        assertThat(((BusinessException) exception).getCode()).isEqualTo(429));
    }

    @Test
    void activeLocksShouldNotEvictEachOtherWhenNewFailuresArriveTest() {
        limiter = new LoginRateLimiter(clock, 2);
        for (int attempt = 0; attempt < LoginRateLimiter.MAX_FAILED_ATTEMPTS; attempt++) {
            limiter.recordFailure("operator");
            limiter.recordFailure("second-user");
        }
        limiter.recordFailure("operator");

        assertThat(limiter.trackedUsernameCount()).isEqualTo(2);
        assertThatThrownBy(() -> limiter.checkAllowed("second-user"))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * A lock the overflow tracker earned must survive an exact tracker refill. The decoys' failure
     * windows end before the operator's lock does, so freeing their slots and taking them again -
     * something the same unauthenticated endpoint can do - happens while the operator lock is still
     * in force. If freeing a slot also drops the overflow state, that lock is cancelled with minutes
     * of {@code LOCK_DURATION} still to run, because the overflow bucket is what a saturated tracker
     * enforces.
     */
    @Test
    void refillingTheExactTrackerMustNotReleaseAnActiveOverflowLockTest() {
        limiter = new LoginRateLimiter(clock, 2);
        limiter.recordFailure("decoy-one");
        limiter.recordFailure("decoy-two");
        clock.advance(Duration.ofMinutes(4));
        for (int attempt = 0; attempt < LoginRateLimiter.MAX_FAILED_ATTEMPTS; attempt++) {
            limiter.recordFailure("operator");
        }
        assertThatThrownBy(() -> limiter.checkAllowed("operator"))
                .isInstanceOf(BusinessException.class);

        // The decoy windows end here, four minutes before the operator lock does; two fresh usernames
        // take the freed slots, so the tracker is saturated again and enforcing overflow locks.
        clock.advance(Duration.ofMinutes(1).plusSeconds(1));
        limiter.recordFailure("fresh-one");
        limiter.recordFailure("fresh-two");
        assertThat(limiter.trackedUsernameCount()).isEqualTo(2);

        assertThatThrownBy(() -> limiter.checkAllowed("operator"))
                .isInstanceOf(BusinessException.class)
                .hasMessageStartingWith("Too many failed login attempts");
    }

    /**
     * The same cancellation through the lookup path: reading an expired exact entry removes it and
     * frees its slot immediately, without waiting for the sweep.
     */
    @Test
    void lookingUpAnExpiredUsernameMustNotReleaseAnotherUsersOverflowLockTest() {
        limiter = new LoginRateLimiter(clock, 2);
        limiter.recordFailure("expired-decoy");
        clock.advance(Duration.ofMinutes(2));
        limiter.recordFailure("live-decoy");
        clock.advance(Duration.ofMinutes(2));
        for (int attempt = 0; attempt < LoginRateLimiter.MAX_FAILED_ATTEMPTS; attempt++) {
            limiter.recordFailure("operator");
        }
        assertThatThrownBy(() -> limiter.checkAllowed("operator"))
                .isInstanceOf(BusinessException.class);

        // Only "expired-decoy" is past its window here; the operator lock runs for four more minutes.
        clock.advance(Duration.ofMinutes(1).plusSeconds(1));
        assertThatCode(() -> limiter.checkAllowed("expired-decoy")).doesNotThrowAnyException();
        limiter.recordFailure("fresh-user");
        assertThat(limiter.trackedUsernameCount()).isEqualTo(2);

        assertThatThrownBy(() -> limiter.checkAllowed("operator"))
                .isInstanceOf(BusinessException.class)
                .hasMessageStartingWith("Too many failed login attempts");
    }

    /**
     * Control: the "the saturated episode is over" reset still applies to overflow state that carries
     * no active lock, so the overflow tracker cannot accumulate stale entries across episodes.
     */
    @Test
    void freeingSlotStillDropsOverflowStateThatCarriesNoLockTest() {
        limiter = new LoginRateLimiter(clock, 2);
        limiter.recordFailure("decoy-one");
        limiter.recordFailure("decoy-two");
        clock.advance(Duration.ofMinutes(1));
        limiter.recordFailure("operator");
        assertThat(limiter.activeOverflowBucketCount()).isEqualTo(1);

        // Past the decoy windows, and past the operator's failure window too, but the operator was
        // never locked: its overflow entry must be dropped with the episode.
        clock.advance(Duration.ofMinutes(5));
        limiter.recordFailure("fresh-user");

        assertThat(limiter.activeOverflowBucketCount()).isZero();
    }

    /**
     * The same cancellation through the success path: the successful login frees the caller's exact
     * slot, which any unauthenticated caller can take again immediately, so the tracker is saturated
     * again and the overflow tracker is once more in charge of the operator lock. A successful login
     * is evidence about its own username only, so it must not release a lock that another username
     * earned.
     */
    @Test
    void successfulLoginMustNotReleaseAnotherUsersOverflowLockTest() {
        limiter = new LoginRateLimiter(clock, 2);
        limiter.recordFailure("decoy-one");
        limiter.recordFailure("decoy-two");
        clock.advance(Duration.ofMinutes(4));
        for (int attempt = 0; attempt < LoginRateLimiter.MAX_FAILED_ATTEMPTS; attempt++) {
            limiter.recordFailure("operator");
        }
        assertThatThrownBy(() -> limiter.checkAllowed("operator"))
                .isInstanceOf(BusinessException.class);

        // "decoy-one" authenticates one minute before the operator lock expires; winning back its
        // slot must not cancel a lock that belongs to a different username.
        clock.advance(Duration.ofMinutes(1).plusSeconds(1));
        limiter.recordSuccess("decoy-one");
        limiter.recordFailure("fresh-one");
        limiter.recordFailure("fresh-two");
        assertThat(limiter.trackedUsernameCount()).isEqualTo(2);

        assertThatThrownBy(() -> limiter.checkAllowed("operator"))
                .isInstanceOf(BusinessException.class)
                .hasMessageStartingWith("Too many failed login attempts");
    }

    /**
     * Control for the success path: overflow state that carries no lock is still dropped when the
     * login frees its exact slot, so the "the saturated episode is over" reset keeps working there.
     */
    @Test
    void successfulLoginStillDropsOverflowStateThatCarriesNoLockTest() {
        limiter = new LoginRateLimiter(clock, 2);
        limiter.recordFailure("decoy-one");
        limiter.recordFailure("decoy-two");
        clock.advance(Duration.ofMinutes(1));
        limiter.recordFailure("operator");
        assertThat(limiter.activeOverflowBucketCount()).isEqualTo(1);

        limiter.recordSuccess("decoy-one");

        assertThat(limiter.activeOverflowBucketCount()).isZero();
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
