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
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;

public final class LoginAttemptLimiter {
    private static final int USER_FAILURE_LIMIT = 5;
    private static final int PREFIX_FAILURE_LIMIT = 20;
    private static final int CLEANUP_LIMIT = 64;
    private static final long WINDOW_MILLIS = 60_000L;
    private static final Pattern VALID_USERNAME = Pattern.compile("[A-Za-z0-9._@-]{1,128}");
    private static final Pattern SHA256_DIGEST = Pattern.compile("[0-9a-f]{64}");

    private final Clock clock;
    private final int userMapLimit;
    private final int prefixMapLimit;
    private final IntConsumer cleanupObserver;
    private final BiConsumer<Integer, Integer> sizeObserver;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Tracker> userTrackers;
    private final Map<String, Tracker> prefixTrackers;
    private final Semaphore passwordPermits;
    private long lastEffectiveNow = Long.MIN_VALUE;

    public LoginAttemptLimiter(StudioSecurityProperties properties, Clock clock) {
        this(clock, properties.maxUsers(), properties.maxUsers(),
            properties.maxConcurrentPasswordChecks(), ignored -> { }, (left, right) -> { });
    }

    LoginAttemptLimiter(
        Clock clock,
        int userMapLimit,
        int prefixMapLimit,
        int semaphoreSize,
        IntConsumer cleanupObserver,
        BiConsumer<Integer, Integer> sizeObserver
    ) {
        this.clock = Objects.requireNonNull(clock);
        this.userMapLimit = userMapLimit;
        this.prefixMapLimit = prefixMapLimit;
        this.cleanupObserver = Objects.requireNonNull(cleanupObserver);
        this.sizeObserver = Objects.requireNonNull(sizeObserver);
        this.userTrackers = new LinkedHashMap<>(16, 0.75f, true);
        this.prefixTrackers = new LinkedHashMap<>(16, 0.75f, true);
        this.passwordPermits = new Semaphore(semaphoreSize, true);
    }

    public Decision beforeAttempt(String username, String remoteAddress) {
        Key key = key(username, remoteAddress);
        lock.lock();
        try {
            long now = effectiveNow();
            int examined = cleanupExpired(now);
            Tracker userTracker = userTrackers.get(key.usernamePrefixDigest());
            Tracker prefixTracker = prefixTrackers.get(key.prefixDigest());
            long userLock = activeLock(userTracker, now);
            long prefixLock = activeLock(prefixTracker, now);
            observe(examined);
            long lockUntil = Math.max(userLock, prefixLock);
            if (lockUntil == Long.MIN_VALUE) {
                return new Decision(true, 0, key);
            }
            return new Decision(false, retryAfterSeconds(lockUntil, now), key);
        } finally {
            lock.unlock();
        }
    }

    public Permit acquirePasswordPermit() {
        if (!passwordPermits.tryAcquire()) {
            return new Permit(null);
        }
        return new Permit(passwordPermits);
    }

    public void recordFailure(Key key) {
        Objects.requireNonNull(key);
        lock.lock();
        try {
            long now = effectiveNow();
            int examined = cleanupExpired(now);
            Tracker userTracker = userTrackers.computeIfAbsent(
                key.usernamePrefixDigest(), ignored -> new Tracker());
            Tracker prefixTracker = prefixTrackers.computeIfAbsent(
                key.prefixDigest(), ignored -> new Tracker());
            addFailure(userTracker, USER_FAILURE_LIMIT, now);
            addFailure(prefixTracker, PREFIX_FAILURE_LIMIT, now);
            evictEldest(userTrackers, userMapLimit);
            evictEldest(prefixTrackers, prefixMapLimit);
            observe(examined);
        } finally {
            lock.unlock();
        }
    }

    public void recordSuccess(Key key) {
        Objects.requireNonNull(key);
        lock.lock();
        try {
            long now = effectiveNow();
            int examined = cleanupExpired(now);
            userTrackers.remove(key.usernamePrefixDigest());
            observe(examined);
        } finally {
            lock.unlock();
        }
    }

    private static long activeLock(Tracker tracker, long now) {
        if (tracker == null || tracker.lockUntilMillis <= now) {
            return Long.MIN_VALUE;
        }
        return tracker.lockUntilMillis;
    }

    private static void addFailure(Tracker tracker, int limit, long now) {
        pruneFailures(tracker, now);
        while (tracker.failures.size() >= limit) {
            tracker.failures.removeFirst();
        }
        tracker.failures.addLast(now);
        if (tracker.failures.size() >= limit) {
            tracker.lockUntilMillis = Math.max(
                tracker.lockUntilMillis,
                saturatedAdd(now, WINDOW_MILLIS)
            );
        }
    }

