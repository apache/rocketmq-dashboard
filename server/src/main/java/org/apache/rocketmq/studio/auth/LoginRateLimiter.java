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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory brute-force protection for the Studio login endpoint.
 *
 * <p>Failed logins are counted per username. After {@link #MAX_FAILED_ATTEMPTS} failures within
 * {@link #FAILURE_WINDOW}, further attempts for that username are rejected for
 * {@link #LOCK_DURATION}. A successful login resets the counter. The state is deliberately kept
 * in memory only: it throttles online guessing attacks and resets on restart.</p>
 */
@Slf4j
@Component
public class LoginRateLimiter {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final int MAX_TRACKED_USERNAMES = 10_000;
    static final Duration FAILURE_WINDOW = Duration.ofMinutes(5);
    static final Duration LOCK_DURATION = Duration.ofMinutes(5);
    private static final Duration CAPACITY_SWEEP_INTERVAL = Duration.ofSeconds(30);

    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int maxTrackedUsernames;
    private long nextCapacitySweepMillis;

    public LoginRateLimiter() {
        this(Clock.systemUTC(), MAX_TRACKED_USERNAMES);
    }

    LoginRateLimiter(Clock clock) {
        this(clock, MAX_TRACKED_USERNAMES);
    }

    LoginRateLimiter(Clock clock, int maxTrackedUsernames) {
        if (maxTrackedUsernames <= 0) {
            throw new IllegalArgumentException("maxTrackedUsernames must be positive");
        }
        this.clock = clock;
        this.maxTrackedUsernames = maxTrackedUsernames;
    }

    /**
     * Rejects the attempt with HTTP 429 while the username is locked out.
     */
    public void checkAllowed(String username) {
        String key = key(username);
        AttemptState state = attempts.get(key);
        if (state == null || state.lockedUntilMillis() == 0) {
            return;
        }
        long now = clock.millis();
        if (state.lockedUntilMillis() > now) {
            long remainingSeconds = (state.lockedUntilMillis() - now + 999) / 1000;
            throw new BusinessException(429, "Too many failed login attempts; try again in "
                    + remainingSeconds + " seconds");
        }
        attempts.remove(key, state);
    }

    public void recordFailure(String username) {
        String key = key(username);
        long now = clock.millis();
        AttemptState state;
        synchronized (attempts) {
            if (!attempts.containsKey(key)) {
                ensureCapacity(now);
            }
            state = attempts.compute(key, (ignored, current) -> {
                AttemptState base = current;
                if (base == null || base.lockedUntilMillis() != 0
                        || now - base.windowStartMillis() >= FAILURE_WINDOW.toMillis()) {
                    base = new AttemptState(now, 0, 0);
                }
                int failures = base.failureCount() + 1;
                if (failures >= MAX_FAILED_ATTEMPTS) {
                    return new AttemptState(now, 0, now + LOCK_DURATION.toMillis());
                }
                return new AttemptState(base.windowStartMillis(), failures, 0);
            });
        }
        if (state != null && state.lockedUntilMillis() != 0) {
            log.warn("Locked login for user {} after {} failed attempts within {} minutes",
                    username, MAX_FAILED_ATTEMPTS, FAILURE_WINDOW.toMinutes());
        }
    }

    public void recordSuccess(String username) {
        attempts.remove(key(username));
    }

    private String key(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private void ensureCapacity(long now) {
        if (attempts.size() < maxTrackedUsernames) {
            return;
        }
        if (now >= nextCapacitySweepMillis) {
            attempts.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
            nextCapacitySweepMillis = now + CAPACITY_SWEEP_INTERVAL.toMillis();
        }
        if (attempts.size() >= maxTrackedUsernames) {
            throw new BusinessException(429, "Too many login attempts are being tracked; try again later");
        }
    }

    private boolean isExpired(AttemptState state, long now) {
        if (state.lockedUntilMillis() != 0) {
            return state.lockedUntilMillis() <= now;
        }
        return now - state.windowStartMillis() >= FAILURE_WINDOW.toMillis();
    }

    private record AttemptState(long windowStartMillis, int failureCount, long lockedUntilMillis) {
    }
}
