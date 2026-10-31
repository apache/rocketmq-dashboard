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
package org.apache.rocketmq.studio.cluster.metrics;

import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.ops.alert.AlertDomain;
import org.apache.rocketmq.studio.ops.alert.NativeAlertProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectorCancellationCheckpointTest {
    enum Checkpoint {
        LEASE,
        SNAPSHOT
    }

    @ParameterizedTest
    @EnumSource(Checkpoint.class)
    void cancellationDuringBlockingCheckpointStopsSubsequentSideEffectsTest(Checkpoint checkpoint) throws Exception {
        AlertingProperties properties = new AlertingProperties();
        properties.setCollectionParallelism(3);
        properties.setCollectionTimeout("PT1S");
        InstanceVO slow = instance("slow");
        InstanceVO fastA = instance("fast-a");
        InstanceVO fastB = instance("fast-b");
        InstanceVO blockerA = instance("blocker-a");
        InstanceVO blockerB = instance("blocker-b");
        InstanceRepository instances = mock(InstanceRepository.class);
        when(instances.findAll()).thenReturn(List.of(slow, fastA, fastB, blockerA, blockerB));

        CountDownLatch blockersStarted = new CountDownLatch(2);
        CountDownLatch releaseBlockers = new CountDownLatch(1);
        CountDownLatch slowFinished = new CountDownLatch(1);
        BlockingCheckpoint checkpointGate = new BlockingCheckpoint();
        AtomicReference<Thread> slowThread = new AtomicReference<>();
        MetricSample slowSample = sample(slow);
        ClusterMetricsCollector collector = mock(ClusterMetricsCollector.class);
        when(collector.supports(any())).thenReturn(true);
        when(collector.metricKeys()).thenReturn(Set.of("nameserver.availability"));
        when(collector.collect(slow)).thenAnswer(invocation -> {
            slowThread.set(Thread.currentThread());
            return List.of(slowSample);
        });
        when(collector.collect(fastA)).thenReturn(List.of());
        when(collector.collect(fastB)).thenReturn(List.of());
        when(collector.collect(blockerA)).thenAnswer(invocation -> {
            blockersStarted.countDown();
            awaitUninterruptibly(releaseBlockers);
            return List.of();
        });
        when(collector.collect(blockerB)).thenAnswer(invocation -> {
            blockersStarted.countDown();
            awaitUninterruptibly(releaseBlockers);
            return List.of();
        });

        AlertCollectionLease lease = mock(AlertCollectionLease.class);
        when(lease.tryAcquire()).thenAnswer(invocation -> {
            if (checkpoint == Checkpoint.LEASE && Thread.currentThread() == slowThread.get()) {
                checkpointGate.awaitReleaseAbsorbingCancellation();
            }
            return true;
        });
        MetricSnapshotRepository snapshots = mock(MetricSnapshotRepository.class);
        doAnswer(invocation -> {
            if (checkpoint == Checkpoint.SNAPSHOT && Thread.currentThread() == slowThread.get()) {
                checkpointGate.awaitReleaseAbsorbingCancellation();
            }
            return null;
        }).when(snapshots).saveAll(any());
        NativeAlertProcessor processor = mock(NativeAlertProcessor.class);
        ExecutorService executor = new ThreadPoolExecutor(3, 3, 0, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>()) {
            @Override
            protected void afterExecute(Runnable task, Throwable error) {
                super.afterExecute(task, error);
                if (Thread.currentThread() == slowThread.get()) {
                    slowFinished.countDown();
                }
            }
        };
        CollectorScheduler scheduler = new CollectorScheduler(properties, instances, List.of(collector), List.of(),
                snapshots, processor, lease, executor);
        AtomicReference<Throwable> passFailure = new AtomicReference<>();
        Thread pass = new Thread(() -> {
            try {
                scheduler.collect();
            } catch (Throwable error) {
                passFailure.set(error);
            }
        });
        try {
            pass.start();
            assertTrue(checkpointGate.entered.await(3, TimeUnit.SECONDS),
                    "slow job must reach the blocking checkpoint");
            assertTrue(blockersStarted.await(3, TimeUnit.SECONDS),
                    "younger jobs must keep the collection pass alive");
            assertTrue(checkpointGate.cancelled.await(5, TimeUnit.SECONDS),
                    "the scheduler must cancel the slow job after its timeout");
            checkpointGate.release.countDown();
            assertTrue(slowFinished.await(3, TimeUnit.SECONDS), "slow worker must finish after release");
            releaseBlockers.countDown();
            pass.join(TimeUnit.SECONDS.toMillis(3));

            if (checkpoint == Checkpoint.LEASE) {
                verify(snapshots, never()).saveAll(List.of(slowSample));
            } else {
                verify(snapshots).saveAll(List.of(slowSample));
            }
            verify(processor, never()).processSuccessfulCollection(any(),
                    org.mockito.ArgumentMatchers.argThat(rows -> rows.contains(slowSample)));
        } finally {
            checkpointGate.release.countDown();
            releaseBlockers.countDown();
            pass.join(TimeUnit.SECONDS.toMillis(3));
            scheduler.stopCollectionExecutor();
        }
        assertNull(passFailure.get(), "collection pass must not fail");
    }

    @Test
    void leaseLossDuringSnapshotStopsAlertProcessingTest() throws Exception {
        AlertingProperties properties = new AlertingProperties();
        InstanceVO instance = instance("lease-lost-during-snapshot");
        InstanceRepository instances = mock(InstanceRepository.class);
        when(instances.findAll()).thenReturn(List.of(instance));
        MetricSample sample = sample(instance);
        ClusterMetricsCollector collector = mock(ClusterMetricsCollector.class);
        when(collector.supports(instance)).thenReturn(true);
        when(collector.metricKeys()).thenReturn(Set.of("nameserver.availability"));
        when(collector.collect(instance)).thenReturn(List.of(sample));

        AlertCollectionLease lease = mock(AlertCollectionLease.class);
        when(lease.tryAcquire()).thenReturn(true);
        when(lease.renew()).thenReturn(false);
        ScheduledExecutorService renewals = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> renewalTask = mock(ScheduledFuture.class);
        AtomicReference<Runnable> renewal = new AtomicReference<>();
        when(renewals.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(invocation -> {
                    renewal.set(invocation.getArgument(0));
                    return renewalTask;
                });
        MetricSnapshotRepository snapshots = mock(MetricSnapshotRepository.class);
        doAnswer(invocation -> {
            renewal.get().run();
            return null;
        }).when(snapshots).saveAll(List.of(sample));
        NativeAlertProcessor processor = mock(NativeAlertProcessor.class);
        CollectorScheduler scheduler = new CollectorScheduler(properties, instances, List.of(collector), List.of(),
                snapshots, processor, lease, newSingleThreadExecutor(), renewals);
        try {
            scheduler.collect();
            verify(snapshots).saveAll(List.of(sample));
            verify(processor, never()).processSuccessfulCollection(any(), any());
        } finally {
            scheduler.stopCollectionExecutor();
        }
    }

    private static InstanceVO instance(String name) {
        return InstanceVO.builder().name(name).build();
    }

    private static MetricSample sample(InstanceVO instance) {
        return new MetricSample("nameserver.availability", AlertDomain.CLUSTER, instance.getName(), null,
                null, 1D, MetricAvailability.AVAILABLE, Instant.now());
    }

    private static ExecutorService newSingleThreadExecutor() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    }

    private static void awaitUninterruptibly(CountDownLatch release) {
        while (true) {
            try {
                release.await();
                return;
            } catch (InterruptedException ignored) {
                // A sibling cancellation must not abort the healthy blocker.
            }
        }
    }

    private static final class BlockingCheckpoint {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch cancelled = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private void awaitReleaseAbsorbingCancellation() {
            entered.countDown();
            while (true) {
                try {
                    release.await();
                    return;
                } catch (InterruptedException interrupted) {
                    cancelled.countDown();
                }
            }
        }
    }
}