    private static void pruneFailures(Tracker tracker, long now) {
        if (now < Long.MIN_VALUE + WINDOW_MILLIS) {
            return;
        }
        long cutoff = saturatedSubtract(now, WINDOW_MILLIS);
        while (!tracker.failures.isEmpty() && tracker.failures.peekFirst() <= cutoff) {
            tracker.failures.removeFirst();
        }
    }

    private static int retryAfterSeconds(long lockUntilMillis, long now) {
        long remainingMillis = saturatedSubtract(lockUntilMillis, now);
        long roundedSeconds = saturatedAdd(remainingMillis, 999L) / 1_000L;
        return (int) Math.min(Integer.MAX_VALUE, roundedSeconds);
    }

    private static Key key(String username, String remoteAddress) {
        String normalizedUsername = username != null && VALID_USERNAME.matcher(username).matches()
            ? "valid:" + username.length() + ":" + username : "invalid:";
        String prefix = addressPrefix(remoteAddress);
        String prefixDigest = sha256("prefix:" + prefix.length() + ":" + prefix);
        return new Key(
            sha256("username:" + normalizedUsername.length() + ":" + normalizedUsername
                + ":prefix:" + prefix.length() + ":" + prefix),
            prefixDigest
        );
    }

    private static String addressPrefix(String address) {
        if (address == null || address.length() > 45) {
            return "unknown";
        }
        int[] ipv4 = parseIpv4(address);
        if (ipv4 != null) {
            return ipv4Prefix(ipv4);
        }
        int[] ipv6 = parseIpv6(address);
        if (ipv6 != null) {
            if (isIpv4Mapped(ipv6)) {
                int[] mappedIpv4 = {
                    ipv6[6] >>> 8,
                    ipv6[6] & 0xff,
                    ipv6[7] >>> 8,
                    ipv6[7] & 0xff
                };
                return ipv4Prefix(mappedIpv4);
            }
            return String.format("v6:%04x:%04x:%04x:%04x",
                ipv6[0], ipv6[1], ipv6[2], ipv6[3]);
        }
        return "unknown";
    }

    private static String ipv4Prefix(int[] ipv4) {
        return "v4:" + ipv4[0] + "." + ipv4[1] + "." + ipv4[2];
    }

    private static boolean isIpv4Mapped(int[] ipv6) {
        return ipv6[0] == 0
            && ipv6[1] == 0
            && ipv6[2] == 0
            && ipv6[3] == 0
            && ipv6[4] == 0
            && ipv6[5] == 0xffff;
    }

