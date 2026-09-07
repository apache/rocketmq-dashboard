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
package org.apache.rocketmq.studio.cluster.broker;

import org.apache.rocketmq.studio.cluster.nameserver.NameserverRegistryService;
import org.apache.rocketmq.studio.cluster.nameserver.NameserverRegistryVO;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.apache.rocketmq.studio.provider.apache.RocketMQBrokerConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClusterServiceRegistryTest {

    @Mock
    private ClusterRepository clusterRepository;

    @Mock
    private ClusterProvider clusterProvider;

    @Mock
    private RocketMQBrokerConfigService brokerConfigService;

    @Mock
    private AuditService auditService;

    @Mock
    private NameserverRegistryService registryService;

    @InjectMocks
    private ClusterService clusterService;

    @Test
    void listRegistryClustersShouldProbeConcurrentlyAndSkipFailuresTest() {
        when(registryService.list()).thenReturn(List.of(
                NameserverRegistryVO.builder()
                        .id(1L)
                        .name("rocketmq1")
                        .namesrvAddr("rocketmq1-nameserver:9876")
                        .build(),
                NameserverRegistryVO.builder()
                        .id(2L)
                        .name("rocketmq2")
                        .namesrvAddr("rocketmq2-nameserver:9876")
                        .build()));
        when(clusterProvider.discoverClustersAt("rocketmq1-nameserver:9876")).thenReturn(List.of(
                ClusterVO.builder().id("DefaultCluster").name("DefaultCluster").build()));
        when(clusterProvider.discoverClustersAt("rocketmq2-nameserver:9876"))
                .thenThrow(new BusinessException(502, "unreachable"));

        List<ClusterVO> result = clusterService.listRegistryClusters();

        assertThat(result).hasSize(1);
        ClusterVO cluster = result.get(0);
        assertThat(cluster.getName()).isEqualTo("rocketmq1");
        assertThat(cluster.getNsClusterName()).isEqualTo("DefaultCluster");
        assertThat(cluster.getEndpoint()).isEqualTo("rocketmq1-nameserver:9876");
    }

    @Test
    void listRegistryClustersShouldReturnEmptyWhenRegistryEmptyTest() {
        when(registryService.list()).thenReturn(List.of());

        assertThat(clusterService.listRegistryClusters()).isEmpty();
    }

    @Test
    void listRegistryClustersShouldSkipEntriesWithoutAddressTest() {
        when(registryService.list()).thenReturn(List.of(
                NameserverRegistryVO.builder().id(1L).name("no-addr").namesrvAddr(" ").build()));

        assertThat(clusterService.listRegistryClusters()).isEmpty();
    }

    @Test
    void listRegistryClustersShouldBoundProbeThreadsWhenProbesBlockTest() throws Exception {
        int maxConcurrency = 4;
        when(registryService.list()).thenReturn(List.of(
                NameserverRegistryVO.builder().id(1L).name("ns-1").namesrvAddr("ns-1:9876").build(),
                NameserverRegistryVO.builder().id(2L).name("ns-2").namesrvAddr("ns-2:9876").build(),
                NameserverRegistryVO.builder().id(3L).name("ns-3").namesrvAddr("ns-3:9876").build(),
                NameserverRegistryVO.builder().id(4L).name("ns-4").namesrvAddr("ns-4:9876").build()));

        CountDownLatch block = new CountDownLatch(1);
        AtomicInteger probes = new AtomicInteger();
        when(clusterProvider.discoverClustersAt(anyString())).thenAnswer(invocation -> {
            probes.incrementAndGet();
            try {
                block.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return List.of();
        });

        try (RegistryProbeRunner runner = new RegistryProbeRunner(maxConcurrency, 8, 150)) {
            clusterService.setRegistryProbeRunner(runner);

            // Two refresh rounds with every probe blocked: a cached thread pool would
            // accumulate a fresh blocked thread per entry per round.
            assertThat(clusterService.listRegistryClusters()).isEmpty();
            assertThat(clusterService.listRegistryClusters()).isEmpty();

            // The pool never grows past its bound and both rounds actually ran every probe.
            assertThat(runner.poolSize()).isLessThanOrEqualTo(maxConcurrency);
            assertThat(runner.activeCount()).isLessThanOrEqualTo(maxConcurrency);
            assertThat(probes).hasValue(8);
        } finally {
            block.countDown();
        }
    }

    @Test
    void listRegistryClustersShouldDegradeSaturatedProbeQueueWithoutThrowingTest() throws Exception {
        // One worker plus a one-slot queue: the third concurrent probe must be rejected
        // and degraded to unavailable rather than growing the pool or failing the request.
        when(registryService.list()).thenReturn(List.of(
                NameserverRegistryVO.builder().id(1L).name("ns-1").namesrvAddr("ns-1:9876").build(),
                NameserverRegistryVO.builder().id(2L).name("ns-2").namesrvAddr("ns-2:9876").build(),
                NameserverRegistryVO.builder().id(3L).name("ns-3").namesrvAddr("ns-3:9876").build()));

        CountDownLatch block = new CountDownLatch(1);
        when(clusterProvider.discoverClustersAt(anyString())).thenAnswer(invocation -> {
            try {
                block.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return List.of();
        });

        try (RegistryProbeRunner runner = new RegistryProbeRunner(1, 1, 150)) {
            clusterService.setRegistryProbeRunner(runner);

            assertThatCode(() -> assertThat(clusterService.listRegistryClusters()).isEmpty())
                    .doesNotThrowAnyException();
            assertThat(runner.poolSize()).isLessThanOrEqualTo(1);
        } finally {
            block.countDown();
        }
    }
}
