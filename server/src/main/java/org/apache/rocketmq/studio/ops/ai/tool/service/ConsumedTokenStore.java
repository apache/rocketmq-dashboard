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
package org.apache.rocketmq.studio.ops.ai.tool.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Records which confirmation token ids have already been consumed so a token admits at most
 * one non-idempotent mutation. The store lives in process memory to match the single-node
 * deployment model of the confirmation flow; it introduces no external dependency.
 *
 * <p>Entries are kept until the token itself expires: a replay inside the TTL is rejected as
 * already used, and a replay afterwards is rejected as expired by the token verification, so
 * dropping expired entries never widens the acceptance window. Purging is opportunistic to
 * keep the amortized cost of an apply at O(1).
 */
final class ConsumedTokenStore {

    /** Purge expired entries once the map grows past this bound; each entry is one id plus one long. */
    private static final int PURGE_THRESHOLD = 4096;

    private final ConcurrentMap<String, Long> consumedExpiries = new ConcurrentHashMap<>();

    /**
     * Atomically marks the token id as consumed.
     *
     * @return {@code true} when this call is the first consumer, {@code false} when the id was
     *         already consumed and the caller must be rejected
     */
    boolean consume(String tokenId, long expiresAtEpochSecond, long nowEpochSecond) {
        boolean firstConsumer = consumedExpiries.putIfAbsent(tokenId, expiresAtEpochSecond) == null;
        if (consumedExpiries.size() > PURGE_THRESHOLD) {
            consumedExpiries.values().removeIf(expiry -> expiry <= nowEpochSecond);
        }
        return firstConsumer;
    }

    int size() {
        return consumedExpiries.size();
    }
}