    private static int[] parseIpv4(String address) {
        if (address == null) {
            return null;
        }
        String[] parts = address.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        int[] result = new int[4];
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty() || part.length() > 3) {
                return null;
            }
            int value = 0;
            for (int character = 0; character < part.length(); character++) {
                char digit = part.charAt(character);
                if (digit < '0' || digit > '9') {
                    return null;
                }
                value = value * 10 + digit - '0';
            }
            if (value > 255) {
                return null;
            }
            result[index] = value;
        }
        return result;
    }

    private static int[] parseIpv6(String address) {
        if (address == null || !address.contains(":") || address.contains("%")) {
            return null;
        }
        int compression = address.indexOf("::");
        if (compression >= 0 && address.indexOf("::", compression + 2) >= 0) {
            return null;
        }
        List<Integer> left;
        List<Integer> right;
        if (compression >= 0) {
            left = parseIpv6Part(address.substring(0, compression), false);
            right = parseIpv6Part(address.substring(compression + 2), true);
            if (left == null || right == null || left.size() + right.size() >= 8) {
                return null;
            }
        } else {
            left = parseIpv6Part(address, true);
            right = List.of();
            if (left == null || left.size() != 8) {
                return null;
            }
        }
        int[] result = new int[8];
        int position = 0;
        for (int value : left) {
            result[position++] = value;
        }
        position = 8 - right.size();
        for (int value : right) {
            result[position++] = value;
        }
        return result;
    }

    private static List<Integer> parseIpv6Part(String part, boolean addressTail) {
        if (part.isEmpty()) {
            return new ArrayList<>();
        }
        String[] tokens = part.split(":", -1);
        List<Integer> result = new ArrayList<>(tokens.length);
        for (int index = 0; index < tokens.length; index++) {
            String token = tokens[index];
            if (token.isEmpty()) {
                return null;
            }
            if (token.contains(".")) {
                if (!addressTail || index != tokens.length - 1) {
                    return null;
                }
                int[] ipv4 = parseIpv4(token);
                if (ipv4 == null) {
                    return null;
                }
                result.add(ipv4[0] << 8 | ipv4[1]);
                result.add(ipv4[2] << 8 | ipv4[3]);
            } else {
                if (token.length() > 4 || !isAsciiHex(token)) {
                    return null;
                }
                result.add(Integer.parseInt(token, 16));
            }
        }
        return result;
    }

    private static boolean isAsciiHex(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean decimal = character >= '0' && character <= '9';
            boolean lowercase = character >= 'a' && character <= 'f';
            boolean uppercase = character >= 'A' && character <= 'F';
            if (!decimal && !lowercase && !uppercase) {
                return false;
            }
        }
        return true;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void evictEldest(Map<String, Tracker> map, int limit) {
        while (map.size() > limit) {
            map.remove(map.keySet().iterator().next());
        }
    }

    private long effectiveNow() {
        lastEffectiveNow = Math.max(lastEffectiveNow, clock.millis());
        return lastEffectiveNow;
    }

    private static long saturatedAdd(long value, long addend) {
        if (addend > 0 && value > Long.MAX_VALUE - addend) {
            return Long.MAX_VALUE;
        }
        if (addend < 0 && value < Long.MIN_VALUE - addend) {
            return Long.MIN_VALUE;
        }
        return value + addend;
    }

    private static long saturatedSubtract(long value, long subtrahend) {
        if (subtrahend > 0 && value < Long.MIN_VALUE + subtrahend) {
            return Long.MIN_VALUE;
        }
        if (subtrahend < 0 && value > Long.MAX_VALUE + subtrahend) {
            return Long.MAX_VALUE;
        }
        return value - subtrahend;
    }

    private int cleanupExpired(long now) {
        int examined = cleanupMap(userTrackers, now, CLEANUP_LIMIT);
        return examined + cleanupMap(prefixTrackers, now, CLEANUP_LIMIT - examined);
    }

    private static int cleanupMap(Map<String, Tracker> map, long now, int budget) {
        int examined = 0;
        Iterator<Map.Entry<String, Tracker>> iterator = map.entrySet().iterator();
        while (iterator.hasNext() && examined < budget) {
            Tracker tracker = iterator.next().getValue();
            examined++;
            pruneFailures(tracker, now);
            if (tracker.failures.isEmpty() && tracker.lockUntilMillis <= now) {
                iterator.remove();
            }
        }
        return examined;
    }

    private void observe(int examined) {
        cleanupObserver.accept(examined);
        sizeObserver.accept(userTrackers.size(), prefixTrackers.size());
    }

    public record Decision(boolean allowed, int retryAfterSeconds, Key key) {
        public Decision {
            Objects.requireNonNull(key);
        }
    }

    public static final class Key {
        private final String usernamePrefixDigest;
        private final String prefixDigest;

        private Key(String usernamePrefixDigest, String prefixDigest) {
            this.usernamePrefixDigest = Objects.requireNonNull(usernamePrefixDigest);
            this.prefixDigest = Objects.requireNonNull(prefixDigest);
            if (!SHA256_DIGEST.matcher(usernamePrefixDigest).matches()
                || !SHA256_DIGEST.matcher(prefixDigest).matches()) {
                throw new IllegalArgumentException("key values must be SHA-256 digests");
            }
        }

        public String usernamePrefixDigest() {
            return usernamePrefixDigest;
        }

        public String prefixDigest() {
            return prefixDigest;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return usernamePrefixDigest.equals(key.usernamePrefixDigest)
                && prefixDigest.equals(key.prefixDigest);
        }

        @Override
        public int hashCode() {
            return Objects.hash(usernamePrefixDigest, prefixDigest);
        }

        @Override
        public String toString() {
            return "Key[usernamePrefixDigest=" + usernamePrefixDigest
                + ", prefixDigest=" + prefixDigest + "]";
        }
    }

    public static final class Permit implements AutoCloseable {
        private final Semaphore semaphore;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Permit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        public boolean acquired() {
            return semaphore != null;
        }

        @Override
        public void close() {
            if (semaphore != null && closed.compareAndSet(false, true)) {
                semaphore.release();
            }
        }
    }

    private static final class Tracker {
        private final ArrayDeque<Long> failures = new ArrayDeque<>();
        private long lockUntilMillis = Long.MIN_VALUE;
    }
}
