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
}
