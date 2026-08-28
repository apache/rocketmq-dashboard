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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
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
        List<InstanceVO> instances = instanceRepository.findAll();
        if (instances.isEmpty()) {
            return;
        }
        Duration timeout = parsePositiveDuration(properties.getCollectionTimeout(), Duration.ofSeconds(15));
        Duration passTimeout = timeout.multipliedBy(instances.size());
        Instant passDeadline = Instant.now().plus(passTimeout);
        int parallelism = Math.max(1, properties.getCollectionParallelism());
        CompletionService<InstanceVO> completionService = new ExecutorCompletionService<>(collectionExecutor);
        ArrayDeque<InstanceVO> pending = new ArrayDeque<>(instances);
        List<CollectionJob> running = new ArrayList<>();
        try {
            submitAvailable(completionService, pending, running, parallelism, timeout, passDeadline);
            while (!running.isEmpty()) {
                completeFinishedJobs(running);
                cancelExpiredJobs(running, timeout, passDeadline, passTimeout);
                submitAvailable(completionService, pending, running, parallelism, timeout, passDeadline);
                if (running.isEmpty()) {
                    break;
                }
                Future<InstanceVO> completed = completionService.poll(nextWaitMillis(running, passDeadline),
                        TimeUnit.MILLISECONDS);
                if (completed != null) {
                    completeJob(running, completed);
                }
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            cancelRunningJobs(running);
            return;
        } catch (RejectedExecutionException error) {
            cancelRunningJobs(running);
            log.warn("Native metric collection executor rejected new work; aborting pass with {} unstarted instance(s)",
                    pending.size());
        }
        if (!pending.isEmpty()) {
            log.warn("Native metric collection pass exceeded {}; {} instance(s) were not started", passTimeout,
                    pending.size());
        }
    }

    private void submitAvailable(CompletionService<InstanceVO> completionService, ArrayDeque<InstanceVO> pending,
            List<CollectionJob> running, int parallelism, Duration timeout, Instant passDeadline) {
        while (!pending.isEmpty() && running.size() < parallelism) {
            Instant now = Instant.now();
            if (!passDeadline.isAfter(now)) {
                return;
            }
            InstanceVO instance = pending.removeFirst();
            try {
                Future<InstanceVO> future = completionService.submit(() -> {
                    collectClusterMetrics(instance);
                    collectBusinessMetrics(instance);
                    return instance;
                });
                running.add(new CollectionJob(instance, future, now.plus(timeout)));
            } catch (RejectedExecutionException error) {
                pending.addFirst(instance);
                throw error;
            }
        }
    }

    private void completeFinishedJobs(List<CollectionJob> running) throws InterruptedException {
        Iterator<CollectionJob> iterator = running.iterator();
        while (iterator.hasNext()) {
            CollectionJob job = iterator.next();
            if (job.future().isDone()) {
                iterator.remove();
                awaitJob(job);
            }
        }
    }

    private void completeJob(List<CollectionJob> running, Future<InstanceVO> completed) throws InterruptedException {
        Iterator<CollectionJob> iterator = running.iterator();
        while (iterator.hasNext()) {
            CollectionJob job = iterator.next();
            if (job.future() == completed) {
                iterator.remove();
                awaitJob(job);
                return;
            }
        }
    }

    private void awaitJob(CollectionJob job) throws InterruptedException {
        try {
            job.future().get();
        } catch (CancellationException ignored) {
            // Already logged when the job was cancelled.
        } catch (ExecutionException error) {
            log.warn("Native metric collection job failed for instance {}: {}", job.instance().getName(),
                    error.getCause().getMessage());
        }
    }

    private void cancelExpiredJobs(List<CollectionJob> running, Duration timeout, Instant passDeadline,
            Duration passTimeout) {
        Instant now = Instant.now();
        Iterator<CollectionJob> iterator = running.iterator();
        while (iterator.hasNext()) {
            CollectionJob job = iterator.next();
            if (!passDeadline.isAfter(now)) {
                job.future().cancel(true);
                iterator.remove();
                log.warn("Native metric collection pass exceeded {}; cancelling instance {}", passTimeout,
                        job.instance().getName());
            } else if (!job.deadline().isAfter(now)) {
                job.future().cancel(true);
                iterator.remove();
                log.warn("Native metric collection exceeded {} for instance {} and was cancelled", timeout,
                        job.instance().getName());
            }
        }
    }

    private void cancelRunningJobs(List<CollectionJob> running) {
        for (CollectionJob job : running) {
            job.future().cancel(true);
        }
        running.clear();
    }

    private long nextWaitMillis(List<CollectionJob> running, Instant passDeadline) {
        Instant nextDeadline = passDeadline;
        for (CollectionJob job : running) {
            if (job.deadline().isBefore(nextDeadline)) {
                nextDeadline = job.deadline();
            }
        }
        return Math.max(1, Duration.between(Instant.now(), nextDeadline).toMillis());
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

    private record CollectionJob(InstanceVO instance, Future<InstanceVO> future, Instant deadline) {
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
