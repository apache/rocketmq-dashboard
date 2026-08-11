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
package org.apache.rocketmq.studio.common.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Minimal thread-safe TTL cache for expensive, frequently-polled reads (dashboard
 * aggregates, instance counts). Values are cached for {@code ttlMillis}; after that a
 * concurrent caller reloads through {@link #get(Object, Supplier)}. Cache stampede is
 * tolerated: one reload per key per expiry window is accepted rather than adding
 * per-key locking, which is the right trade-off for short TTLs.
 *
 * @param <K> cache key type
 * @param <V> cached value type
 */
public final class TtlCache<K, V> {

    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final long ttlNanos;
    private final ConcurrentMap<K, Entry<V>> entries = new ConcurrentHashMap<>();

    public TtlCache(long ttlMillis) {
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("ttlMillis must be positive");
        }
        this.ttlNanos = ttlMillis * NANOS_PER_MILLI;
    }

    /**
     * Returns the cached value for {@code key} if fresh, otherwise loads it through
     * {@code loader}, stores it and returns it.
     */
    public V get(K key, Supplier<V> loader) {
        long now = System.nanoTime();
        Entry<V> entry = entries.get(key);
        if (entry != null && now - entry.createdNanos < ttlNanos) {
            return entry.value;
        }
        V value = loader.get();
        if (value != null) {
            entries.put(key, new Entry<>(value, now));
        }
        return value;
    }

    /**
     * Drops a single key (used when the underlying data is known to have changed).
     */
    public void invalidate(K key) {
        entries.remove(key);
    }

    private record Entry<V>(V value, long createdNanos) {
    }
}
