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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class InstanceResourceCountRunnerTest {

    @Test
    void countAllShouldRunIndependentLookupsConcurrentlyAndKeepInputOrderTest() throws Exception {
        InstanceVO first = instance(1L);
        InstanceVO second = instance(2L);
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        try (InstanceResourceCountRunner runner =
                     new InstanceResourceCountRunner(2, 2, 2, TimeUnit.SECONDS)) {
            CompletableFuture<List<InstanceResourceCountRunner.CountOutcome>> call =
                    CompletableFuture.supplyAsync(() -> runner.countAll(List.of(first, second), instance -> {
                        bothStarted.countDown();
                        if (instance.getId().equals(1L)) {
                            releaseFirst.await();
                        }
                        int id = instance.getId().intValue();
                        return new InstanceResourceCountRunner.ResourceCounts(id * 10, id * 100);
                    }));

            assertThat(bothStarted.await(1, TimeUnit.SECONDS)).isTrue();
            releaseFirst.countDown();

            List<InstanceResourceCountRunner.CountOutcome> outcomes = call.get(1, TimeUnit.SECONDS);
            assertThat(outcomes).extracting(InstanceResourceCountRunner.CountOutcome::status)
                    .containsExactly(
                            InstanceResourceCountRunner.OutcomeStatus.SUCCESS,
                            InstanceResourceCountRunner.OutcomeStatus.SUCCESS);
            assertThat(outcomes).extracting(outcome -> outcome.counts().topicCount())
                    .containsExactly(10, 20);
        } finally {
            releaseFirst.countDown();
        }
    }

    @Test
    void countAllShouldDiscardAResultThatFinishesAfterTheBatchDeadlineTest() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);

        try (InstanceResourceCountRunner runner =
                     new InstanceResourceCountRunner(1, 1, 100, TimeUnit.MILLISECONDS)) {
            long startedAt = System.nanoTime();
            List<InstanceResourceCountRunner.CountOutcome> outcomes = runner.countAll(
                    List.of(instance(1L)), ignored -> {
                        started.countDown();
                        awaitIgnoringInterrupt(release);
                        finished.countDown();
                        return new InstanceResourceCountRunner.ResourceCounts(7, 5);
                    });
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(elapsedMillis).isLessThan(1_000);
            assertThat(outcomes).singleElement().satisfies(outcome -> {
                assertThat(outcome.status()).isEqualTo(InstanceResourceCountRunner.OutcomeStatus.TIMED_OUT);
                assertThat(outcome.counts()).isNull();
            });

            release.countDown();
            assertThat(finished.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(outcomes).singleElement().satisfies(outcome ->
                    assertThat(outcome.status()).isEqualTo(
                            InstanceResourceCountRunner.OutcomeStatus.TIMED_OUT));
        } finally {
            release.countDown();
        }
    }

    @Test
    void countAllShouldRejectRowsBeyondTheBoundedQueueTest() throws Exception {
        List<InstanceVO> instances = List.of(instance(1L), instance(2L), instance(3L));
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (InstanceResourceCountRunner runner =
                     new InstanceResourceCountRunner(1, 1, 2, TimeUnit.SECONDS)) {
            CompletableFuture<List<InstanceResourceCountRunner.CountOutcome>> call =
                    CompletableFuture.supplyAsync(() -> runner.countAll(instances, instance -> {
                        firstStarted.countDown();
                        release.await();
                        int id = instance.getId().intValue();
                        return new InstanceResourceCountRunner.ResourceCounts(id, id);
                    }));

            assertThat(firstStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(waitForQueueSize(runner, 1, 1, TimeUnit.SECONDS)).isTrue();
            assertThat(runner.queuedCount()).isLessThanOrEqualTo(1);
            release.countDown();

            List<InstanceResourceCountRunner.CountOutcome> outcomes = call.get(1, TimeUnit.SECONDS);
            assertThat(outcomes).extracting(InstanceResourceCountRunner.CountOutcome::status)
                    .containsExactly(
                            InstanceResourceCountRunner.OutcomeStatus.SUCCESS,
                            InstanceResourceCountRunner.OutcomeStatus.SUCCESS,
                            InstanceResourceCountRunner.OutcomeStatus.REJECTED);
        } finally {
            release.countDown();
        }
    }

    @Test
    void countAllShouldUseOneDeadlineForTheWholeBatchTest() {
        CountDownLatch release = new CountDownLatch(1);

        try (InstanceResourceCountRunner runner =
                     new InstanceResourceCountRunner(2, 2, 150, TimeUnit.MILLISECONDS)) {
            long startedAt = System.nanoTime();
            List<InstanceResourceCountRunner.CountOutcome> outcomes = runner.countAll(
                    List.of(instance(1L), instance(2L)), ignored -> {
                        release.await();
                        return new InstanceResourceCountRunner.ResourceCounts(1, 1);
                    });
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

            assertThat(elapsedMillis).isLessThan(1_000);
            assertThat(outcomes).extracting(InstanceResourceCountRunner.CountOutcome::status)
                    .containsOnly(InstanceResourceCountRunner.OutcomeStatus.TIMED_OUT);
        } finally {
            release.countDown();
        }
    }

    @Test
    void countAllShouldKeepFailuresAndNullResultsLocalToTheirRowsTest() {
        try (InstanceResourceCountRunner runner =
                     new InstanceResourceCountRunner(3, 3, 1, TimeUnit.SECONDS)) {
            List<InstanceResourceCountRunner.CountOutcome> outcomes = runner.countAll(
                    List.of(instance(1L), instance(2L), instance(3L)), instance -> {
                        if (instance.getId().equals(1L)) {
                            throw new IllegalStateException("provider unavailable");
                        }
                        if (instance.getId().equals(2L)) {
                            return null;
                        }
                        return new InstanceResourceCountRunner.ResourceCounts(3, 4);
                    });

            assertThat(outcomes).extracting(InstanceResourceCountRunner.CountOutcome::status)
                    .containsExactly(
                            InstanceResourceCountRunner.OutcomeStatus.FAILED,
                            InstanceResourceCountRunner.OutcomeStatus.FAILED,
                            InstanceResourceCountRunner.OutcomeStatus.SUCCESS);
            assertThat(outcomes.get(0).failure()).hasMessage("provider unavailable");
            assertThat(outcomes.get(1).failure()).hasMessage("resource count provider returned null");
            assertThat(outcomes.get(2).counts())
                    .isEqualTo(new InstanceResourceCountRunner.ResourceCounts(3, 4));
        }
    }

    @Test
    void countAllShouldCancelOutstandingWorkAndRestoreCallerInterruptTest() throws Exception {
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch workerInterrupted = new CountDownLatch(1);
        AtomicReference<List<InstanceResourceCountRunner.CountOutcome>> outcomes = new AtomicReference<>();
        AtomicBoolean interruptRestored = new AtomicBoolean();

        try (InstanceResourceCountRunner runner =
                     new InstanceResourceCountRunner(1, 2, 10, TimeUnit.SECONDS)) {
            Thread caller = new Thread(() -> {
                outcomes.set(runner.countAll(List.of(instance(1L), instance(2L)), ignored -> {
                    workerStarted.countDown();
                    try {
                        Thread.sleep(10_000);
                    } catch (InterruptedException exception) {
                        workerInterrupted.countDown();
                        throw exception;
                    }
                    return new InstanceResourceCountRunner.ResourceCounts(1, 1);
                }));
                interruptRestored.set(Thread.currentThread().isInterrupted());
            });
            caller.start();

            assertThat(workerStarted.await(1, TimeUnit.SECONDS)).isTrue();
            caller.interrupt();
            caller.join(1_000);

            assertThat(caller.isAlive()).isFalse();
            assertThat(workerInterrupted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(interruptRestored).isTrue();
            assertThat(outcomes.get()).extracting(InstanceResourceCountRunner.CountOutcome::status)
                    .containsOnly(InstanceResourceCountRunner.OutcomeStatus.INTERRUPTED);
            assertThat(runner.queuedCount()).isZero();
        }
    }

    private static InstanceVO instance(long id) {
        InstanceVO instance = InstanceVO.builder().name("instance-" + id).build();
        instance.setId(id);
        return instance;
    }

    private static void awaitIgnoringInterrupt(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean waitForQueueSize(InstanceResourceCountRunner runner, int expected,
                                            long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (runner.queuedCount() == expected) {
                return true;
            }
            Thread.sleep(5);
        }
        return runner.queuedCount() == expected;
    }
}
