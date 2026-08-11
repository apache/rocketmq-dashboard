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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

/**
 * Bounded-parallel mapping for the N+1 call pattern found across the providers
 * (per-topic, per-group and per-instance admin/cloud calls that were issued
 * serially). A shared daemon pool keeps the parallelism bounded so a busy page
 * cannot exhaust the JVM threads, and a per-call semaphore limits how many
 * concurrent remote requests a single fan-out issues.
 *
 * <p>Failures propagate as {@link java.util.concurrent.CompletionException}
 * wrapped in the thrown exception from {@link #map}; callers that must tolerate
 * per-item failures should map exceptions inside the {@code mapper} itself.
 */
public final class ParallelOps {

    private static final int DEFAULT_MAX_CONCURRENCY = 8;

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
            Math.max(4, Math.min(16, Runtime.getRuntime().availableProcessors() * 2)),
            runnable -> {
                Thread thread = new Thread(runnable, "studio-parallel");
                thread.setDaemon(true);
                return thread;
            });

    private ParallelOps() {
    }

    /**
     * Maps every item in {@code items} through {@code mapper} in parallel with a bounded
     * concurrency, preserving the input order in the returned list.
     *
     * @param items   the input collection; empty or single-item inputs run inline
     * @param mapper  the per-item call, may throw (surfaced as CompletionException)
     * @param <T>     input element type
     * @param <R>     result element type
     * @return the mapped results in input order
     */
    public static <T, R> List<R> map(List<T> items, Function<T, R> mapper) {
        return map(items, mapper, DEFAULT_MAX_CONCURRENCY);
    }

    /**
     * @see #map(List, Function)
     */
    public static <T, R> List<R> map(List<T> items, Function<T, R> mapper, int maxConcurrency) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        if (items.size() == 1) {
            return List.of(mapper.apply(items.get(0)));
        }
        Semaphore gate = new Semaphore(Math.max(1, maxConcurrency));
        List<CompletableFuture<R>> futures = items.stream()
                .map(item -> CompletableFuture.supplyAsync(() -> {
                    try {
                        gate.acquire();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                    try {
                        return mapper.apply(item);
                    } finally {
                        gate.release();
                    }
                }, EXECUTOR))
                .toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }
}
