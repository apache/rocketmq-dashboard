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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import com.rocketmq.studio.auth.security.StudioSessionStore.IssuedSession;
import com.rocketmq.studio.auth.security.StudioSessionStore.Session;
import com.rocketmq.studio.auth.security.StudioUserRegistry.User;

public final class InMemoryStudioSessionStore implements StudioSessionStore {
    static final int CLEANUP_LIMIT = 64;

    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_LENGTH = 43;
    private static final int TOKEN_ATTEMPTS = 3;
    private static final String ISSUE_FAILURE = "Unable to issue session";
    private static final Comparator<ExpiryEntry> EXPIRY_ORDER =
        Comparator.comparing(ExpiryEntry::expiresAt)
            .thenComparingLong(ExpiryEntry::sequence);

    private final StudioSecurityProperties properties;
    private final Clock clock;
    private final TokenBytesGenerator tokenBytesGenerator;
    private final Supplier<UUID> uuidSupplier;
    private final Map<TokenDigest, Session> sessions;
    private final IntConsumer cleanupObserver;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<UUID, TokenDigest> digestsById = new HashMap<>();
    private final Map<String, ArrayDeque<UUID>> sessionsByUser = new HashMap<>();
    private final Map<UUID, ExpiryEntry> expiryById = new HashMap<>();
    private final PriorityQueue<ExpiryEntry> expiryQueue = new PriorityQueue<>(EXPIRY_ORDER);

    private long nextSequence;

    public InMemoryStudioSessionStore(StudioSecurityProperties properties, Clock clock) {
        this(
            properties,
            clock,
            secureTokenBytesGenerator(),
            UUID::randomUUID,
            new HashMap<>(),
            ignored -> {
            }
        );
    }

