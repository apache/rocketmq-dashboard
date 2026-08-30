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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectorSchedulerTest {

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
        verify(processor).process(List.of(sample));
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
    }
}
