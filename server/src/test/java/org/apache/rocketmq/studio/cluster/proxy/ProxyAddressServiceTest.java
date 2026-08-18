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

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProxyAddressServiceTest {

    private final ProxyHealthProbe healthProbe = mock(ProxyHealthProbe.class);
    private final ProxyAddressService proxyAddressService = new ProxyAddressService(healthProbe);

    @BeforeEach
    void setUp() {
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
                "10.0.0.1:8081/path"
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
    void reloadConfigShouldRejectUnregisteredAddressTest() {
        assertThatThrownBy(() -> proxyAddressService.reloadConfig("10.0.0.1:8081"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("addr is not a registered proxy address")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
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
        ProxyAddressService service = new ProxyAddressService(slowProbe);
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
                    Thread.sleep(5_000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return ProxyHealthProbe.ProbeResult.reachable(1L);
        };
        java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newFixedThreadPool(4);
        ProxyAddressService service = new ProxyAddressService(selectiveProbe, executor, 500L);
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
}
