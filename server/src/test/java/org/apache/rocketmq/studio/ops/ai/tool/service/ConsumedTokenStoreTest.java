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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConsumedTokenStoreTest {

    private final ConsumedTokenStore store = new ConsumedTokenStore();

    @Test
    void firstConsumerWinsAndRepeatsAreRefused() {
        assertThat(store.consume("0000000000000001", 2_000L, 1_000L)).isTrue();
        assertThat(store.consume("0000000000000001", 2_000L, 1_000L)).isFalse();
        assertThat(store.consume("0000000000000001", 5_000L, 1_000L)).isFalse();
        assertThat(store.consume("0000000000000002", 2_000L, 1_000L)).isTrue();
        assertThat(store.size()).isEqualTo(2);
    }

    @Test
    void keepsEntriesWithinTtlSoReplaysStayRejected() {
        // A consumed-but-not-yet-expired id must still refuse replays even after many
        // other consumptions happened; purging only drops entries at or past expiry.
        assertThat(store.consume("replayed", 1_600L, 1_000L)).isTrue();
        for (int entry = 0; entry < 5_000; entry++) {
            store.consume(String.format("filler-%05d", entry), 9_000L, 1_100L);
        }
        assertThat(store.consume("replayed", 1_600L, 1_500L)).isFalse();
        // Once the original token itself is expired, its entry is purgeable; rejection of a
        // later replay then comes from the expiry check in ToolTokenService, not from this store.
        for (int entry = 0; entry < 5_000; entry++) {
            store.consume(String.format("purge-%05d", entry), 9_000L, 1_700L);
        }
        assertThat(store.consume("replayed", 1_600L, 1_700L)).isTrue();
        assertThat(store.size()).isLessThanOrEqualTo(10_001);
    }

    @Test
    void concurrentConsumersOfOneIdElectExactlyOneWinner() throws Exception {
        int callers = 16;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            CyclicBarrier rendezvous = new CyclicBarrier(callers);
            AtomicInteger winners = new AtomicInteger();
            List<Future<?>> futures = new ArrayList<>();
            for (int caller = 0; caller < callers; caller++) {
                futures.add(pool.submit(() -> {
                    rendezvous.await(10, TimeUnit.SECONDS);
                    if (store.consume("contended", 2_000L, 1_000L)) {
                        winners.incrementAndGet();
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
            assertThat(winners).hasValue(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
