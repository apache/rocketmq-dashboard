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

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.ops.alert.NativeAlertProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.time.Duration;
import java.time.Instant;

/** Runs independent, bounded collection jobs for each configured instance. */
@Slf4j
@Component
@EnableConfigurationProperties(AlertingProperties.class)
public class CollectorScheduler {
    private final AlertingProperties properties;
    private final InstanceRepository instanceRepository;
    private final List<ClusterMetricsCollector> clusterCollectors;
    private final List<BusinessMetricsCollector> businessCollectors;
    private final MetricSnapshotRepository snapshotRepository;
    private final NativeAlertProcessor alertProcessor;
    private final AlertCollectionLease collectionLease;
    private final ExecutorService collectionExecutor;

    @Autowired
    public CollectorScheduler(AlertingProperties properties, InstanceRepository instanceRepository,
            List<ClusterMetricsCollector> clusterCollectors, List<BusinessMetricsCollector> businessCollectors,
            MetricSnapshotRepository snapshotRepository, NativeAlertProcessor alertProcessor,
            AlertCollectionLease collectionLease) {
        this(properties, instanceRepository, clusterCollectors, businessCollectors, snapshotRepository, alertProcessor,
                collectionLease, newCollectionExecutor(properties));
    }

    CollectorScheduler(AlertingProperties properties, InstanceRepository instanceRepository,
            List<ClusterMetricsCollector> clusterCollectors, List<BusinessMetricsCollector> businessCollectors,
            MetricSnapshotRepository snapshotRepository, NativeAlertProcessor alertProcessor,
            AlertCollectionLease collectionLease, ExecutorService collectionExecutor) {
        this.properties = properties;
        this.instanceRepository = instanceRepository;
        this.clusterCollectors = clusterCollectors;
        this.businessCollectors = businessCollectors;
        this.snapshotRepository = snapshotRepository;
        this.alertProcessor = alertProcessor;
        this.collectionLease = collectionLease;
        this.collectionExecutor = collectionExecutor;
    }

    @Scheduled(fixedDelayString = "${studio.alerting.collection-interval:PT30S}")
    public void collect() {
        if (!collectionLease.tryAcquire()) {
            log.debug("Skipping native alert collection because another Studio replica holds the lease");
            return;
        }
        List<Future<?>> jobs = instanceRepository.findAll().stream()
                .map(this::submitCollection)
                .filter(java.util.Objects::nonNull)
                .toList();
        Duration timeout = parsePositiveDuration(properties.getCollectionTimeout(), Duration.ofSeconds(15));
        long passStartedAt = System.nanoTime();
        long timeoutNanos = timeout.toNanos();
        for (Future<?> job : jobs) {
            try {
                long remainingNanos = timeoutNanos - (System.nanoTime() - passStartedAt);
                if (remainingNanos <= 0) {
                    job.cancel(true);
                    continue;
                }
                job.get(remainingNanos, TimeUnit.NANOSECONDS);
            } catch (java.util.concurrent.TimeoutException error) {
                job.cancel(true);
                log.warn("Native metric collection pass exceeded {} and unfinished work was cancelled", timeout);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return;
            } catch (java.util.concurrent.ExecutionException error) {
                log.warn("Native metric collection job failed: {}", error.getCause().getMessage());
            }
        }
    }

    private Future<?> submitCollection(InstanceVO instance) {
        try {
            return collectionExecutor.submit(() -> {
                collectClusterMetrics(instance);
                collectBusinessMetrics(instance);
            });
        } catch (java.util.concurrent.RejectedExecutionException error) {
            log.warn("Skipping native metric collection for instance {} because the collector is saturated", instance.getName());
            return null;
        }
    }

    @Scheduled(fixedDelayString = "${studio.alerting.snapshot-cleanup-interval:PT1H}")
    public void cleanUpSnapshots() {
        Duration retention = Duration.parse(properties.getSnapshotRetention());
        if (retention.isNegative() || retention.isZero()) {
            return;
        }
        snapshotRepository.deleteBefore(Instant.now().minus(retention));
    }

    private void collectClusterMetrics(InstanceVO instance) {
        for (ClusterMetricsCollector collector : clusterCollectors) {
            try {
                persist(collector.supports(instance) ? collector.collect(instance) : List.of());
            } catch (RuntimeException error) {
                log.warn("Native metric collector failed for instance {}: {}", instance.getName(), error.getMessage());
            }
        }
    }

    private void collectBusinessMetrics(InstanceVO instance) {
        for (BusinessMetricsCollector collector : businessCollectors) {
            try {
                persist(collector.supports(instance) ? collector.collect(instance) : List.of());
            } catch (RuntimeException error) {
                log.warn("Native metric collector failed for instance {}: {}", instance.getName(), error.getMessage());
            }
        }
    }

    private void persist(List<MetricSample> samples) {
        if (!samples.isEmpty()) {
            // Refresh immediately before persisting so a slow remote collection cannot write after lease loss.
            if (!collectionLease.tryAcquire()) {
                log.debug("Discarding native metric samples because the collection lease was lost");
                return;
            }
            snapshotRepository.saveAll(samples);
            alertProcessor.process(samples);
        }
    }

    @PreDestroy
    void stopCollectionExecutor() {
        collectionExecutor.shutdownNow();
    }

    private static ExecutorService newCollectionExecutor(AlertingProperties properties) {
        int parallelism = Math.max(1, properties.getCollectionParallelism());
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(task, "studio-native-metric-collector");
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(parallelism, parallelism, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(parallelism), threadFactory, new ThreadPoolExecutor.AbortPolicy());
    }

    private static Duration parsePositiveDuration(String value, Duration fallback) {
        try {
            Duration duration = Duration.parse(value);
            return duration.isPositive() ? duration : fallback;
        } catch (RuntimeException error) {
            log.warn("Invalid native metric collection timeout '{}'; using {}", value, fallback);
            return fallback;
        }
    }
}