    InMemoryStudioSessionStore(
        StudioSecurityProperties properties,
        Clock clock,
        TokenBytesGenerator tokenBytesGenerator,
        Supplier<UUID> uuidSupplier,
        Map<TokenDigest, Session> sessions,
        IntConsumer cleanupObserver
    ) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.tokenBytesGenerator = Objects.requireNonNull(
            tokenBytesGenerator,
            "tokenBytesGenerator"
        );
        this.uuidSupplier = Objects.requireNonNull(uuidSupplier, "uuidSupplier");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.cleanupObserver = Objects.requireNonNull(cleanupObserver, "cleanupObserver");
        initializeInjectedSessions();
    }

    @Override
    public IssuedSession issue(User user, long registryRevision) {
        validateUser(user);
        lock.lock();
        try {
            Instant issuedAt = issueInstant();
            cleanup(issuedAt);
            return issueLocked(user, registryRevision, issuedAt);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<Session> resolve(String rawToken) {
        lock.lock();
        try {
            Instant now = clock.instant();
            cleanup(now);
            if (!isTokenFormat(rawToken)) {
                return Optional.empty();
            }
            TokenDigest digest = TokenDigest.sha256(rawToken);
            Session session = sessions.get(digest);
            if (session == null) {
                return Optional.empty();
            }
            if (!session.expiresAt().isAfter(now)) {
                removeActive(digest, session);
                return Optional.empty();
            }
            return Optional.of(session);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void revoke(UUID sessionId) {
        if (sessionId == null) {
            return;
        }
        lock.lock();
        try {
            removeById(sessionId);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void revokeByUser(String username) {
        if (username == null) {
            return;
        }
        lock.lock();
        try {
            ArrayDeque<UUID> sessionIds = sessionsByUser.get(username);
            if (sessionIds == null) {
                return;
            }
            for (UUID sessionId : new ArrayList<>(sessionIds)) {
                removeById(sessionId);
            }
        } finally {
            lock.unlock();
        }
    }

    private static TokenBytesGenerator secureTokenBytesGenerator() {
        SecureRandom secureRandom = new SecureRandom();
        return secureRandom::nextBytes;
    }

    private static void validateUser(User user) {
        if (user == null
            || user.username() == null
            || user.passwordHash() == null
            || user.role() == null
            || user.fingerprint() == null) {
            throw new IllegalArgumentException(ISSUE_FAILURE);
        }
    }

    private Instant issueInstant() {
        try {
            return clock.instant();
        } catch (RuntimeException e) {
            throw issueFailure();
        }
    }

    private IssuedSession issueLocked(User user, long registryRevision, Instant issuedAt) {
        Instant expiresAt;
        try {
            expiresAt = issuedAt.plusSeconds(properties.sessionTtl().toSeconds());
        } catch (DateTimeException | ArithmeticException e) {
            throw issueFailure();
        }
        if (nextSequence == Long.MAX_VALUE) {
            throw issueFailure();
        }
        long candidateSequence = nextSequence;
        String rawToken = null;
        TokenDigest candidateDigest = null;
        for (int attempt = 0; attempt < TOKEN_ATTEMPTS; attempt++) {
            byte[] bytes = new byte[TOKEN_BYTES];
            try {
                tokenBytesGenerator.nextBytes(bytes);
                String generatedToken = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(bytes);
                TokenDigest generatedDigest = TokenDigest.sha256(generatedToken);
                if (!sessions.containsKey(generatedDigest)) {
                    rawToken = generatedToken;
                    candidateDigest = generatedDigest;
                    break;
                }
            } catch (RuntimeException e) {
                throw issueFailure();
            } finally {
                Arrays.fill(bytes, (byte) 0);
            }
        }
        if (rawToken == null) {
            throw issueFailure();
        }
        UUID sessionId;
        try {
            sessionId = uuidSupplier.get();
        } catch (RuntimeException e) {
            throw issueFailure();
        }
        if (sessionId == null || digestsById.containsKey(sessionId)) {
            throw issueFailure();
        }

        Session session = new Session(
            sessionId,
            user.username(),
            user.role(),
            user.fingerprint(),
            registryRevision,
            issuedAt,
            expiresAt,
            candidateSequence
        );
        evictForCapacity(user.username());
        sessions.put(candidateDigest, session);
        digestsById.put(session.id(), candidateDigest);
        sessionsByUser.computeIfAbsent(
            session.username(),
            ignored -> new ArrayDeque<>()
        ).addLast(session.id());
        ExpiryEntry expiryEntry = new ExpiryEntry(
            candidateDigest,
            session.id(),
            session.username(),
            session.expiresAt(),
            session.sequence()
        );
        expiryById.put(session.id(), expiryEntry);
        expiryQueue.add(expiryEntry);
        nextSequence = candidateSequence + 1;
        return new IssuedSession(rawToken, session);
    }

    private void evictForCapacity(String username) {
        ArrayDeque<UUID> userSessions = sessionsByUser.get(username);
        while (userSessions != null
            && userSessions.size() >= properties.maxSessionsPerUser()) {
            removeById(userSessions.peekFirst());
            userSessions = sessionsByUser.get(username);
        }
    }

    private void cleanup(Instant now) {
        int examined = 0;
        while (examined < CLEANUP_LIMIT) {
            ExpiryEntry entry = expiryQueue.peek();
            if (entry == null) {
                break;
            }
            Session current = sessions.get(entry.digest());
            boolean stale = current == null || !current.id().equals(entry.sessionId());
            if (!stale && current.expiresAt().isAfter(now)) {
                break;
            }
            expiryQueue.remove();
            examined++;
            expiryById.remove(entry.sessionId(), entry);
            if (!stale) {
                removeActive(entry.digest(), current);
            } else {
                digestsById.remove(entry.sessionId(), entry.digest());
                removeUserIndex(entry.username(), entry.sessionId());
            }
        }
        cleanupObserver.accept(examined);
    }

    private void removeActive(TokenDigest digest, Session session) {
        if (!sessions.remove(digest, session)) {
            return;
        }
        digestsById.remove(session.id(), digest);
        ExpiryEntry expiryEntry = expiryById.remove(session.id());
        if (expiryEntry != null) {
            expiryQueue.remove(expiryEntry);
        }
        removeUserIndex(session.username(), session.id());
    }

    private void removeById(UUID sessionId) {
        TokenDigest digest = digestsById.get(sessionId);
        Session session = digest == null ? null : sessions.get(digest);
        if (session != null && session.id().equals(sessionId)) {
            removeActive(digest, session);
            return;
        }
        digestsById.remove(sessionId);
        ExpiryEntry expiryEntry = expiryById.remove(sessionId);
        if (expiryEntry != null) {
            expiryQueue.remove(expiryEntry);
            removeUserIndex(expiryEntry.username(), sessionId);
        }
    }

    private void removeUserIndex(String username, UUID sessionId) {
        ArrayDeque<UUID> userSessions = sessionsByUser.get(username);
        if (userSessions != null) {
            userSessions.remove(sessionId);
            if (userSessions.isEmpty()) {
                sessionsByUser.remove(username);
            }
        }
    }

    private static boolean isTokenFormat(String rawToken) {
        if (rawToken == null || rawToken.length() != TOKEN_LENGTH) {
            return false;
        }
        for (int index = 0; index < rawToken.length(); index++) {
            char character = rawToken.charAt(index);
            boolean accepted = character >= 'A' && character <= 'Z'
                || character >= 'a' && character <= 'z'
                || character >= '0' && character <= '9'
                || character == '-'
                || character == '_';
            if (!accepted) {
                return false;
            }
        }
        return true;
    }

    private void initializeInjectedSessions() {
        if (sessions.isEmpty()) {
            return;
        }
        List<Map.Entry<TokenDigest, Session>> entries = new ArrayList<>(sessions.entrySet());
        entries.sort(Comparator.comparingLong(entry -> entry.getValue().sequence()));
        Set<Long> seenSequences = new HashSet<>();
        for (Map.Entry<TokenDigest, Session> entry : entries) {
            TokenDigest digest = Objects.requireNonNull(entry.getKey(), "session digest");
            Session session = Objects.requireNonNull(entry.getValue(), "session");
            if (!seenSequences.add(session.sequence())
                || digestsById.put(session.id(), digest) != null) {
                throw new IllegalArgumentException("Invalid injected session state");
            }
            sessionsByUser.computeIfAbsent(
                session.username(),
                ignored -> new ArrayDeque<>()
            ).addLast(session.id());
            ExpiryEntry expiryEntry = new ExpiryEntry(
                digest,
                session.id(),
                session.username(),
                session.expiresAt(),
                session.sequence()
            );
            if (expiryById.put(session.id(), expiryEntry) != null) {
                throw new IllegalArgumentException("Invalid injected session state");
            }
            expiryQueue.add(expiryEntry);
        }
        long highestSequence = entries.get(entries.size() - 1).getValue().sequence();
        nextSequence = highestSequence == Long.MAX_VALUE
            ? Long.MAX_VALUE
            : highestSequence + 1;
    }

    private static IllegalStateException issueFailure() {
        return new IllegalStateException(ISSUE_FAILURE);
    }

    private record ExpiryEntry(
        TokenDigest digest,
        UUID sessionId,
        String username,
        Instant expiresAt,
        long sequence
    ) {
    }
}

@FunctionalInterface
interface TokenBytesGenerator {
    void nextBytes(byte[] target);
}

final class TokenDigest {
    private static final String SHA_256 = "SHA-256";

    private final byte[] bytes;
    private final int hashCode;

    private TokenDigest(byte[] bytes) {
        if (bytes.length != 32) {
            throw new IllegalArgumentException("Invalid token digest");
        }
        this.bytes = Arrays.copyOf(bytes, bytes.length);
        this.hashCode = Arrays.hashCode(this.bytes);
    }

    static TokenDigest sha256(String rawToken) {
        return fromBytes(newDigest().digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    }

    static TokenDigest fromBytes(byte[] bytes) {
        return new TokenDigest(Objects.requireNonNull(bytes, "bytes"));
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other
            || other instanceof TokenDigest digest
            && MessageDigest.isEqual(bytes, digest.bytes);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return "TokenDigest[redacted]";
    }
}
