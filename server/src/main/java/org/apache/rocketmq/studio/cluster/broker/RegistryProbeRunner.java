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
package org.apache.rocketmq.studio.cluster.broker;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.cluster.nameserver.NameserverRegistryVO;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Bounded, closeable executor that runs NameServer registry probes concurrently.
 *
 * <p>Replaces an unbounded cached thread pool so that probes blocked on an unreachable
 * NameServer cannot accumulate threads without limit. Each probe is cancelled (its worker
 * interrupted) when the deadline is reached so the pool thread is released, and a saturated
 * queue degrades the offending entry to unavailable instead of growing the pool.</p>
 */
@Slf4j
class RegistryProbeRunner implements AutoCloseable {

    private static final long KEEP_ALIVE_SECONDS = 60L;

    private final ThreadPoolExecutor executor;
    private final long timeoutMillis;
    private final int maxConcurrency;

    RegistryProbeRunner(int maxConcurrency, int queueCapacity, long timeoutMillis) {
        this.maxConcurrency = maxConcurrency;
        this.timeoutMillis = timeoutMillis;
        this.executor = new ThreadPoolExecutor(
                maxConcurrency,
                maxConcurrency,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, "nameserver-registry-probe");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        // Let idle workers (including core) time out so a quiet registry holds no threads.
        this.executor.allowCoreThreadTimeOut(true);
    }

    /** Probes a single registry entry; may block on the underlying admin client. */
    interface ProbeFunction {
        List<ClusterVO> probe(NameserverRegistryVO entry) throws Exception;
    }

    /**
     * Probes every entry concurrently, bounded by the pool. A rejected (saturated), timed-out
     * or failing entry contributes an empty result without affecting the others.
     */
    List<ClusterVO> probeAll(List<NameserverRegistryVO> entries, ProbeFunction function) {
        return entries.stream()
                .map(entry -> probeOne(entry, function))
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList();
    }

    private CompletableFuture<List<ClusterVO>> probeOne(NameserverRegistryVO entry, ProbeFunction function) {
        CompletableFuture<List<ClusterVO>> result = new CompletableFuture<>();
        Future<?> task;
        try {
            task = executor.submit(() -> {
                try {
                    result.complete(function.probe(entry));
                } catch (Exception exception) {
                    result.completeExceptionally(exception);
                }
            });
        } catch (RejectedExecutionException rejected) {
            // Queue saturated: degrade this entry to unavailable instead of growing the pool.
            log.warn("NameServer registry probe rejected for {} ({}): executor saturated",
                    entry.getName(), entry.getNamesrvAddr());
            return CompletableFuture.completedFuture(List.of());
        }
        return result
                .orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .whenComplete((value, error) -> {
                    if (error instanceof TimeoutException) {
                        // Interrupt the worker so the blocked probe releases its pool thread.
                        task.cancel(true);
                    }
                })
                .exceptionally(error -> {
                    log.warn("NameServer registry probe failed for {} ({}): {}",
                            entry.getName(), entry.getNamesrvAddr(), rootMessage(error));
                    return List.of();
                });
    }

    int activeCount() {
        return executor.getActiveCount();
    }

    int poolSize() {
        return executor.getPoolSize();
    }

    int queuedTaskCount() {
        return executor.getQueue().size();
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }
}
