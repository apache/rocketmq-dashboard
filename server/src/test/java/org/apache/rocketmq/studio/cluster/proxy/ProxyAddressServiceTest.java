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

package org.apache.rocketmq.studio.cluster.proxy;

import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.cluster.broker.ClusterService;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProxyAddressServiceTest {

    @Mock
    private ClusterService clusterService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private OperationAuditService operationAuditService;

    private final ProxyHealthProbe healthProbe = mock(ProxyHealthProbe.class);
    private ProxyAddressService proxyAddressService;

    @BeforeEach
    void setUp() {
        proxyAddressService = new ProxyAddressService(clusterService, healthProbe, restTemplate,
                operationAuditService);
        // Default probe outcome: everything reachable with 1 ms latency.
        when(healthProbe.probe(anyString(), anyInt(), anyInt()))
                .thenReturn(ProxyHealthProbe.ProbeResult.reachable(1L));
    }

    @Test
    void homePageShouldReturnDefaultProxyAddress() {
        ProxyHomeVO home = proxyAddressService.getHomePage();

        assertThat(home.getProxyAddrList()).containsExactly("127.0.0.1:8081");
        assertThat(home.getCurrentProxyAddr()).isEqualTo("127.0.0.1:8081");
    }

    @Test
    void addProxyAddrShouldTrimAndKeepUniqueAddresses() {
        proxyAddressService.addProxyAddr(" 10.0.0.1:8081 ");
        proxyAddressService.addProxyAddr("10.0.0.1:8081");

        ProxyHomeVO home = proxyAddressService.getHomePage();
        assertThat(home.getProxyAddrList()).containsExactly("127.0.0.1:8081", "10.0.0.1:8081");
        assertThat(home.getCurrentProxyAddr()).isEqualTo("127.0.0.1:8081");
        verify(operationAuditService, times(1)).record("ADD_PROXY_ADDRESS", "PROXY", "10.0.0.1:8081",
                null, null, "SUCCESS", null);
    }

    @Test
    void addProxyAddrShouldRejectBlankAddress() {
        assertThatThrownBy(() -> proxyAddressService.addProxyAddr(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("newProxyAddr is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
    }

    @Test
    void addProxyAddrShouldAcceptBracketedIpv6Address() {
        proxyAddressService.addProxyAddr(" [::1]:8081 ");

        ProxyHomeVO home = proxyAddressService.getHomePage();
        assertThat(home.getProxyAddrList()).containsExactly("127.0.0.1:8081", "[::1]:8081");
    }

    @Test
    void addProxyAddrShouldRejectInvalidAddressFormats() {
        List<String> invalidProxyAddrs = List.of(
                "10.0.0.1",
                "10.0.0.1:abc",
                "10.0.0.1:0",
                "10.0.0.1:65536",
                "http://10.0.0.1:8081",
                "10.0.0.1:8081/path",
                "[:::]:8081",
                "[2001:db8::1::2]:8081",
                "[127.0.0.1]:8081"
        );

        for (String invalidProxyAddr : invalidProxyAddrs) {
            assertThatThrownBy(() -> proxyAddressService.addProxyAddr(invalidProxyAddr))
                    .as("invalid proxy address %s", invalidProxyAddr)
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        }
    }

    @Test
    void removeProxyAddrShouldTrimAndRemoveAddress() {
        proxyAddressService.addProxyAddr("10.0.0.1:8081");

        proxyAddressService.removeProxyAddr(" 10.0.0.1:8081 ");

        ProxyHomeVO home = proxyAddressService.getHomePage();
        assertThat(home.getProxyAddrList()).containsExactly("127.0.0.1:8081");
        assertThat(home.getCurrentProxyAddr()).isEqualTo("127.0.0.1:8081");
        verify(operationAuditService).record("REMOVE_PROXY_ADDRESS", "PROXY", "10.0.0.1:8081",
                null, null, "SUCCESS", null);
    }

    @Test
    void auditFailureShouldNotAbortProxyAddressMutation() {
        doThrow(new RuntimeException("audit unavailable")).when(operationAuditService)
                .record("ADD_PROXY_ADDRESS", "PROXY", "10.0.0.1:8081", null, null, "SUCCESS", null);

        proxyAddressService.addProxyAddr("10.0.0.1:8081");

        assertThat(proxyAddressService.getHomePage().getProxyAddrList())
                .containsExactly("127.0.0.1:8081", "10.0.0.1:8081");
    }

    @Test
    void removeProxyAddrShouldSelectNextProxyWhenCurrentIsRemoved() {
        proxyAddressService.removeProxyAddr("127.0.0.1:8081");

        ProxyHomeVO emptyHome = proxyAddressService.getHomePage();
        assertThat(emptyHome.getProxyAddrList()).isEmpty();
        assertThat(emptyHome.getCurrentProxyAddr()).isEmpty();

        proxyAddressService.addProxyAddr("10.0.0.1:8081");
        proxyAddressService.addProxyAddr("10.0.0.2:8081");
        proxyAddressService.removeProxyAddr("10.0.0.1:8081");

        ProxyHomeVO home = proxyAddressService.getHomePage();
        assertThat(home.getProxyAddrList()).containsExactly("10.0.0.2:8081");
        assertThat(home.getCurrentProxyAddr()).isEqualTo("10.0.0.2:8081");
    }

    @Test
    void removeProxyAddrShouldRejectBlankAddress() {
        assertThatThrownBy(() -> proxyAddressService.removeProxyAddr(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("proxyAddr is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
    }

    @Test
    void removeProxyAddrShouldRejectUnknownAddress() {
        assertThatThrownBy(() -> proxyAddressService.removeProxyAddr("10.0.0.1:8081"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Proxy address not found: 10.0.0.1:8081")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));
    }

    @Test
    void reloadConfigShouldRejectLoopbackOutsideTrustedCluster() {
        doThrow(new BusinessException(404, "Proxy not found: 127.0.0.2:8081"))
                .when(clusterService).requireProxy("cluster-1", "127.0.0.2:8081");

        assertThatThrownBy(() -> proxyAddressService.reloadConfig("cluster-1", "127.0.0.2:8081"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Proxy not found: 127.0.0.2:8081")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));

        verifyNoInteractions(restTemplate);
    }

    @Test
    void reloadConfigShouldRejectNonTrustedEndpoint() {
        doThrow(new BusinessException(404, "Proxy not found: 198.51.100.10:8081"))
                .when(clusterService).requireProxy("cluster-1", "198.51.100.10:8081");

        assertThatThrownBy(() -> proxyAddressService.reloadConfig("cluster-1", "198.51.100.10:8081"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Proxy not found: 198.51.100.10:8081")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));

        verifyNoInteractions(restTemplate);
    }

    @Test
    void reloadConfigShouldPostToTrustedDiscoveredProxy() {
        doNothing().when(clusterService).requireProxy("cluster-1", "10.0.0.10:8081");
        when(restTemplate.postForEntity(eq("http://10.0.0.10:8081/admin/reloadConfig"), isNull(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("ok"));

        proxyAddressService.reloadConfig("cluster-1", "10.0.0.10:8081");

        verify(clusterService).requireProxy("cluster-1", "10.0.0.10:8081");
        verify(restTemplate).postForEntity(eq("http://10.0.0.10:8081/admin/reloadConfig"), isNull(), eq(String.class));
        verify(operationAuditService).record("RELOAD_PROXY_CONFIG", "PROXY", "10.0.0.10:8081",
                "cluster-1", null, "SUCCESS", null);
    }

    @Test
    void buildTopologyShouldReportUpPartialAndDownStatus() {
        proxyAddressService.addProxyAddr("10.0.0.2:8081");
        proxyAddressService.addProxyAddr("10.0.0.3:8081");

        // 10.0.0.2: gRPC down, remoting (8080) up → PARTIAL
        when(healthProbe.probe(eq("10.0.0.2"), eq(8081), anyInt()))
                .thenReturn(ProxyHealthProbe.ProbeResult.unreachable());
        when(healthProbe.probe(eq("10.0.0.2"), eq(8080), anyInt()))
                .thenReturn(ProxyHealthProbe.ProbeResult.reachable(2L));
        // 10.0.0.3: both ports down → DOWN
        when(healthProbe.probe(eq("10.0.0.3"), anyInt(), anyInt()))
                .thenReturn(ProxyHealthProbe.ProbeResult.unreachable());

        List<ProxyTopologyVO> topology = proxyAddressService.buildTopology();

        assertThat(topology).hasSize(3);

        ProxyTopologyVO up = topology.get(0);
        assertThat(up.getProxyAddr()).isEqualTo("127.0.0.1:8081");
        assertThat(up.getStatus()).isEqualTo("UP");
        assertThat(up.getGrpcPort()).isEqualTo(8081);
        assertThat(up.getRemotingPort()).isEqualTo(8080);
        assertThat(up.isGrpcReachable()).isTrue();
        assertThat(up.isRemotingReachable()).isTrue();
        assertThat(up.getLatencyMs()).isEqualTo(1L);

        ProxyTopologyVO partial = topology.get(1);
        assertThat(partial.getProxyAddr()).isEqualTo("10.0.0.2:8081");
        assertThat(partial.getStatus()).isEqualTo("PARTIAL");
        assertThat(partial.isGrpcReachable()).isFalse();
        assertThat(partial.isRemotingReachable()).isTrue();

        ProxyTopologyVO down = topology.get(2);
        assertThat(down.getProxyAddr()).isEqualTo("10.0.0.3:8081");
        assertThat(down.getStatus()).isEqualTo("DOWN");
        assertThat(down.isGrpcReachable()).isFalse();
        assertThat(down.isRemotingReachable()).isFalse();
        assertThat(down.getLatencyMs()).isEqualTo(-1L);
    }

    @Test
    void buildTopologyShouldNotDeriveRemotingPortForNonStandardGrpcPort() {
        proxyAddressService.addProxyAddr("10.0.0.4:8443");

        List<ProxyTopologyVO> topology = proxyAddressService.buildTopology();

        ProxyTopologyVO custom = topology.stream()
                .filter(node -> node.getProxyAddr().equals("10.0.0.4:8443"))
                .findFirst()
                .orElseThrow();
        assertThat(custom.getStatus()).isEqualTo("UP");
        assertThat(custom.getRemotingPort()).isNull();
        assertThat(custom.isRemotingReachable()).isFalse();
    }

    @Test
    void buildTopologyShouldProbeAddressesInParallelTest() {
        // Every probe blocks 300 ms; 4 addresses mean 8 probes, which would take
        // at least 2.4 s if probed serially but only one wave (~300 ms) in parallel.
        ProxyHealthProbe slowProbe = (host, port, timeoutMillis) -> {
            try {
                Thread.sleep(300L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ProxyHealthProbe.ProbeResult.reachable(300L);
        };
        ProxyAddressService service = new ProxyAddressService(clusterService, slowProbe);
        service.addProxyAddr("10.0.0.11:8081");
        service.addProxyAddr("10.0.0.12:8081");
        service.addProxyAddr("10.0.0.13:8081");

        long start = System.nanoTime();
        List<ProxyTopologyVO> topology = service.buildTopology();
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        assertThat(topology).hasSize(4);
        assertThat(elapsedMillis).isLessThan(2_000L);
    }

    @Test
    void buildTopologyShouldDegradeHungProbesWithinTotalBudgetTest() {
        // 10.0.0.21: gRPC probe hangs, remoting probe returns → PARTIAL.
        // 10.0.0.22: both probes hang → DOWN. Neither may exceed the 500 ms budget.
        ProxyHealthProbe selectiveProbe = (host, port, timeoutMillis) -> {
            boolean hang = "10.0.0.21".equals(host) ? port == 8081 : "10.0.0.22".equals(host);
            if (hang) {
                try {
                    Thread.sleep(1_000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return ProxyHealthProbe.ProbeResult.reachable(1L);
        };
        java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newFixedThreadPool(4);
        ProxyAddressService service = new ProxyAddressService(clusterService, selectiveProbe, executor, 500L);
        service.addProxyAddr("10.0.0.21:8081");
        service.addProxyAddr("10.0.0.22:8081");

        long start = System.nanoTime();
        List<ProxyTopologyVO> topology = service.buildTopology();
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
        executor.shutdownNow();

        assertThat(elapsedMillis).isLessThan(3_000L);
        assertThat(topology).hasSize(3);

        ProxyTopologyVO partial = topology.get(1);
        assertThat(partial.getProxyAddr()).isEqualTo("10.0.0.21:8081");
        assertThat(partial.getStatus()).isEqualTo("PARTIAL");
        assertThat(partial.isGrpcReachable()).isFalse();
        assertThat(partial.isRemotingReachable()).isTrue();
        assertThat(partial.getLatencyMs()).isEqualTo(-1L);

        ProxyTopologyVO down = topology.get(2);
        assertThat(down.getProxyAddr()).isEqualTo("10.0.0.22:8081");
        assertThat(down.getStatus()).isEqualTo("DOWN");
        assertThat(down.isGrpcReachable()).isFalse();
        assertThat(down.isRemotingReachable()).isFalse();
    }

    @Test
    void buildTopologyShouldSkipQueuedProbeAfterBudgetCancelsItsFuture() throws Exception {
        // Single probe thread: 10.9.0.1's probe runs first and blocks on the gate, so
        // 10.9.0.2's probe task sits in the queue past the 300 ms budget. When the gate
        // opens, the queued task must see its cancelled future and skip the socket work
        // instead of pinning a pool thread with a probe nobody waits for.
        java.util.concurrent.CountDownLatch gate = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger queuedHostProbes =
                new java.util.concurrent.atomic.AtomicInteger();
        ProxyHealthProbe gatedProbe = (host, port, timeoutMillis) -> {
            if ("10.9.0.1".equals(host)) {
                try {
                    gate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else if ("10.9.0.2".equals(host)) {
                queuedHostProbes.incrementAndGet();
            }
            return ProxyHealthProbe.ProbeResult.reachable(1L);
        };
        java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newSingleThreadExecutor();
        ProxyAddressService service =
                new ProxyAddressService(clusterService, gatedProbe, executor, 300L);
        service.addProxyAddr("10.9.0.1:9001");
        service.addProxyAddr("10.9.0.2:9001");

        List<ProxyTopologyVO> topology = service.buildTopology();

        // Budget elapsed with 10.9.0.1 gated and 10.9.0.2 still queued, so both are DOWN.
        assertThat(topology.get(1).getProxyAddr()).isEqualTo("10.9.0.1:9001");
        assertThat(topology.get(1).getStatus()).isEqualTo("DOWN");
        assertThat(topology.get(2).getProxyAddr()).isEqualTo("10.9.0.2:9001");
        assertThat(topology.get(2).getStatus()).isEqualTo("DOWN");

        // Release the gate: 10.9.0.1's task finishes and the queued 10.9.0.2 task runs.
        gate.countDown();
        java.util.concurrent.CountDownLatch drained = new java.util.concurrent.CountDownLatch(1);
        executor.execute(drained::countDown);
        assertThat(drained.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        executor.shutdownNow();

        // The queued probe's future was cancelled by the budget, so its socket work was skipped.
        assertThat(queuedHostProbes).hasValue(0);
    }
}
