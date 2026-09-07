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
package org.apache.rocketmq.studio.instance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs instance resource-count lookups without exposing response objects to worker threads.
 *
 * <p>The executor and its queue are both bounded because vendor SDK calls may ignore thread
 * interruption while an HTTP request is in flight. Each batch has one absolute deadline. A
 * result is usable only when the worker completed it before that deadline; late results remain
 * detached and therefore cannot mutate a response that has already been returned.</p>
 */
final class InstanceResourceCountRunner implements AutoCloseable {

    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();

    private final ThreadPoolExecutor executor;
    private final long timeoutNanos;

    InstanceResourceCountRunner(int parallelism, int queueCapacity, long timeout, TimeUnit timeoutUnit) {
        if (parallelism <= 0) {
            throw new IllegalArgumentException("parallelism must be positive");
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        if (timeout <= 0) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        Objects.requireNonNull(timeoutUnit, "timeoutUnit");

        this.timeoutNanos = timeoutUnit.toNanos(timeout);
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable,
                    "instance-resource-counts-" + THREAD_SEQUENCE.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = new ThreadPoolExecutor(
                parallelism,
                parallelism,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    @FunctionalInterface
    interface CountFunction {
        ResourceCounts count(InstanceVO instance) throws Exception;
    }

    record ResourceCounts(int topicCount, int consumerGroupCount) {
    }

    enum OutcomeStatus {
        SUCCESS,
        FAILED,
        TIMED_OUT,
        REJECTED,
        INTERRUPTED
    }

    record CountOutcome(OutcomeStatus status, ResourceCounts counts, Throwable failure) {

        static CountOutcome success(ResourceCounts counts) {
            return new CountOutcome(OutcomeStatus.SUCCESS, counts, null);
        }

        static CountOutcome failed(Throwable failure) {
            return new CountOutcome(OutcomeStatus.FAILED, null, failure);
        }

        static CountOutcome unavailable(OutcomeStatus status) {
            return new CountOutcome(status, null, null);
        }

        boolean available() {
            return status == OutcomeStatus.SUCCESS;
        }
    }

    List<CountOutcome> countAll(List<InstanceVO> instances, CountFunction function) {
        Objects.requireNonNull(instances, "instances");
        Objects.requireNonNull(function, "function");
        if (instances.isEmpty()) {
            return List.of();
        }

        long startedNanos = System.nanoTime();
        List<SubmittedCount> submitted = submitAll(instances, function);
        List<CountOutcome> outcomes = new ArrayList<>(submitted.size());
        boolean interrupted = false;

        for (SubmittedCount count : submitted) {
            if (count.rejected()) {
                outcomes.add(CountOutcome.unavailable(OutcomeStatus.REJECTED));
                continue;
            }
            if (interrupted) {
                count.future().cancel(true);
                outcomes.add(CountOutcome.unavailable(OutcomeStatus.INTERRUPTED));
                continue;
            }

            try {
                CompletedCount completed = await(count.future(), startedNanos);
                if (elapsedNanos(startedNanos, completed.completedNanos()) > timeoutNanos) {
                    outcomes.add(CountOutcome.unavailable(OutcomeStatus.TIMED_OUT));
                } else if (completed.failure() != null) {
                    outcomes.add(CountOutcome.failed(completed.failure()));
                } else if (completed.counts() == null) {
                    outcomes.add(CountOutcome.failed(
                            new IllegalStateException("resource count provider returned null")));
                } else {
                    outcomes.add(CountOutcome.success(completed.counts()));
                }
            } catch (TimeoutException exception) {
                count.future().cancel(true);
                outcomes.add(CountOutcome.unavailable(OutcomeStatus.TIMED_OUT));
            } catch (InterruptedException exception) {
                count.future().cancel(true);
                interrupted = true;
                outcomes.add(CountOutcome.unavailable(OutcomeStatus.INTERRUPTED));
            } catch (CancellationException exception) {
                outcomes.add(CountOutcome.unavailable(OutcomeStatus.INTERRUPTED));
            } catch (ExecutionException exception) {
                outcomes.add(CountOutcome.failed(rootCause(exception)));
            }
        }

        cancelOutstanding(submitted);
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        // Future.cancel does not remove queued FutureTasks from a ThreadPoolExecutor queue.
        // Purging prevents timed-out requests from leaving a cancelled backlog behind.
        executor.purge();
        return List.copyOf(outcomes);
    }

    private List<SubmittedCount> submitAll(List<InstanceVO> instances, CountFunction function) {
        List<SubmittedCount> submitted = new ArrayList<>(instances.size());
        for (InstanceVO instance : instances) {
            try {
                Future<CompletedCount> future = executor.submit(() -> execute(instance, function));
                submitted.add(SubmittedCount.accepted(future));
            } catch (RejectedExecutionException exception) {
                submitted.add(SubmittedCount.rejectedSubmission());
            }
        }
        return submitted;
    }

    private static CompletedCount execute(InstanceVO instance, CountFunction function) {
        try {
            return new CompletedCount(function.count(instance), null, System.nanoTime());
        } catch (Exception exception) {
            return new CompletedCount(null, exception, System.nanoTime());
        }
    }

    private CompletedCount await(Future<CompletedCount> future, long startedNanos)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (future.isDone()) {
            return future.get();
        }
        long remainingNanos = timeoutNanos - elapsedNanos(startedNanos, System.nanoTime());
        if (remainingNanos <= 0L) {
            throw new TimeoutException("resource count batch deadline reached");
        }
        return future.get(remainingNanos, TimeUnit.NANOSECONDS);
    }

    private static void cancelOutstanding(List<SubmittedCount> submitted) {
        for (SubmittedCount count : submitted) {
            if (!count.rejected() && !count.future().isDone()) {
                count.future().cancel(true);
            }
        }
    }

    int activeCount() {
        return executor.getActiveCount();
    }

    int queuedCount() {
        return executor.getQueue().size();
    }

    @Override
    public void close() {
        executor.shutdownNow();
        executor.purge();
    }

    private static long elapsedNanos(long startedNanos, long completedNanos) {
        return completedNanos - startedNanos;
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private record CompletedCount(ResourceCounts counts, Throwable failure, long completedNanos) {
    }

    private record SubmittedCount(Future<CompletedCount> future, boolean rejected) {

        static SubmittedCount accepted(Future<CompletedCount> future) {
            return new SubmittedCount(future, false);
        }

        static SubmittedCount rejectedSubmission() {
            return new SubmittedCount(null, true);
        }
    }
}
