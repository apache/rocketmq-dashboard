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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectorSchedulerTest {

    @Test
    void collectsAllInstancesWhenInventoryExceedsExecutorCapacityTest() throws Exception {
        AlertingProperties properties = new AlertingProperties();
        properties.setCollectionParallelism(2);
        properties.setCollectionTimeout("PT2S");
        InstanceRepository instances = mock(InstanceRepository.class);
        List<InstanceVO> inventory = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            inventory.add(InstanceVO.builder().name("instance-" + index).endpoint("instance-" + index + ":9876").build());
        }
        when(instances.findAll()).thenReturn(inventory);

        CountDownLatch firstWaveStarted = new CountDownLatch(2);
        CountDownLatch releaseFirstWave = new CountDownLatch(1);
        AtomicInteger started = new AtomicInteger();
        ClusterMetricsCollector collector = mock(ClusterMetricsCollector.class);
        for (InstanceVO instance : inventory) {
            when(collector.supports(instance)).thenReturn(true);
            when(collector.collect(instance)).thenAnswer(invocation -> {
                if (started.incrementAndGet() <= 2) {
                    firstWaveStarted.countDown();
                    assertTrue(releaseFirstWave.await(1, TimeUnit.SECONDS), "test did not release the first wave");
                }
                return List.of(sampleFor(instance));
            });
        }

        List<MetricSample> persisted = new CopyOnWriteArrayList<>();
        MetricSnapshotRepository snapshots = mock(MetricSnapshotRepository.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            persisted.addAll(invocation.getArgument(0));
            return null;
        }).when(snapshots).saveAll(any());
        AlertCollectionLease lease = mock(AlertCollectionLease.class);
        when(lease.tryAcquire()).thenReturn(true);
        CollectorScheduler scheduler = new CollectorScheduler(properties, instances, List.of(collector), List.of(), snapshots,
                mock(NativeAlertProcessor.class), lease);

        Thread pass = new Thread(scheduler::collect);
        pass.start();
        assertTrue(firstWaveStarted.await(1, TimeUnit.SECONDS), "first wave should occupy the collector executor");
        TimeUnit.MILLISECONDS.sleep(100);
        releaseFirstWave.countDown();
        pass.join(TimeUnit.SECONDS.toMillis(3));
        scheduler.stopCollectionExecutor();

        assertTrue(!pass.isAlive(), "collection pass should finish");
        assertEquals(inventory.size(), persisted.size(), "collector saturation must not drop later instances");
        assertEquals(inventory.stream().map(InstanceVO::getName).sorted().toList(),
                persisted.stream().map(MetricSample::instanceId).sorted().toList());
    }

    @Test
    void doesNotQueueMoreThanParallelismBeforeAnyCollectionCompletesTest() throws Exception {
        AlertingProperties properties = new AlertingProperties();
        properties.setCollectionParallelism(2);
        properties.setCollectionTimeout("PT2S");
        InstanceRepository instances = mock(InstanceRepository.class);
        List<InstanceVO> inventory = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            inventory.add(InstanceVO.builder().name("window-" + index).endpoint("window-" + index + ":9876").build());
        }
        when(instances.findAll()).thenReturn(inventory);

        CountDownLatch firstWaveStarted = new CountDownLatch(2);
        CountDownLatch releaseFirstWave = new CountDownLatch(1);
        AtomicInteger started = new AtomicInteger();
        ClusterMetricsCollector collector = mock(ClusterMetricsCollector.class);
        for (InstanceVO instance : inventory) {
            when(collector.supports(instance)).thenReturn(true);
            when(collector.collect(instance)).thenAnswer(invocation -> {
                if (started.incrementAndGet() <= 2) {
                    firstWaveStarted.countDown();
                    assertTrue(releaseFirstWave.await(1, TimeUnit.SECONDS), "test did not release the first wave");
                }
                return List.of(sampleFor(instance));
            });
        }

        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, queue);
        CollectorScheduler scheduler = new CollectorScheduler(properties, instances, List.of(collector), List.of(),
                mock(MetricSnapshotRepository.class), mock(NativeAlertProcessor.class), acquiredLease(), executor);

        Thread pass = new Thread(scheduler::collect);
        pass.start();
        assertTrue(firstWaveStarted.await(1, TimeUnit.SECONDS), "first wave should occupy the collector executor");
        TimeUnit.MILLISECONDS.sleep(100);
        int queuedBeforeAnyCompletion = queue.size();
        releaseFirstWave.countDown();
        pass.join(TimeUnit.SECONDS.toMillis(3));
        scheduler.stopCollectionExecutor();

        assertTrue(!pass.isAlive(), "collection pass should finish");
        assertEquals(0, queuedBeforeAnyCompletion, "collector should submit the next instance only after one finishes");
    }

    @Test
    void persistsFastInstanceWhileAnotherInstanceTimesOutTest() throws Exception {
        AlertingProperties properties = new AlertingProperties();
        properties.setCollectionParallelism(2);
        properties.setCollectionTimeout("PT0.1S");
        InstanceRepository instances = mock(InstanceRepository.class);
        InstanceVO slow = InstanceVO.builder().name("slow").endpoint("slow:9876").build();
        InstanceVO fast = InstanceVO.builder().name("fast").endpoint("fast:9876").build();
        when(instances.findAll()).thenReturn(List.of(slow, fast));

        ClusterMetricsCollector collector = mock(ClusterMetricsCollector.class);
        when(collector.supports(slow)).thenReturn(true);
        when(collector.supports(fast)).thenReturn(true);
        when(collector.collect(slow)).thenAnswer(invocation -> {
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return List.of();
        });
        MetricSample fastSample = new MetricSample("nameserver.availability", AlertDomain.CLUSTER, "fast", null,
                null, 1D, MetricAvailability.AVAILABLE, Instant.now());
        when(collector.collect(fast)).thenReturn(List.of(fastSample));

        CountDownLatch persisted = new CountDownLatch(1);
        MetricSnapshotRepository snapshots = mock(MetricSnapshotRepository.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            persisted.countDown();
            return null;
        }).when(snapshots).saveAll(List.of(fastSample));
        AlertCollectionLease lease = mock(AlertCollectionLease.class);
        when(lease.tryAcquire()).thenReturn(true);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CollectorScheduler scheduler = new CollectorScheduler(properties, instances, List.of(collector), List.of(), snapshots,
                mock(NativeAlertProcessor.class), lease, executor);

        Thread pass = new Thread(scheduler::collect);
        pass.start();
        assertTrue(persisted.await(1, TimeUnit.SECONDS), "fast instance should persist before the slow timeout expires");
        pass.join(TimeUnit.SECONDS.toMillis(2));
        assertTrue(!pass.isAlive(), "collection pass should finish after cancelling the slow instance");
        scheduler.stopCollectionExecutor();
    }

    @Test
    void discardsLateResultsFromACancelledJobTest() throws Exception {
        AlertingProperties properties = new AlertingProperties();
        // Four instances, parallelism 2, staggered by a gated quick job: the
        // slow job's deadline expires while two later-submitted jobs are
        // still running, so the pass — and its lease heartbeat — stay alive
        // when the cancelled job's late result arrives.
        properties.setCollectionParallelism(2);
        properties.setCollectionTimeout("PT0.5S");
        InstanceRepository instances = mock(InstanceRepository.class);
        InstanceVO slow = InstanceVO.builder().name("slow").endpoint("slow:9876").build();
        InstanceVO quick = InstanceVO.builder().name("quick").endpoint("quick:9876").build();
        InstanceVO blocker1 = InstanceVO.builder().name("blocker1").endpoint("blocker1:9876").build();
        InstanceVO blocker2 = InstanceVO.builder().name("blocker2").endpoint("blocker2:9876").build();
        when(instances.findAll()).thenReturn(List.of(slow, quick, blocker1, blocker2));

        CountDownLatch releaseQuick = new CountDownLatch(1);
        CountDownLatch releaseBlocker1 = new CountDownLatch(1);
        CountDownLatch releaseBlocker2 = new CountDownLatch(1);
        CountDownLatch releaseSlow = new CountDownLatch(1);
        MetricSample slowSample = new MetricSample("nameserver.availability", AlertDomain.CLUSTER, "slow", null,
                null, 1D, MetricAvailability.AVAILABLE, Instant.now());
        MetricSample blockerSample = new MetricSample("nameserver.availability", AlertDomain.CLUSTER, "blocker1",
                null, null, 1D, MetricAvailability.AVAILABLE, Instant.now());
        ClusterMetricsCollector collector = mock(ClusterMetricsCollector.class);
        when(collector.supports(any())).thenReturn(true);
        when(collector.metricKeys()).thenReturn(java.util.Set.of("nameserver.availability"));
        when(collector.collect(slow)).thenAnswer(invocation -> {
            // Absorbs the cancellation interrupt and keeps waiting, like a
            // collector that only checks the interrupt at safe points: the
            // late result must still be discarded. The flag is deliberately
            // not re-set — re-setting it would make await() spin instead of
            // parking again.
            while (true) {
                try {
                    releaseSlow.await();
                    break;
                } catch (InterruptedException absorbed) {
                    // swallowed on purpose
                }
            }
            return List.of(slowSample);
        });
        when(collector.collect(quick)).thenAnswer(invocation -> {
            releaseQuick.await();
            return List.of();
        });
        when(collector.collect(blocker1)).thenAnswer(invocation -> {
            releaseBlocker1.await();
            return List.of(blockerSample);
        });
        when(collector.collect(blocker2)).thenAnswer(invocation -> {
            releaseBlocker2.await();
            return List.of();
        });

        MetricSnapshotRepository snapshots = mock(MetricSnapshotRepository.class);
        CountDownLatch persisted = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            if (invocation.getArgument(0).toString().contains("blocker1")) {
                persisted.countDown();
            }
            return null;
        }).when(snapshots).saveAll(any());
        NativeAlertProcessor processor = mock(NativeAlertProcessor.class);
        AlertCollectionLease lease = mock(AlertCollectionLease.class);
        when(lease.tryAcquire()).thenReturn(true);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CollectorScheduler scheduler = new CollectorScheduler(properties, instances, List.of(collector), List.of(),
                snapshots, processor, lease, executor);

        Thread pass = new Thread(scheduler::collect);
        pass.start();
        // t=0: slow + quick start. t=400ms: quick finishes, blocker1 starts
        // with a fresh deadline. t=500ms: slow's deadline cancels it while
        // blocker1/blocker2 keep the pass alive; blocker2 takes the freed
        // slot with another fresh deadline.
        TimeUnit.MILLISECONDS.sleep(400);
        releaseQuick.countDown();
        TimeUnit.MILLISECONDS.sleep(200);
        releaseBlocker1.countDown();
        assertTrue(persisted.await(1, TimeUnit.SECONDS), "the healthy instance still persists");
        // The cancelled job's late result lands while the pass is alive and
        // the lease heartbeat is held; only afterwards is the last blocker
        // released to let the pass finish.
        releaseSlow.countDown();
        TimeUnit.MILLISECONDS.sleep(200);
        releaseBlocker2.countDown();
        pass.join(TimeUnit.SECONDS.toMillis(3));
        assertTrue(!pass.isAlive(), "the collection pass should finish");
        verify(snapshots, never()).saveAll(List.of(slowSample));
        verify(processor, never()).processSuccessfulCollection(any(),
                argThat(list -> list.contains(slowSample)));
        scheduler.stopCollectionExecutor();
    }

    @Test
    void appliesCollectionTimeoutToTheWholePassInsteadOfEachInstanceTest() {
        AlertingProperties properties = new AlertingProperties();
        properties.setCollectionTimeout("PT0.25S");
        InstanceRepository instances = mock(InstanceRepository.class);
        when(instances.findAll()).thenReturn(List.of(
                InstanceVO.builder().name("slow-a").build(),
                InstanceVO.builder().name("slow-b").build(),
                InstanceVO.builder().name("slow-c").build()));
        ClusterMetricsCollector collector = mock(ClusterMetricsCollector.class);
        when(collector.supports(any(InstanceVO.class))).thenReturn(true);
        when(collector.collect(any(InstanceVO.class))).thenAnswer(invocation -> {
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return List.of();
        });
        AlertCollectionLease lease = mock(AlertCollectionLease.class);
        when(lease.tryAcquire()).thenReturn(true);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CollectorScheduler scheduler = new CollectorScheduler(properties, instances, List.of(collector), List.of(),
                mock(MetricSnapshotRepository.class), mock(NativeAlertProcessor.class), lease, executor);

        long startedAt = System.nanoTime();
        scheduler.collect();
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        scheduler.stopCollectionExecutor();

        assertTrue(elapsedMillis < 600,
                "the configured timeout should cap the complete pass, elapsed=" + elapsedMillis + "ms");
    }

    @Test
    void collectsAndPersistsSupportedSamplesTest() {
        AlertingProperties properties = new AlertingProperties();
        InstanceRepository instances = mock(InstanceRepository.class);
        ClusterMetricsCollector collector = mock(ClusterMetricsCollector.class);
        MetricSnapshotRepository snapshots = mock(MetricSnapshotRepository.class);
        InstanceVO instance = InstanceVO.builder().name("local").endpoint("localhost:9876").build();
        MetricSample sample = new MetricSample("nameserver.availability", AlertDomain.CLUSTER, "local", null,
                null, 1D, MetricAvailability.AVAILABLE, Instant.now());
        when(instances.findAll()).thenReturn(List.of(instance));
        when(collector.supports(instance)).thenReturn(true);
        when(collector.collect(instance)).thenReturn(List.of(sample));

        NativeAlertProcessor processor = mock(NativeAlertProcessor.class);
        AlertCollectionLease lease = mock(AlertCollectionLease.class);
        when(lease.tryAcquire()).thenReturn(true);
        new CollectorScheduler(properties, instances, List.of(collector), List.of(), snapshots, processor, lease).collect();

        verify(snapshots).saveAll(List.of(sample));
        verify(processor).processSuccessfulCollection(any(MetricCollectionScope.class), org.mockito.ArgumentMatchers.eq(List.of(sample)));
    }

    @Test
    void removesExpiredSnapshotsUsingConfiguredRetentionTest() {
        AlertingProperties properties = new AlertingProperties();
        properties.setSnapshotRetention("PT2H");
        MetricSnapshotRepository snapshots = mock(MetricSnapshotRepository.class);

        new CollectorScheduler(properties, mock(InstanceRepository.class), List.of(), List.of(), snapshots,
                mock(NativeAlertProcessor.class), mock(AlertCollectionLease.class)).cleanUpSnapshots();

        verify(snapshots).deleteBefore(any(Instant.class));
    }

    @Test
    void doesNotCollectWhenAnotherReplicaHoldsTheLeaseTest() {
        AlertingProperties properties = new AlertingProperties();
        InstanceRepository instances = mock(InstanceRepository.class);
        AlertCollectionLease lease = mock(AlertCollectionLease.class);
        when(lease.tryAcquire()).thenReturn(false);

        new CollectorScheduler(properties, instances, List.of(), List.of(), mock(MetricSnapshotRepository.class),
                mock(NativeAlertProcessor.class), lease).collect();

        verify(instances, never()).findAll();
    }

    @Test
    void discardsCollectedSamplesWhenTheLeaseExpiresBeforePersistenceTest() {
        AlertingProperties properties = new AlertingProperties();
        InstanceRepository instances = mock(InstanceRepository.class);
        ClusterMetricsCollector collector = mock(ClusterMetricsCollector.class);
        MetricSnapshotRepository snapshots = mock(MetricSnapshotRepository.class);
        NativeAlertProcessor processor = mock(NativeAlertProcessor.class);
        AlertCollectionLease lease = mock(AlertCollectionLease.class);
        InstanceVO instance = InstanceVO.builder().name("local").endpoint("localhost:9876").build();
        MetricSample sample = new MetricSample("nameserver.availability", AlertDomain.CLUSTER, "local", null,
                null, 1D, MetricAvailability.AVAILABLE, Instant.now());
        when(instances.findAll()).thenReturn(List.of(instance));
        when(collector.supports(instance)).thenReturn(true);
        when(collector.collect(instance)).thenReturn(List.of(sample));
        when(lease.tryAcquire()).thenReturn(true, false);

        new CollectorScheduler(properties, instances, List.of(collector), List.of(), snapshots, processor, lease).collect();

        verify(snapshots, never()).saveAll(any());
        verify(processor, never()).process(any());
        verify(processor, never()).processSuccessfulCollection(any(MetricCollectionScope.class), any());
    }

    @Test
    void doesNotReconcileCollectionScopeWhenCollectorFailsTest() {
        AlertingProperties properties = new AlertingProperties();
        InstanceRepository instances = mock(InstanceRepository.class);
        ClusterMetricsCollector collector = mock(ClusterMetricsCollector.class);
        MetricSnapshotRepository snapshots = mock(MetricSnapshotRepository.class);
        NativeAlertProcessor processor = mock(NativeAlertProcessor.class);
        AlertCollectionLease lease = mock(AlertCollectionLease.class);
        InstanceVO instance = InstanceVO.builder().name("local").endpoint("localhost:9876").build();
        when(instances.findAll()).thenReturn(List.of(instance));
        when(collector.supports(instance)).thenReturn(true);
        when(collector.collect(instance)).thenThrow(new IllegalStateException("collector failed"));
        when(lease.tryAcquire()).thenReturn(true);

        new CollectorScheduler(properties, instances, List.of(collector), List.of(), snapshots, processor, lease).collect();

        verify(snapshots, never()).saveAll(any());
        verify(processor, never()).processSuccessfulCollection(any(MetricCollectionScope.class), any());
    }

    @Test
    void reconcilesSuccessfulEmptyCollectionWhenCollectorDeclaresMetricKeysTest() {
        AlertingProperties properties = new AlertingProperties();
        InstanceRepository instances = mock(InstanceRepository.class);
        ClusterMetricsCollector collector = mock(ClusterMetricsCollector.class);
        MetricSnapshotRepository snapshots = mock(MetricSnapshotRepository.class);
        NativeAlertProcessor processor = mock(NativeAlertProcessor.class);
        AlertCollectionLease lease = mock(AlertCollectionLease.class);
        InstanceVO instance = InstanceVO.builder().name("local").endpoint("localhost:9876").build();
        when(instances.findAll()).thenReturn(List.of(instance));
        when(collector.supports(instance)).thenReturn(true);
        when(collector.metricKeys()).thenReturn(java.util.Set.of("broker.availability"));
        when(collector.collect(instance)).thenReturn(List.of());
        when(lease.tryAcquire()).thenReturn(true);

        new CollectorScheduler(properties, instances, List.of(collector), List.of(), snapshots, processor, lease).collect();

        verify(snapshots, never()).saveAll(any());
        verify(processor).processSuccessfulCollection(any(MetricCollectionScope.class), org.mockito.ArgumentMatchers.eq(List.of()));
    }

    @Test
    void renewsLeaseWhileAnEmptyCollectionPassIsStillRunningTest() throws Exception {
        AlertingProperties properties = new AlertingProperties();
        properties.setCollectionLeaseDuration("PT0.2S");
        properties.setCollectionLeaseRenewalInterval("PT1S");
        properties.setCollectionTimeout("PT1S");
        InstanceVO instance = InstanceVO.builder().name("slow-empty").build();
        InstanceRepository instances = mock(InstanceRepository.class);
        when(instances.findAll()).thenReturn(List.of(instance));
        ClusterMetricsCollector collector = mock(ClusterMetricsCollector.class);
        when(collector.supports(instance)).thenReturn(true);
        when(collector.collect(instance)).thenAnswer(invocation -> {
            Thread.sleep(350);
            return List.of();
        });
        AlertCollectionLease lease = mock(AlertCollectionLease.class);
        when(lease.tryAcquire()).thenReturn(true);
        when(lease.renew()).thenReturn(true);
        ExecutorService collectionExecutor = Executors.newFixedThreadPool(1);
        ScheduledExecutorService renewalExecutor = Executors.newSingleThreadScheduledExecutor();
        CollectorScheduler scheduler = new CollectorScheduler(properties, instances, List.of(collector), List.of(),
                mock(MetricSnapshotRepository.class), mock(NativeAlertProcessor.class), lease, collectionExecutor,
                renewalExecutor);

        try {
            scheduler.collect();
            verify(lease, atLeast(3)).renew();
        } finally {
            scheduler.stopCollectionExecutor();
        }
    }

    @Test
    void stopsAnActivePassWhenLeaseRenewalFailsTest() throws Exception {
        AlertingProperties properties = new AlertingProperties();
        properties.setCollectionLeaseDuration("PT0.2S");
        properties.setCollectionLeaseRenewalInterval("PT1S");
        properties.setCollectionTimeout("PT1S");
        InstanceVO instance = InstanceVO.builder().name("lease-lost").build();
        InstanceRepository instances = mock(InstanceRepository.class);
        when(instances.findAll()).thenReturn(List.of(instance));
        ClusterMetricsCollector collector = mock(ClusterMetricsCollector.class);
        when(collector.supports(instance)).thenReturn(true);
        when(collector.collect(instance)).thenAnswer(invocation -> {
            Thread.sleep(350);
            return List.of(sampleFor(instance));
        });
        MetricSnapshotRepository snapshots = mock(MetricSnapshotRepository.class);
        NativeAlertProcessor processor = mock(NativeAlertProcessor.class);
        AlertCollectionLease lease = mock(AlertCollectionLease.class);
        when(lease.tryAcquire()).thenReturn(true);
        when(lease.renew()).thenReturn(false);
        ExecutorService collectionExecutor = Executors.newFixedThreadPool(1);
        ScheduledExecutorService renewalExecutor = Executors.newSingleThreadScheduledExecutor();
        CollectorScheduler scheduler = new CollectorScheduler(properties, instances, List.of(collector), List.of(),
                snapshots, processor, lease, collectionExecutor, renewalExecutor);

        try {
            scheduler.collect();
            verify(snapshots, never()).saveAll(any());
            verify(processor, never()).processSuccessfulCollection(any(), any());
        } finally {
            scheduler.stopCollectionExecutor();
        }
    }

    @Test
    void cleanUpSnapshotsSkipsRunWhenRetentionIsMalformedTest() {
        AlertingProperties properties = new AlertingProperties();
        properties.setSnapshotRetention("24h");
        MetricSnapshotRepository snapshots = mock(MetricSnapshotRepository.class);
        CollectorScheduler scheduler = new CollectorScheduler(properties, mock(InstanceRepository.class), List.of(),
                List.of(), snapshots, mock(NativeAlertProcessor.class), mock(AlertCollectionLease.class));

        assertDoesNotThrow(scheduler::cleanUpSnapshots);
        verify(snapshots, never()).deleteBefore(any());
    }

    @Test
    void cleanUpSnapshotsDeletesBeforeRetentionHorizonTest() {
        AlertingProperties properties = new AlertingProperties();
        properties.setSnapshotRetention("PT24H");
        MetricSnapshotRepository snapshots = mock(MetricSnapshotRepository.class);
        CollectorScheduler scheduler = new CollectorScheduler(properties, mock(InstanceRepository.class), List.of(),
                List.of(), snapshots, mock(NativeAlertProcessor.class), mock(AlertCollectionLease.class));

        scheduler.cleanUpSnapshots();

        verify(snapshots).deleteBefore(argThat(horizon -> horizon.isAfter(Instant.now().minusSeconds(24 * 3600 + 600))
                && horizon.isBefore(Instant.now().minusSeconds(24 * 3600 - 600))));
    }

    private static MetricSample sampleFor(InstanceVO instance) {
        return new MetricSample("nameserver.availability", AlertDomain.CLUSTER, instance.getName(), null,
                null, 1D, MetricAvailability.AVAILABLE, Instant.now());
    }

    private static AlertCollectionLease acquiredLease() {
        AlertCollectionLease lease = mock(AlertCollectionLease.class);
        when(lease.tryAcquire()).thenReturn(true);
        return lease;
    }
}
