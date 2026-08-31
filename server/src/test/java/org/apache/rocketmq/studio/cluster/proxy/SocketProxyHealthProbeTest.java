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

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SocketProxyHealthProbeTest {

    @Test
    void probeShouldReportReachableForListeningAddress() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            SocketProxyHealthProbe probe = new SocketProxyHealthProbe();

            ProxyHealthProbe.ProbeResult result =
                    probe.probe("127.0.0.1", server.getLocalPort(), 2_000);

            assertThat(result.reachable()).isTrue();
            assertThat(result.latencyMs()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void probeShouldReportUnreachableForClosedPort() throws Exception {
        int closedPort;
        try (ServerSocket server = new ServerSocket(0)) {
            closedPort = server.getLocalPort();
        }

        SocketProxyHealthProbe probe = new SocketProxyHealthProbe();

        ProxyHealthProbe.ProbeResult result = probe.probe("127.0.0.1", closedPort, 2_000);

        assertThat(result.reachable()).isFalse();
    }

    @Test
    void resolveAddressShouldReturnAddressForFastResolution() {
        FastResolveProbe probe = new FastResolveProbe();

        InetSocketAddress address =
                probe.resolveAddress("fast-proxy", 8081, System.nanoTime(), 500);

        assertThat(address).isNotNull();
        assertThat(address.getPort()).isEqualTo(8081);
    }

    @Test
    void resolveAddressShouldBoundSlowNameResolution() {
        SlowResolveProbe probe = new SlowResolveProbe();
        long start = System.nanoTime();

        InetSocketAddress address =
                probe.resolveAddress("slow-proxy", 8081, System.nanoTime(), 30);

        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
        assertThat(address).isNull();
        // The slow resolution (300 ms) must not be awaited; the probe budget (30 ms) bounds it.
        assertThat(elapsedMillis).isLessThan(250);
    }

    @Test
    void probeShouldDegradeToUnreachableWhenNameResolutionExceedsBudget() {
        SlowResolveProbe probe = new SlowResolveProbe();
        long start = System.nanoTime();

        ProxyHealthProbe.ProbeResult result = probe.probe("slow-proxy", 8081, 30);

        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
        assertThat(result.reachable()).isFalse();
        assertThat(elapsedMillis).isLessThan(250);
    }

    private static final class FastResolveProbe extends SocketProxyHealthProbe {
        @Override
        InetSocketAddress doResolve(String host, int port) {
            return new InetSocketAddress("127.0.0.1", port);
        }
    }

    private static final class SlowResolveProbe extends SocketProxyHealthProbe {
        @Override
        InetSocketAddress doResolve(String host, int port) {
            try {
                TimeUnit.MILLISECONDS.sleep(300);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return new InetSocketAddress("127.0.0.1", port);
        }
    }
}
