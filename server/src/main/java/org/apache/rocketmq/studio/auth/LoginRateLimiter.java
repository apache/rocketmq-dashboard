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

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * In-memory brute-force protection for the Studio login endpoint.
 *
 * <p>Failed logins are counted per normalized username. The exact tracker has a strict size
 * bound. When that bound is occupied, previously unseen usernames use a second fixed-size set
 * of hash buckets instead of failing open or growing memory. Collisions can share a lock, and a
 * lock the overflow tracker earned outlives the saturation that created it: all three paths that
 * free an exact slot drop overflow state only when no lock in it is still in force. Rate limiting
 * therefore cannot be disabled by freeing a slot, and the trade-off is that a colliding username
 * stays rejected for the remainder of a lock it shares.</p>
 */
@Slf4j
@Component
public class LoginRateLimiter {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(5);
    static final Duration LOCK_DURATION = Duration.ofMinutes(5);
    static final int MAX_TRACKED_USERNAMES = 10_000;
    static final int OVERFLOW_BUCKET_COUNT = 1_024;

    private final Map<String, AttemptState> attempts = new HashMap<>();
    private final AttemptState[] overflowAttempts;
    private final Clock clock;
    private final int maxTrackedUsernames;
    private final int overflowBucketMask;
    private final int overflowHashSeed;

    private long nextExactExpiryMillis = Long.MAX_VALUE;

    public LoginRateLimiter() {
        this(Clock.systemUTC());
    }

    LoginRateLimiter(Clock clock) {
        this(clock, MAX_TRACKED_USERNAMES);
    }

    LoginRateLimiter(Clock clock, int maxTrackedUsernames) {
        this(clock, maxTrackedUsernames, OVERFLOW_BUCKET_COUNT,
                ThreadLocalRandom.current().nextInt());
    }

    LoginRateLimiter(Clock clock, int maxTrackedUsernames,
                     int overflowBucketCount, int overflowHashSeed) {
        if (maxTrackedUsernames <= 0) {
            throw new IllegalArgumentException("maxTrackedUsernames must be positive");
        }
        if (overflowBucketCount <= 0
                || (overflowBucketCount & (overflowBucketCount - 1)) != 0) {
            throw new IllegalArgumentException("overflowBucketCount must be a power of two");
        }
        this.clock = clock;
        this.maxTrackedUsernames = maxTrackedUsernames;
        this.overflowAttempts = new AttemptState[overflowBucketCount];
        this.overflowBucketMask = overflowBucketCount - 1;
        this.overflowHashSeed = overflowHashSeed;
    }

    /**
     * Rejects the attempt with HTTP 429 while its exact or overflow state is locked.
     */
    public synchronized void checkAllowed(String username) {
        String key = key(username);
        long now = clock.millis();
        AttemptState exact = activeExactState(key, now);
        if (exact != null) {
            rejectIfLocked(exact, now);
            return;
        }

        reclaimExpiredExactAttemptsIfDue(now);
        if (attempts.size() < maxTrackedUsernames) {
            return;
        }

        int bucket = overflowBucket(key);
        AttemptState overflow = activeOverflowState(bucket, now);
        if (overflow != null) {
            rejectIfLocked(overflow, now);
        }
    }

    public synchronized void recordFailure(String username) {
        String key = key(username);
        long now = clock.millis();
        AttemptState exact = activeExactState(key, now);
        if (exact != null) {
            FailureUpdate update = incrementFailure(exact, now);
            attempts.put(key, update.state());
            noteExactExpiry(update.state());
            logLock(username, update, false);
            return;
        }

        reclaimExpiredExactAttemptsIfDue(now);
        if (attempts.size() < maxTrackedUsernames) {
            FailureUpdate update = incrementFailure(null, now);
            attempts.put(key, update.state());
            noteExactExpiry(update.state());
            logLock(username, update, false);
            return;
        }

        int bucket = overflowBucket(key);
        AttemptState overflow = activeOverflowState(bucket, now);
        FailureUpdate update = incrementFailure(overflow, now);
        overflowAttempts[bucket] = update.state();
        logLock(username, update, true);
    }

    public synchronized void recordSuccess(String username) {
        String key = key(username);
        long now = clock.millis();
        if (attempts.remove(key) != null) {
            // The caller's own exact slot is free again, so overflow leftovers from the saturated
            // period must not affect a later saturation episode. A lock that is still in force is
            // not a leftover: it belongs to the username it rejects, not to this caller.
            clearStaleOverflowAttempts(now);
            return;
        }
        overflowAttempts[overflowBucket(key)] = null;
    }

    synchronized int trackedUsernameCount() {
        return attempts.size();
    }

    synchronized int activeOverflowBucketCount() {
        int active = 0;
        long now = clock.millis();
        for (int index = 0; index < overflowAttempts.length; index++) {
            if (activeOverflowState(index, now) != null) {
                active++;
            }
        }
        return active;
    }

