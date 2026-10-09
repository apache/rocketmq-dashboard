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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the TCP reachability contract of {@link SocketProxyHealthProbe} and its {@code ProbeResult}:
 * a listening port is reachable with a non-negative latency, a closed port is unreachable with the
 * -1 sentinel, and the reachable factory clamps a negative latency to zero.
 */
class SocketProxyHealthProbeTest {

    private final SocketProxyHealthProbe probe = new SocketProxyHealthProbe();

    @Test
    void aListeningLocalPortIsReachableWithANonNegativeLatency() throws Exception {
        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            ProxyHealthProbe.ProbeResult result = probe.probe("127.0.0.1", server.getLocalPort(), 2000);
            assertThat(result.reachable()).isTrue();
            assertThat(result.latencyMs()).isGreaterThanOrEqualTo(0L);
        }
    }

    @Test
    void aClosedLocalPortIsUnreachableWithTheSentinelLatency() throws Exception {
        int port;
        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            port = server.getLocalPort();
        }
        ProxyHealthProbe.ProbeResult result = probe.probe("127.0.0.1", port, 2000);
        assertThat(result.reachable()).isFalse();
        assertThat(result.latencyMs()).isEqualTo(-1L);
    }

    @Test
    void theUnreachableFactoryUsesTheSentinelLatency() {
        ProxyHealthProbe.ProbeResult result = ProxyHealthProbe.ProbeResult.unreachable();
        assertThat(result.reachable()).isFalse();
        assertThat(result.latencyMs()).isEqualTo(-1L);
    }

    @Test
    void theReachableFactoryClampsANegativeLatencyToZero() {
        assertThat(ProxyHealthProbe.ProbeResult.reachable(-5L).latencyMs()).isZero();
        assertThat(ProxyHealthProbe.ProbeResult.reachable(-5L).reachable()).isTrue();
        assertThat(ProxyHealthProbe.ProbeResult.reachable(42L).latencyMs()).isEqualTo(42L);
    }
}
