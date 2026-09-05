/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.cluster.broker;

import org.apache.rocketmq.studio.cluster.nameserver.NameserverRegistryVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RegistryProbeRunnerTest {

    @Test
    void startsIndependentRegistryProbesConcurrently() throws Exception {
        CountDownLatch probesStarted = new CountDownLatch(2);
        CountDownLatch releaseProbes = new CountDownLatch(1);
        List<NameserverRegistryVO> entries = List.of(
                NameserverRegistryVO.builder().name("registry-a").namesrvAddr("a:9876").build(),
                NameserverRegistryVO.builder().name("registry-b").namesrvAddr("b:9876").build());

        try (RegistryProbeRunner runner = new RegistryProbeRunner(2, 2, 2_000)) {
            CompletableFuture<List<ClusterVO>> result = CompletableFuture.supplyAsync(() ->
                    runner.probeAll(entries, entry -> {
                        probesStarted.countDown();
                        releaseProbes.await();
                        return List.of(ClusterVO.builder().id(entry.getName()).build());
                    }));

            assertThat(probesStarted.await(1, TimeUnit.SECONDS)).isTrue();
            releaseProbes.countDown();
            assertThat(result.get(1, TimeUnit.SECONDS))
                    .extracting(ClusterVO::getId)
                    .containsExactly("registry-a", "registry-b");
        }
    }

    @Test
    void failingProbeDegradesToEmptyWithoutAffectingOthers() throws Exception {
        List<NameserverRegistryVO> entries = List.of(
                NameserverRegistryVO.builder().name("registry-a").namesrvAddr("a:9876").build(),
                NameserverRegistryVO.builder().name("registry-b").namesrvAddr("b:9876").build());

        try (RegistryProbeRunner runner = new RegistryProbeRunner(2, 2, 2_000)) {
            List<ClusterVO> result = runner.probeAll(entries, entry -> {
                if ("registry-a".equals(entry.getName())) {
                    throw new IllegalStateException("nameserver unavailable");
                }
                return List.of(ClusterVO.builder().id(entry.getName()).build());
            });

            assertThat(result).extracting(ClusterVO::getId).containsExactly("registry-b");
        }
    }

    @Test
    void emptyEntryListCompletesWithoutSubmitting() throws Exception {
        try (RegistryProbeRunner runner = new RegistryProbeRunner(2, 2, 2_000)) {
            assertThat(runner.probeAll(List.of(), entry -> List.of())).isEmpty();
        }
    }

    @Test
    void blockedProbePastDeadlineIsCancelledAndDegradesToEmpty() throws Exception {
        CountDownLatch releaseProbe = new CountDownLatch(1);
        List<NameserverRegistryVO> entries = List.of(
                NameserverRegistryVO.builder().name("registry-a").namesrvAddr("a:9876").build());

        try (RegistryProbeRunner runner = new RegistryProbeRunner(1, 1, 150)) {
            long startedAt = System.currentTimeMillis();
            List<ClusterVO> result = runner.probeAll(entries, entry -> {
                try {
                    releaseProbe.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                return List.of(ClusterVO.builder().id(entry.getName()).build());
            });
            long elapsed = System.currentTimeMillis() - startedAt;

            assertThat(result).isEmpty();
            assertThat(elapsed).isGreaterThanOrEqualTo(150L);
        } finally {
            releaseProbe.countDown();
        }
    }

    @Test
    void saturatedQueueDegradesRejectedEntryToEmpty() throws Exception {
        CountDownLatch blockFirstProbe = new CountDownLatch(1);
        CountDownLatch firstProbeStarted = new CountDownLatch(1);
        List<NameserverRegistryVO> entries = List.of(
                NameserverRegistryVO.builder().name("registry-a").namesrvAddr("a:9876").build(),
                NameserverRegistryVO.builder().name("registry-b").namesrvAddr("b:9876").build(),
                NameserverRegistryVO.builder().name("registry-c").namesrvAddr("c:9876").build());

        try (RegistryProbeRunner runner = new RegistryProbeRunner(1, 1, 2_000)) {
            CompletableFuture<List<ClusterVO>> result = CompletableFuture.supplyAsync(() ->
                    runner.probeAll(entries, entry -> {
                        if ("registry-a".equals(entry.getName())) {
                            firstProbeStarted.countDown();
                            blockFirstProbe.await();
                        }
                        return List.of(ClusterVO.builder().id(entry.getName()).build());
                    }));

            assertThat(firstProbeStarted.await(1, TimeUnit.SECONDS)).isTrue();
            // Give the rejected submission time to degrade before releasing the worker.
            Thread.sleep(200);
            blockFirstProbe.countDown();

            // One worker plus one queue slot absorb the first two entries; the third
            // must degrade to empty instead of growing the pool or failing the call.
            assertThat(result.get(1, TimeUnit.SECONDS))
                    .extracting(ClusterVO::getId)
                    .containsExactly("registry-a", "registry-b");
            assertThat(runner.poolSize()).isLessThanOrEqualTo(1);
        } finally {
            blockFirstProbe.countDown();
        }
    }
}
