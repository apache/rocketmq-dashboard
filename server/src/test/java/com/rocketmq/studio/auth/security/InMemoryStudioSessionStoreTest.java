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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.rocketmq.studio.auth.security.StudioSessionStore.IssuedSession;
import com.rocketmq.studio.auth.security.StudioSessionStore.Session;
import com.rocketmq.studio.auth.security.StudioUserRegistry.Role;
import com.rocketmq.studio.auth.security.StudioUserRegistry.User;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryStudioSessionStoreTest {
    private static final Instant START = Instant.parse("2026-07-24T00:00:00Z");
    private static final String ISSUE_FAILURE = "Unable to issue session";

    @Test
    void storesOnlyTheSha256DigestOfAThirtyTwoByteUrlSafeToken() throws Exception {
        Map<TokenDigest, Session> backing = new HashMap<>();
        byte[] randomBytes = bytes(7);
        InMemoryStudioSessionStore store = new InMemoryStudioSessionStore(
            properties(5),
            Clock.fixed(START, ZoneOffset.UTC),
            target -> System.arraycopy(randomBytes, 0, target, 0, target.length),
            () -> UUID.fromString("00000000-0000-0000-0000-000000000001"),
            backing,
            ignored -> {
            }
        );

        IssuedSession issued = store.issue(user("token-user"), 17);

        assertThat(issued.token())
            .hasSize(43)
            .matches("[A-Za-z0-9_-]{43}")
            .doesNotContain("=");
        assertThat(Base64.getUrlDecoder().decode(issued.token())).hasSize(32);
        TokenDigest expected = TokenDigest.fromBytes(
            MessageDigest.getInstance("SHA-256")
                .digest(issued.token().getBytes(StandardCharsets.UTF_8))
        );
        assertThat(backing).containsOnlyKeys(expected);
        assertThat(backing).containsValue(issued.session());
        assertThat(backing.toString()).doesNotContain(issued.token());
        assertThat(expected.toString()).doesNotContain(issued.token());

        byte[] digestBytes = MessageDigest.getInstance("SHA-256")
            .digest(issued.token().getBytes(StandardCharsets.UTF_8));
        TokenDigest defensive = TokenDigest.fromBytes(digestBytes);
        digestBytes[0] ^= 0x7f;
        assertThat(defensive).isEqualTo(expected);
        assertThat(TokenDigest.fromBytes(bytes(1))).isNotEqualTo(expected);
        assertThat(TokenDigest.fromBytes(bytes(2))).isNotEqualTo(expected);
    }

    @Test
    void issuesAndResolvesAnImmutableAbsoluteExpirySessionWithoutSliding() {
        MutableClock clock = new MutableClock(START);
        InMemoryStudioSessionStore store = store(properties(5), clock);
        User user = new User("Alice", "hash", Role.ADMIN, "fingerprint-17");

        IssuedSession issued = store.issue(user, 29);

        assertThat(issued.session()).isEqualTo(new Session(
            issued.session().id(),
            "Alice",
            Role.ADMIN,
            "fingerprint-17",
            29,
            START,
            START.plusSeconds(300),
            0
        ));
        assertThat(store.resolve(issued.token())).contains(issued.session());

        clock.advance(Duration.ofMinutes(4));

        Optional<Session> resolved = store.resolve(issued.token());
        assertThat(resolved).contains(issued.session());
        assertThat(resolved.orElseThrow().expiresAt()).isEqualTo(START.plusSeconds(300));
        assertThat(issued.toString()).doesNotContain(issued.token());
    }

    @Test
    void removesAnExpiredRequestedSessionAndTreatsUnknownInputAsAbsent() {
        MutableClock clock = new MutableClock(START);
        Map<TokenDigest, Session> backing = new HashMap<>();
        InMemoryStudioSessionStore store = store(properties(5), clock, backing);
        IssuedSession issued = store.issue(user("expiring-user"), 1);

        assertThat(store.resolve(null)).isEmpty();
        assertThat(store.resolve("malformed token")).isEmpty();
        assertThat(store.resolve("A".repeat(43))).isEmpty();

        clock.advance(Duration.ofMinutes(5));

        assertThat(store.resolve(issued.token())).isEmpty();
        assertThat(backing).isEmpty();
        assertThat(store.resolve(issued.token())).isEmpty();
    }

    @Test
    void retriesOneDigestCollisionAndPublishesOnlyTheSuccessfulToken() {
        Map<TokenDigest, Session> backing = new HashMap<>();
        ByteQueueGenerator tokens = new ByteQueueGenerator(
            bytes(1),
            bytes(1),
            bytes(2)
        );
        InMemoryStudioSessionStore store = new InMemoryStudioSessionStore(
            properties(5),
            Clock.fixed(START, ZoneOffset.UTC),
            tokens,
            new UuidSequence(),
            backing,
            ignored -> {
            }
        );
        IssuedSession first = store.issue(user("collision-user"), 1);

        IssuedSession second = store.issue(user("collision-user"), 2);

        assertThat(second.token()).isNotEqualTo(first.token());
        assertThat(tokens.calls()).isEqualTo(3);
        assertThat(backing).hasSize(2);
        assertThat(store.resolve(first.token())).contains(first.session());
        assertThat(store.resolve(second.token())).contains(second.session());
    }

    @Test
    void failsGenericallyAfterThreeDigestCollisionsWithoutPartialState() {
        Map<TokenDigest, Session> backing = new HashMap<>();
        ByteQueueGenerator tokens = new ByteQueueGenerator(
            bytes(3),
            bytes(3),
            bytes(3),
            bytes(3)
        );
        InMemoryStudioSessionStore store = new InMemoryStudioSessionStore(
            properties(5),
            Clock.fixed(START, ZoneOffset.UTC),
            tokens,
            new UuidSequence(),
            backing,
            ignored -> {
            }
        );
        IssuedSession existing = store.issue(user("collision-marker-user"), 1);

        assertThatThrownBy(() -> store.issue(user("collision-marker-user"), 2))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(ISSUE_FAILURE)
            .message()
            .doesNotContain(existing.token(), "collision-marker-user", "3, 4, 5");
        assertThat(backing).containsOnlyKeys(TokenDigest.sha256(existing.token()));
        assertThat(store.resolve(existing.token())).contains(existing.session());
    }

    @Test
    void rejectsInvalidUserDataWithoutLeakingIt() {
        InMemoryStudioSessionStore store = store(
            properties(5),
            Clock.fixed(START, ZoneOffset.UTC)
        );
        String marker = "sensitive-user-marker";

        assertThatThrownBy(() -> store.issue(null, 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(ISSUE_FAILURE);
        assertThatThrownBy(() -> store.issue(
            new User(marker, null, Role.USER, "fingerprint"),
            1
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(ISSUE_FAILURE)
            .message()
            .doesNotContain(marker);
        assertThatThrownBy(() -> store.issue(
            new User(marker, "hash", null, "fingerprint"),
            1
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(ISSUE_FAILURE)
            .message()
            .doesNotContain(marker);
        assertThatThrownBy(() -> store.issue(
            new User(marker, "hash", Role.USER, null),
            1
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(ISSUE_FAILURE)
            .message()
            .doesNotContain(marker);
    }

    @Test
    void instantOverflowFailsGenericallyWithoutPublishingPartialState() {
        Map<TokenDigest, Session> backing = new HashMap<>();
        InMemoryStudioSessionStore store = store(
            properties(5),
            Clock.fixed(Instant.MAX, ZoneOffset.UTC),
            backing
        );

        assertThatThrownBy(() -> store.issue(user("overflow-marker"), 1))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(ISSUE_FAILURE)
            .message()
            .doesNotContain("overflow-marker", Instant.MAX.toString());
        assertThat(backing).isEmpty();
    }

    @Test
    void revokesBySessionIdIdempotently() {
        InMemoryStudioSessionStore store = store(
            properties(5),
            Clock.fixed(START, ZoneOffset.UTC)
        );
        IssuedSession first = store.issue(user("alice"), 1);
        IssuedSession second = store.issue(user("alice"), 1);

        store.revoke(first.session().id());
        store.revoke(first.session().id());
        store.revoke(UUID.randomUUID());
        store.revoke(null);

        assertThat(store.resolve(first.token())).isEmpty();
        assertThat(store.resolve(second.token())).contains(second.session());
    }

    @Test
    void revokesAllSessionsForOnlyTheExactCaseSensitiveUsername() {
        InMemoryStudioSessionStore store = store(
            properties(5),
            Clock.fixed(START, ZoneOffset.UTC)
        );
        IssuedSession upperFirst = store.issue(user("Alice"), 1);
        IssuedSession upperSecond = store.issue(user("Alice"), 1);
        IssuedSession lower = store.issue(user("alice"), 1);
        IssuedSession other = store.issue(user("bob"), 1);

        store.revokeByUser("Alice");
        store.revokeByUser("Alice");
        store.revokeByUser("missing");
        store.revokeByUser(null);

        assertThat(store.resolve(upperFirst.token())).isEmpty();
        assertThat(store.resolve(upperSecond.token())).isEmpty();
        assertThat(store.resolve(lower.token())).contains(lower.session());
        assertThat(store.resolve(other.token())).contains(other.session());
    }

    @Test
    void aFreshStoreDoesNotResolveTokensFromAPreviousStore() {
        Clock clock = Clock.fixed(START, ZoneOffset.UTC);
        InMemoryStudioSessionStore original = store(properties(5), clock);
        IssuedSession issued = original.issue(user("restart-user"), 1);

        InMemoryStudioSessionStore restarted = new InMemoryStudioSessionStore(
            properties(5),
            clock
        );

        assertThat(restarted.resolve(issued.token())).isEmpty();
        assertThat(original.resolve(issued.token())).contains(issued.session());
    }

    @Test
    void perUserCapEvictsTheOldestSequenceWhenClockTimesAreIdentical() {
        InMemoryStudioSessionStore store = store(
            properties(2),
            Clock.fixed(START, ZoneOffset.UTC)
        );
        IssuedSession first = store.issue(user("alice"), 1);
        IssuedSession other = store.issue(user("bob"), 1);
        IssuedSession second = store.issue(user("alice"), 1);
        IssuedSession third = store.issue(user("alice"), 1);

        assertThat(first.session().issuedAt()).isEqualTo(third.session().issuedAt());
        assertThat(List.of(
            first.session().sequence(),
            other.session().sequence(),
            second.session().sequence(),
            third.session().sequence()
        )).containsExactly(0L, 1L, 2L, 3L);
        assertThat(store.resolve(first.token())).isEmpty();
        assertThat(store.resolve(second.token())).contains(second.session());
        assertThat(store.resolve(third.token())).contains(third.session());
        assertThat(store.resolve(other.token())).contains(other.session());

        store.revoke(first.session().id());
        store.revoke(second.session().id());
        assertThat(store.resolve(second.token())).isEmpty();
        assertThat(store.resolve(third.token())).contains(third.session());

        IssuedSession fourth = store.issue(user("alice"), 1);
        assertThat(store.resolve(third.token())).contains(third.session());
        assertThat(store.resolve(fourth.token())).contains(fourth.session());
    }

    @Test
    void sequenceExhaustionFailsGenericallyWithoutChangingInjectedState() {
        String existingToken = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes(21));
        Session existing = new Session(
            UUID.fromString("00000000-0000-0000-0000-000000000099"),
            "existing-user",
            Role.USER,
            "existing-fingerprint",
            1,
            START,
            Instant.MAX,
            Long.MAX_VALUE
        );
        Map<TokenDigest, Session> backing = new HashMap<>();
        backing.put(TokenDigest.sha256(existingToken), existing);
        InMemoryStudioSessionStore store = new InMemoryStudioSessionStore(
            properties(5),
            Clock.fixed(START, ZoneOffset.UTC),
            new ByteQueueGenerator(bytes(22)),
            new UuidSequence(),
            backing,
            ignored -> {
            }
        );

        assertThatThrownBy(() -> store.issue(user("sequence-marker"), 2))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(ISSUE_FAILURE)
            .message()
            .doesNotContain("sequence-marker", Long.toString(Long.MAX_VALUE));
        assertThat(backing).containsOnlyKeys(TokenDigest.sha256(existingToken));
        assertThat(store.resolve(existingToken)).contains(existing);
    }

    @Test
    void resolveDrainsOnlySixtyFourExpiredEntriesPerCallAndPreservesLiveSessions() {
        MutableClock clock = new MutableClock(START);
        Map<TokenDigest, Session> backing = new HashMap<>();
        List<Integer> cleanupCounts = new ArrayList<>();
        InMemoryStudioSessionStore store = cleanupStore(clock, backing, cleanupCounts);
        for (int index = 0; index < 70; index++) {
            store.issue(user("expired-" + index), 1);
        }
        clock.advance(Duration.ofMinutes(4));
        IssuedSession live = store.issue(user("live-user"), 1);
        clock.advance(Duration.ofMinutes(1));
        cleanupCounts.clear();

        assertThat(store.resolve("A".repeat(43))).isEmpty();

        assertThat(cleanupCounts).containsExactly(64);
        assertThat(backing).hasSize(7);

        assertThat(store.resolve("B".repeat(43))).isEmpty();

        assertThat(cleanupCounts).containsExactly(64, 6);
        assertThat(backing).hasSize(1);
        assertThat(store.resolve(live.token())).contains(live.session());
    }

    @Test
    void staleRevocationEntriesCountAgainstTheCleanupBudget() {
        MutableClock clock = new MutableClock(START);
        Map<TokenDigest, Session> backing = new HashMap<>();
        List<Integer> cleanupCounts = new ArrayList<>();
        InMemoryStudioSessionStore store = cleanupStore(clock, backing, cleanupCounts);
        List<IssuedSession> issued = new ArrayList<>();
        for (int index = 0; index < 70; index++) {
            issued.add(store.issue(user("revoked-" + index), 1));
        }
        issued.forEach(session -> store.revoke(session.session().id()));
        cleanupCounts.clear();

        store.resolve("A".repeat(43));
        store.resolve("B".repeat(43));

        assertThat(cleanupCounts).containsExactly(64, 6);
        assertThat(backing).isEmpty();
    }

    @Test
    void issueAlsoPerformsAtMostSixtyFourCleanupExaminations() {
        MutableClock clock = new MutableClock(START);
        Map<TokenDigest, Session> backing = new HashMap<>();
        List<Integer> cleanupCounts = new ArrayList<>();
        InMemoryStudioSessionStore store = cleanupStore(clock, backing, cleanupCounts);
        for (int index = 0; index < 70; index++) {
            store.issue(user("old-" + index), 1);
        }
        clock.advance(Duration.ofMinutes(5));
        cleanupCounts.clear();

        IssuedSession fresh = store.issue(user("fresh-user"), 2);

        assertThat(cleanupCounts).containsExactly(64);
        assertThat(backing).hasSize(7);
        assertThat(store.resolve(fresh.token())).contains(fresh.session());
        assertThat(cleanupCounts).containsExactly(64, 6);
        assertThat(backing).hasSize(1);
    }

    @Test
    void requestedExpiredSessionIsRemovedAfterCleanupBudgetIsConsumed() {
        MutableClock clock = new MutableClock(START);
        Map<TokenDigest, Session> backing = new HashMap<>();
        List<Integer> cleanupCounts = new ArrayList<>();
        InMemoryStudioSessionStore store = cleanupStore(clock, backing, cleanupCounts);
        List<IssuedSession> issued = new ArrayList<>();
        for (int index = 0; index < 65; index++) {
            issued.add(store.issue(user("requested-" + index), 1));
        }
        IssuedSession requested = issued.get(64);
        clock.advance(Duration.ofMinutes(5));
        cleanupCounts.clear();

        assertThat(store.resolve(requested.token())).isEmpty();

        assertThat(cleanupCounts).containsExactly(64);
        assertThat(backing).isEmpty();
    }

    @Test
    void concurrentIssueForOneUserRespectsTheCapAndUsesUniqueSequences() throws Exception {
        Map<TokenDigest, Session> backing = new HashMap<>();
        InMemoryStudioSessionStore store = new InMemoryStudioSessionStore(
            properties(5),
            Clock.fixed(START, ZoneOffset.UTC),
            new CountingTokenGenerator(),
            new UuidSequence(),
            backing,
            ignored -> {
            }
        );
        int callerCount = 40;
        ExecutorService executor = Executors.newFixedThreadPool(callerCount);
        try {
            CountDownLatch ready = new CountDownLatch(callerCount);
            CountDownLatch begin = new CountDownLatch(1);
            List<Future<IssuedSession>> futures = new ArrayList<>();
            for (int index = 0; index < callerCount; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    await(begin);
                    return store.issue(user("concurrent-user"), 1);
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            begin.countDown();

            List<IssuedSession> issued = new ArrayList<>();
            for (Future<IssuedSession> future : futures) {
                issued.add(future.get(5, TimeUnit.SECONDS));
            }

            assertThat(issued).extracting(IssuedSession::token).doesNotHaveDuplicates();
            assertThat(issued).extracting(item -> item.session().sequence())
                .doesNotHaveDuplicates()
                .hasSize(callerCount);
            assertThat(backing).hasSize(5);
            assertThat(issued.stream().filter(
                item -> store.resolve(item.token()).isPresent()
            )).hasSize(5);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentResolveAndRevocationNeverResurrectSessionsOrCorruptIndexes()
        throws Exception {
        Map<TokenDigest, Session> backing = new HashMap<>();
        InMemoryStudioSessionStore store = new InMemoryStudioSessionStore(
            properties(20),
            Clock.fixed(START, ZoneOffset.UTC),
            new CountingTokenGenerator(),
            new UuidSequence(),
            backing,
            ignored -> {
            }
        );
        List<IssuedSession> issued = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            issued.add(store.issue(user("shared-user"), 1));
        }
        int callerCount = 44;
        CountDownLatch ready = new CountDownLatch(callerCount);
        CountDownLatch begin = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(callerCount);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (IssuedSession item : issued) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    await(begin);
                    for (int repeat = 0; repeat < 20; repeat++) {
                        store.resolve(item.token());
                    }
                }));
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    await(begin);
                    store.revoke(item.session().id());
                    store.revoke(item.session().id());
                }));
            }
            for (int index = 0; index < 4; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    await(begin);
                    store.revokeByUser("shared-user");
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            begin.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        store.revokeByUser("shared-user");
        assertThat(backing).isEmpty();
        assertThat(issued).allSatisfy(
            item -> assertThat(store.resolve(item.token())).isEmpty()
        );

        IssuedSession replacement = store.issue(user("shared-user"), 2);
        assertThat(store.resolve(replacement.token())).contains(replacement.session());
        store.revokeByUser("shared-user");
        assertThat(store.resolve(replacement.token())).isEmpty();
        assertThat(backing).isEmpty();
    }

    @Test
    void doesNotLogRawTokensOrUsernames() {
        String usernameMarker = "unique-session-username-marker";
        Logger logger = (Logger) LoggerFactory.getLogger(InMemoryStudioSessionStore.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        IssuedSession[] issued = new IssuedSession[1];
        try {
            InMemoryStudioSessionStore store = store(
                properties(5),
                Clock.fixed(START, ZoneOffset.UTC)
            );
            issued[0] = store.issue(user(usernameMarker), 1);
            store.resolve(issued[0].token());
            store.revokeByUser(usernameMarker);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list)
            .extracting(ILoggingEvent::getFormattedMessage)
            .allSatisfy(message -> assertThat(message)
                .doesNotContain(usernameMarker, issued[0].token()));
    }

    @Test
    void sessionRecordsRejectNullComponentsAndDoNotExposeTheTokenInText() {
        UUID id = UUID.randomUUID();
        Session session = new Session(
            id,
            "record-user",
            Role.USER,
            "fingerprint",
            1,
            START,
            START.plusSeconds(300),
            1
        );
        IssuedSession issued = new IssuedSession("secret-token-marker", session);

        assertThat(issued.toString()).doesNotContain("secret-token-marker");
        assertThatThrownBy(() -> new IssuedSession(null, session))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IssuedSession("token", null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Session(
            null,
            "record-user",
            Role.USER,
            "fingerprint",
            1,
            START,
            START.plusSeconds(300),
            1
        )).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Session(
            id,
            null,
            Role.USER,
            "fingerprint",
            1,
            START,
            START.plusSeconds(300),
            1
        )).isInstanceOf(NullPointerException.class);
    }

    private static InMemoryStudioSessionStore cleanupStore(
        Clock clock,
        Map<TokenDigest, Session> backing,
        List<Integer> cleanupCounts
    ) {
        return new InMemoryStudioSessionStore(
            properties(5),
            clock,
            new CountingTokenGenerator(),
            new UuidSequence(),
            backing,
            cleanupCounts::add
        );
    }

    private static InMemoryStudioSessionStore store(
        StudioSecurityProperties properties,
        Clock clock
    ) {
        return store(properties, clock, new HashMap<>());
    }

    private static InMemoryStudioSessionStore store(
        StudioSecurityProperties properties,
        Clock clock,
        Map<TokenDigest, Session> backing
    ) {
        return new InMemoryStudioSessionStore(
            properties,
            clock,
            new ByteQueueGenerator(
                bytes(11),
                bytes(12),
                bytes(13),
                bytes(14),
                bytes(15)
            ),
            new UuidSequence(),
            backing,
            ignored -> {
            }
        );
    }

    private static StudioSecurityProperties properties(int maxSessions) {
        return new StudioSecurityProperties(
            null,
            Duration.ofMinutes(5),
            maxSessions,
            1000,
            Duration.ofSeconds(1),
            8
        );
    }

    private static User user(String username) {
        return new User(username, "unused-password-hash", Role.USER, username + "-fingerprint");
    }

    private static byte[] bytes(int marker) {
        byte[] bytes = new byte[32];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) (marker + index);
        }
        return bytes;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent test");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent test interrupted");
        }
    }

    private static final class ByteQueueGenerator implements TokenBytesGenerator {
        private final ArrayDeque<byte[]> values;
        private final AtomicInteger calls = new AtomicInteger();

        private ByteQueueGenerator(byte[]... values) {
            this.values = new ArrayDeque<>(List.of(values));
        }

        @Override
        public void nextBytes(byte[] target) {
            calls.incrementAndGet();
            byte[] value = values.removeFirst();
            assertThat(target).hasSize(32);
            System.arraycopy(value, 0, target, 0, target.length);
        }

        private int calls() {
            return calls.get();
        }
    }

    private static final class UuidSequence implements java.util.function.Supplier<UUID> {
        private final AtomicInteger value = new AtomicInteger();

        @Override
        public UUID get() {
            return new UUID(0, value.incrementAndGet());
        }
    }

    private static final class CountingTokenGenerator implements TokenBytesGenerator {
        private final AtomicInteger value = new AtomicInteger();

        @Override
        public void nextBytes(byte[] target) {
            Arrays.fill(target, (byte) 0x5a);
            ByteBuffer.wrap(target).putInt(value.incrementAndGet());
        }
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

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
            return current;
        }
    }
}
