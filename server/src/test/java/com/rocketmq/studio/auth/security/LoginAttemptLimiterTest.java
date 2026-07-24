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
package com.rocketmq.studio.auth.security;

import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptLimiterTest {
    @Test
    void fifthUsernameFailureIsCredentialFailureAndNextAttemptIsDenied() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(properties(2),
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        LoginAttemptLimiter.Decision decision = limiter.beforeAttempt("alice", "192.0.2.4");
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(decision.allowed()).isTrue();
            limiter.recordFailure(decision.key());
            decision = limiter.beforeAttempt("alice", "192.0.2.4");
        }

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfterSeconds()).isEqualTo(60);
    }

    @Test
    void twentiethPrefixFailureIsCredentialFailureAndNextAttemptIsDenied() {
        LoginAttemptLimiter limiter = limiter(new MutableClock());

        for (int attempt = 0; attempt < 20; attempt++) {
            LoginAttemptLimiter.Decision decision =
                limiter.beforeAttempt("user" + attempt, "192.0.2.99");
            assertThat(decision.allowed()).isTrue();
            limiter.recordFailure(decision.key());
        }

        assertThat(limiter.beforeAttempt("new-user", "192.0.2.1").allowed()).isFalse();
        assertThat(limiter.beforeAttempt("new-user", "192.0.3.1").allowed()).isTrue();
    }

    @Test
    void retryAfterRoundsUpAndLockExpiresAtOneMinute() {
        MutableClock clock = new MutableClock();
        LoginAttemptLimiter limiter = limiter(clock);
        fail(limiter, "alice", "192.0.2.4", 5);

        clock.advance(Duration.ofMillis(59_001));
        assertThat(limiter.beforeAttempt("alice", "192.0.2.4").retryAfterSeconds()).isOne();

        clock.advance(Duration.ofMillis(999));
        assertThat(limiter.beforeAttempt("alice", "192.0.2.4"))
            .extracting(LoginAttemptLimiter.Decision::allowed,
                LoginAttemptLimiter.Decision::retryAfterSeconds)
            .containsExactly(true, 0);
    }

    @Test
    void failuresUseATrueRollingMinute() {
        MutableClock clock = new MutableClock();
        LoginAttemptLimiter limiter = limiter(clock);
        fail(limiter, "alice", "192.0.2.4", 4);

        clock.advance(Duration.ofSeconds(60));
        fail(limiter, "alice", "192.0.2.4", 1);

        assertThat(limiter.beforeAttempt("alice", "192.0.2.4").allowed()).isTrue();
    }

    @Test
    void clockRollbackDoesNotShortenLockOrDisorderFailures() {
        MutableClock clock = new MutableClock();
        clock.setMillis(100_000L);
        LoginAttemptLimiter limiter = limiter(clock);
        LoginAttemptLimiter.Key key = fail(limiter, "alice", "192.0.2.4", 4);

        clock.setMillis(0);
        limiter.recordFailure(key);
        clock.setMillis(100_000L);

        assertThat(limiter.beforeAttempt("alice", "192.0.2.4"))
            .extracting(LoginAttemptLimiter.Decision::allowed,
                LoginAttemptLimiter.Decision::retryAfterSeconds)
            .containsExactly(false, 60);
    }

    @Test
    void timeArithmeticSaturatesAtLongBoundaries() {
        MutableClock upperClock = new MutableClock();
        upperClock.setMillis(Long.MAX_VALUE - 30_000L);
        LoginAttemptLimiter upperLimiter = limiter(upperClock);
        fail(upperLimiter, "upper", "192.0.2.4", 5);
        assertThat(upperLimiter.beforeAttempt("upper", "192.0.2.4"))
            .extracting(LoginAttemptLimiter.Decision::allowed,
                LoginAttemptLimiter.Decision::retryAfterSeconds)
            .containsExactly(false, 30);

        MutableClock lowerClock = new MutableClock();
        lowerClock.setMillis(Long.MIN_VALUE);
        LoginAttemptLimiter lowerLimiter = limiter(lowerClock);
        fail(lowerLimiter, "lower", "192.0.3.4", 5);
        assertThat(lowerLimiter.beforeAttempt("lower", "192.0.3.4"))
            .extracting(LoginAttemptLimiter.Decision::allowed,
                LoginAttemptLimiter.Decision::retryAfterSeconds)
            .containsExactly(false, 60);
    }

    @Test
    void successClearsUsernameFailuresButNotPrefixFailures() {
        LoginAttemptLimiter limiter = limiter(new MutableClock());
        LoginAttemptLimiter.Key aliceKey = fail(limiter, "alice", "192.0.2.4", 5);

        limiter.recordSuccess(aliceKey);
        assertThat(limiter.beforeAttempt("alice", "192.0.2.4").allowed()).isTrue();

        for (int attempt = 0; attempt < 15; attempt++) {
            LoginAttemptLimiter.Decision decision =
                limiter.beforeAttempt("other" + attempt, "192.0.2.9");
            limiter.recordFailure(decision.key());
        }
        assertThat(limiter.beforeAttempt("new-user", "192.0.2.200").allowed()).isFalse();
    }

    @Test
    void groupsLiteralAddressesByIpv4Slash24AndIpv6Slash64() {
        LoginAttemptLimiter limiter = limiter(new MutableClock());

        assertThat(key(limiter, "alice", "192.0.2.1").prefixDigest())
            .isEqualTo(key(limiter, "bob", "192.0.2.254").prefixDigest())
            .isNotEqualTo(key(limiter, "bob", "192.0.3.1").prefixDigest());
        assertThat(key(limiter, "alice", "2001:db8:abcd:12::1").prefixDigest())
            .isEqualTo(key(limiter, "bob", "2001:0db8:abcd:0012:ffff::1").prefixDigest())
            .isNotEqualTo(key(limiter, "bob", "2001:db8:abcd:13::1").prefixDigest());
    }

    @Test
    void normalizesRfc4291MappedIpv6ToNativeIpv4Slash24() {
        LoginAttemptLimiter limiter = limiter(new MutableClock());
        String nativePrefix = key(limiter, "alice", "192.0.2.128").prefixDigest();

        assertThat(List.of(
            key(limiter, "alice", "::ffff:192.0.2.128").prefixDigest(),
            key(limiter, "alice", "::ffff:c000:280").prefixDigest(),
            key(limiter, "alice", "0:0:0:0:0:ffff:c000:0280").prefixDigest()
        )).containsOnly(nativePrefix);
        assertThat(key(limiter, "alice", "64:ff9b::c000:280").prefixDigest())
            .isNotEqualTo(nativePrefix);
        assertThat(key(limiter, "alice", "2001:db8::ffff:c000:280").prefixDigest())
            .isNotEqualTo(nativePrefix);
    }

    @Test
    void addressesLongerThanMaximumLiteralLengthUseUnknownPrefix() {
        LoginAttemptLimiter limiter = limiter(new MutableClock());
        String unknown = key(limiter, "alice", null).prefixDigest();

        assertThat(key(limiter, "alice", ".".repeat(46)).prefixDigest()).isEqualTo(unknown);
        assertThat(key(limiter, "alice", ":".repeat(46)).prefixDigest()).isEqualTo(unknown);
    }

    @Test
    void malformedHostnamesZonesAndNullShareUnknownPrefix() {
        LoginAttemptLimiter limiter = limiter(new MutableClock());
        String unknown = key(limiter, "alice", null).prefixDigest();

        assertThat(List.of(
            key(limiter, "alice", "example.com").prefixDigest(),
            key(limiter, "alice", "300.2.3.4").prefixDigest(),
            key(limiter, "alice", "2001:db8::1%eth0").prefixDigest(),
            key(limiter, "alice", "192.0.2.1::").prefixDigest(),
            key(limiter, "alice", "+1::").prefixDigest(),
            key(limiter, "alice", "-1::").prefixDigest(),
            key(limiter, "alice", "").prefixDigest()
        )).containsOnly(unknown);
    }

    @Test
    void usernamesAreCaseSensitiveAndInvalidValuesShareASeparateBucket() {
        LoginAttemptLimiter limiter = limiter(new MutableClock());

        assertThat(key(limiter, "Alice", "192.0.2.1").usernamePrefixDigest())
            .isNotEqualTo(key(limiter, "alice", "192.0.2.1").usernamePrefixDigest());
        assertThat(key(limiter, null, "192.0.2.1").usernamePrefixDigest())
            .isEqualTo(key(limiter, "", "192.0.2.1").usernamePrefixDigest())
            .isEqualTo(key(limiter, " ", "192.0.2.1").usernamePrefixDigest())
            .isEqualTo(key(limiter, "álîçé", "192.0.2.1").usernamePrefixDigest())
            .isNotEqualTo(key(limiter, "invalid", "192.0.2.1").usernamePrefixDigest());
    }

    @Test
    void keyContainsOnlyFixedLengthDigestsAndNoRawMarker() {
        LoginAttemptLimiter limiter = limiter(new MutableClock());
        LoginAttemptLimiter.Key key = key(limiter, "secret-user", "192.0.2.99");

        assertThat(key.usernamePrefixDigest()).matches("[0-9a-f]{64}");
        assertThat(key.prefixDigest()).matches("[0-9a-f]{64}");
        assertThat(key).isEqualTo(key(limiter, "secret-user", "192.0.2.99"));
        assertThat(key.toString())
            .doesNotContain("secret-user", "192.0.2", "unknown", "invalid");
    }

    @Test
    void keyConstructionIsRestrictedToTheLimiter() {
        assertThat(LoginAttemptLimiter.Key.class.getDeclaredConstructors())
            .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers()));
    }

    @Test
    void semaphoreSaturatesAndClosedPermitReleasesExactlyOnce() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(properties(2), new MutableClock());
        LoginAttemptLimiter.Permit first = limiter.acquirePasswordPermit();
        LoginAttemptLimiter.Permit second = limiter.acquirePasswordPermit();

        assertThat(first.acquired()).isTrue();
        assertThat(second.acquired()).isTrue();
        assertThat(limiter.acquirePasswordPermit().acquired()).isFalse();

        first.close();
        first.close();
        assertThat(limiter.acquirePasswordPermit().acquired()).isTrue();
        assertThat(limiter.acquirePasswordPermit().acquired()).isFalse();
        second.close();
    }

    @Test
    void concurrentCloseReleasesPermitOnlyOnce() throws Exception {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(properties(1), new MutableClock());
        LoginAttemptLimiter.Permit permit = limiter.acquirePasswordPermit();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Runnable> closes = new ArrayList<>();
            for (int index = 0; index < 32; index++) {
                closes.add(permit::close);
            }
            executor.invokeAll(closes.stream().<java.util.concurrent.Callable<Void>>map(close -> () -> {
                close.run();
                return null;
            }).toList());

            assertThat(limiter.acquirePasswordPermit().acquired()).isTrue();
            assertThat(limiter.acquirePasswordPermit().acquired()).isFalse();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void passwordSemaphoreIsFair() throws Exception {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(properties(1), new MutableClock());
        var field = LoginAttemptLimiter.class.getDeclaredField("passwordPermits");
        field.setAccessible(true);

        assertThat((Semaphore) field.get(limiter)).matches(Semaphore::isFair);
    }

    @Test
    void boundedAccessOrderMapsEvictEldestAndReportSizes() {
        AtomicReference<String> observedSizes = new AtomicReference<>();
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(
            new MutableClock(), 2, 2, 1, ignored -> { },
            (users, prefixes) -> observedSizes.set(users + ":" + prefixes));
        fail(limiter, "bob", "192.0.2.1", 4);
        fail(limiter, "alice", "192.0.3.1", 1);
        limiter.beforeAttempt("bob", "192.0.2.1");
        fail(limiter, "carol", "192.0.4.1", 1);

        LoginAttemptLimiter.Decision fifthFailure =
            limiter.beforeAttempt("bob", "192.0.2.1");
        assertThat(fifthFailure.allowed()).isTrue();
        limiter.recordFailure(fifthFailure.key());

        assertThat(limiter.beforeAttempt("bob", "192.0.2.1").allowed()).isFalse();
        assertThat(observedSizes.get()).isEqualTo("2:2");
    }

    @Test
    void cleanupExaminesExactlySixtyFourExpiredEntriesAndKeepsMapsBounded() {
        MutableClock clock = new MutableClock();
        AtomicInteger examined = new AtomicInteger();
        AtomicReference<String> observedSizes = new AtomicReference<>();
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(
            clock, 128, 128, 1, examined::set,
            (users, prefixes) -> observedSizes.set(users + ":" + prefixes));
        for (int index = 0; index < 256; index++) {
            fail(limiter, "user" + index, "10.0." + index + ".1", 1);
        }
        clock.advance(Duration.ofSeconds(61));
        examined.set(-1);

        limiter.beforeAttempt("probe", "203.0.113.1");

        assertThat(examined.get()).isEqualTo(64);
        assertThat(observedSizes.get()).isEqualTo("64:128");
    }

    @Test
    void tenThousandRepeatedCompletionsKeepTrackerDequesAtThresholds() throws Exception {
        LoginAttemptLimiter limiter = limiter(new MutableClock());
        LoginAttemptLimiter.Key key = key(limiter, "alice", "192.0.2.4");

        for (int completion = 0; completion < 10_000; completion++) {
            limiter.recordFailure(key);
        }

        assertTrackerFailureCounts(limiter, 5, 20);
    }

    @Test
    void concurrentCompletionsKeepTrackerDequesAtThresholds() throws Exception {
        LoginAttemptLimiter limiter = limiter(new MutableClock());
        LoginAttemptLimiter.Key key = key(limiter, "alice", "192.0.2.4");
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> completions = new ArrayList<>();
            for (int worker = 0; worker < 8; worker++) {
                completions.add(() -> {
                    for (int completion = 0; completion < 2_000; completion++) {
                        limiter.recordFailure(key);
                    }
                    return null;
                });
            }
            for (Future<Void> completion : executor.invokeAll(completions)) {
                completion.get();
            }

            assertTrackerFailureCounts(limiter, 5, 20);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void assertTrackerFailureCounts(
        LoginAttemptLimiter limiter,
        int expectedUserFailures,
        int expectedPrefixFailures
    ) throws Exception {
        assertThat(trackerFailureCount(limiter, "userTrackers")).isEqualTo(expectedUserFailures);
        assertThat(trackerFailureCount(limiter, "prefixTrackers")).isEqualTo(expectedPrefixFailures);
    }

    private static int trackerFailureCount(LoginAttemptLimiter limiter, String mapFieldName)
        throws Exception {
        var mapField = LoginAttemptLimiter.class.getDeclaredField(mapFieldName);
        mapField.setAccessible(true);
        Object tracker = ((Map<?, ?>) mapField.get(limiter)).values().iterator().next();
        var failuresField = tracker.getClass().getDeclaredField("failures");
        failuresField.setAccessible(true);
        return ((ArrayDeque<?>) failuresField.get(tracker)).size();
    }

    private static LoginAttemptLimiter limiter(Clock clock) {
        return new LoginAttemptLimiter(properties(2), clock);
    }

    private static LoginAttemptLimiter.Key fail(
        LoginAttemptLimiter limiter,
        String username,
        String address,
        int count
    ) {
        LoginAttemptLimiter.Key key = null;
        for (int attempt = 0; attempt < count; attempt++) {
            LoginAttemptLimiter.Decision decision = limiter.beforeAttempt(username, address);
            assertThat(decision.allowed()).isTrue();
            key = decision.key();
            limiter.recordFailure(key);
        }
        return key;
    }

    private static LoginAttemptLimiter.Key key(
        LoginAttemptLimiter limiter,
        String username,
        String address
    ) {
        return limiter.beforeAttempt(username, address).key();
    }

    private static StudioSecurityProperties properties(int maxConcurrentPasswordChecks) {
        return new StudioSecurityProperties(
            null,
            Duration.ofHours(24),
            5,
            1_000,
            Duration.ofSeconds(1),
            maxConcurrentPasswordChecks
        );
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.EPOCH;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        private void setMillis(long millis) {
            instant = Instant.ofEpochMilli(millis);
        }
    }
}