    private AttemptState activeExactState(String key, long now) {
        AttemptState state = attempts.get(key);
        if (state == null || !state.expiredAt(now)) {
            return state;
        }
        attempts.remove(key);
        clearStaleOverflowAttempts(now);
        return null;
    }

    private AttemptState activeOverflowState(int bucket, long now) {
        AttemptState state = overflowAttempts[bucket];
        if (state == null || !state.expiredAt(now)) {
            return state;
        }
        overflowAttempts[bucket] = null;
        return null;
    }

    private void reclaimExpiredExactAttemptsIfDue(long now) {
        if (now < nextExactExpiryMillis) {
            return;
        }
        boolean removed = attempts.entrySet().removeIf(entry -> entry.getValue().expiredAt(now));
        nextExactExpiryMillis = attempts.values().stream()
                .mapToLong(AttemptState::expiresAtMillis)
                .min()
                .orElse(Long.MAX_VALUE);
        if (removed) {
            clearStaleOverflowAttempts(now);
        }
    }

    private FailureUpdate incrementFailure(AttemptState current, long now) {
        if (current != null && current.lockedAt(now)) {
            // A request that passed checkAllowed before another request established this lock
            // may finish later. Its failure must not shorten or clear the active lock.
            return new FailureUpdate(current, false);
        }
        AttemptState base = current == null || current.expiredAt(now)
                ? new AttemptState(now, 0, 0)
                : current;
        int failures = base.failureCount() + 1;
        if (failures >= MAX_FAILED_ATTEMPTS) {
            return new FailureUpdate(
                    new AttemptState(now, 0, now + LOCK_DURATION.toMillis()), true);
        }
        return new FailureUpdate(
                new AttemptState(base.windowStartMillis(), failures, 0), false);
    }

    private void rejectIfLocked(AttemptState state, long now) {
        if (!state.lockedAt(now)) {
            return;
        }
        long remainingSeconds = (state.lockedUntilMillis() - now + 999) / 1000;
        throw new BusinessException(429, "Too many failed login attempts; try again in "
                + remainingSeconds + " seconds");
    }

    private void logLock(String username, FailureUpdate update, boolean overflow) {
        if (!update.newlyLocked()) {
            return;
        }
        if (overflow) {
            log.warn("Locked a saturated login-rate bucket after {} failed attempts within {} minutes",
                    MAX_FAILED_ATTEMPTS, FAILURE_WINDOW.toMinutes());
        } else {
            log.warn("Locked login for user {} after {} failed attempts within {} minutes",
                    username, MAX_FAILED_ATTEMPTS, FAILURE_WINDOW.toMinutes());
        }
    }

    private void noteExactExpiry(AttemptState state) {
        nextExactExpiryMillis = Math.min(nextExactExpiryMillis, state.expiresAtMillis());
    }

    private void clearOverflowAttempts() {
        Arrays.fill(overflowAttempts, null);
    }

    /**
     * Drops the overflow state of a saturated episode that is over, but never a lock that is still in
     * force. All three callers free an exact slot - the two expiry paths on unauthenticated login
     * requests, and {@link #recordSuccess} for the username that just authenticated - and that slot
     * can be taken again immediately by anybody, which puts the overflow tracker back in charge of
     * the usernames it is protecting. A successful login is evidence about its own username only: it
     * says nothing about the failures behind a bucket that already earned a rejection, so cancelling a
     * live lock here would release another username's lock with most of {@link #LOCK_DURATION} still
     * to run. State that carries no lock has nothing to protect and is still dropped, so an episode's
     * leftovers cannot outlive it.
     */
    private void clearStaleOverflowAttempts(long now) {
        for (int index = 0; index < overflowAttempts.length; index++) {
            AttemptState state = activeOverflowState(index, now);
            if (state != null && state.lockedAt(now)) {
                return;
            }
        }
        clearOverflowAttempts();
    }

    private int overflowBucket(String key) {
        long hash = 0xcbf29ce484222325L ^ Integer.toUnsignedLong(overflowHashSeed);
        for (int index = 0; index < key.length(); index++) {
            hash ^= key.charAt(index);
            hash *= 0x100000001b3L;
        }
        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdL;
        hash ^= hash >>> 33;
        return ((int) hash) & overflowBucketMask;
    }

    private String key(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private record FailureUpdate(AttemptState state, boolean newlyLocked) {
    }

    private record AttemptState(long windowStartMillis, int failureCount, long lockedUntilMillis) {

        boolean lockedAt(long now) {
            return lockedUntilMillis > now;
        }

        boolean expiredAt(long now) {
            return lockedUntilMillis != 0
                    ? lockedUntilMillis <= now
                    : now - windowStartMillis >= FAILURE_WINDOW.toMillis();
        }

        long expiresAtMillis() {
            return lockedUntilMillis != 0
                    ? lockedUntilMillis
                    : windowStartMillis + FAILURE_WINDOW.toMillis();
        }
    }
}
