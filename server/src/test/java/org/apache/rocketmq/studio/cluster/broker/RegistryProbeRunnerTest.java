/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.cluster.broker;

import org.apache.rocketmq.studio.cluster.nameserver.NameserverRegistryVO;
import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
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
    void degradesFailingProbeToUnavailableRow() {
        List<NameserverRegistryVO> entries = List.of(
                NameserverRegistryVO.builder().name("registry-a").namesrvAddr("a:9876").build(),
                NameserverRegistryVO.builder().name("registry-b").namesrvAddr("b:9876").build());

        try (RegistryProbeRunner runner = new RegistryProbeRunner(2, 2, 2_000)) {
            List<ClusterVO> result = runner.probeAll(entries, entry -> {
                if ("registry-b".equals(entry.getName())) {
                    throw new IllegalStateException("connection refused");
                }
                return List.of(ClusterVO.builder().id("DefaultCluster").build());
            });

            // A probe that throws must leave a visible unavailable row behind. An empty
            // result makes the entry indistinguishable from one that was never registered.
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getId()).isEqualTo("DefaultCluster");
            assertUnavailableRow(result.get(1), "registry-b", "b:9876");
        }
    }

    @Test
    void degradesTimedOutProbeToUnavailableRow() throws Exception {
        NameserverRegistryVO entry = NameserverRegistryVO.builder()
                .name("registry-a").namesrvAddr("a:9876").build();
        CountDownLatch release = new CountDownLatch(1);

        try (RegistryProbeRunner runner = new RegistryProbeRunner(1, 1, 150)) {
            List<ClusterVO> result = runner.probeAll(List.of(entry), probed -> {
                // Hangs past the deadline like an unresponsive (half-dead) NameServer.
                release.await();
                return List.of(ClusterVO.builder().id("DefaultCluster").build());
            });

            // The probe that never answers must still be reported as unavailable rather
            // than silently disappearing from the aggregated result.
            assertThat(result).hasSize(1);
            assertUnavailableRow(result.get(0), "registry-a", "a:9876");
        } finally {
            release.countDown();
        }
    }

    @Test
    void degradesRejectedProbeToUnavailableRowWhenQueueSaturated() throws Exception {
        List<NameserverRegistryVO> entries = List.of(
                NameserverRegistryVO.builder().name("registry-a").namesrvAddr("a:9876").build(),
                NameserverRegistryVO.builder().name("registry-b").namesrvAddr("b:9876").build(),
                NameserverRegistryVO.builder().name("registry-c").namesrvAddr("c:9876").build());
        CountDownLatch workerBusy = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (RegistryProbeRunner runner = new RegistryProbeRunner(1, 1, 2_000)) {
            CompletableFuture<List<ClusterVO>> result = CompletableFuture.supplyAsync(() ->
                    runner.probeAll(entries, entry -> {
                        if ("registry-c".equals(entry.getName())) {
                            return List.of(ClusterVO.builder().id("registry-c").build());
                        }
                        workerBusy.countDown();
                        // Holds the only worker (registry-a) and the only queue slot (registry-b).
                        release.await();
                        return List.of(ClusterVO.builder().id(entry.getName()).build());
                    }));

            assertThat(workerBusy.await(1, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            List<ClusterVO> rows = result.get(1, TimeUnit.SECONDS);

            // The third probe is rejected by the saturated pool. Growing the pool was ruled
            // out; dropping the entry was the bug — it must be reported as unavailable.
            assertThat(rows).hasSize(3);
            assertThat(rows).anySatisfy(row -> assertUnavailableRow(row, "registry-c", "c:9876"));
        } finally {
            release.countDown();
        }
    }

    private static void assertUnavailableRow(ClusterVO row, String name, String endpoint) {
        assertThat(row.getName()).isEqualTo(name);
        assertThat(row.getEndpoint()).isEqualTo(endpoint);
        assertThat(row.getStatus()).isEqualTo(ClusterStatus.warning);
        assertThat(row.getBrokers()).isEmpty();
        assertThat(row.getNsClusterName()).isNull();
    }
}
