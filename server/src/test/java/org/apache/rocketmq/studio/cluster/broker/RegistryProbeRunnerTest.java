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
import java.util.concurrent.atomic.AtomicInteger;

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
    void emptyEntriesShouldProbeNothing() {
        AtomicInteger invocations = new AtomicInteger();
        try (RegistryProbeRunner runner = new RegistryProbeRunner(2, 2, 2_000)) {
            assertThat(runner.probeAll(List.of(), entry -> {
                invocations.incrementAndGet();
                return List.of();
            })).isEmpty();
            assertThat(invocations).hasValue(0);
        }
    }

    @Test
    void failingProbeDegradesOnlyItsOwnEntry() {
        try (RegistryProbeRunner runner = new RegistryProbeRunner(2, 2, 2_000)) {
            List<ClusterVO> result = runner.probeAll(List.of(
                    NameserverRegistryVO.builder().name("registry-a").namesrvAddr("a:9876").build(),
                    NameserverRegistryVO.builder().name("registry-b").namesrvAddr("b:9876").build()),
                    entry -> {
                        if (entry.getName().equals("registry-a")) {
                            throw new IllegalStateException("nameserver a unreachable");
                        }
                        return List.of(ClusterVO.builder().id("registry-b").build());
                    });

            assertThat(result).extracting(ClusterVO::getId).containsExactly("registry-b");
        }
    }

    @Test
    void timedOutProbeReturnsEmptyWithoutThrowing() {
        CountDownLatch neverRelease = new CountDownLatch(1);
        try (RegistryProbeRunner runner = new RegistryProbeRunner(2, 2, 150)) {
            List<ClusterVO> result = runner.probeAll(List.of(
                    NameserverRegistryVO.builder().name("registry-a").namesrvAddr("a:9876").build()),
                    entry -> {
                        neverRelease.await();
                        return List.of(ClusterVO.builder().id("registry-a").build());
                    });

            assertThat(result).isEmpty();
        }
    }
}
