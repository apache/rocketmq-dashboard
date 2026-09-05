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

import java.net.InetAddress;
import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SocketProxyHealthProbe}, exercised against a real loopback socket so
 * the plain-TCP connect path (the proxy health signal used by topology building) is verified.
 */
class SocketProxyHealthProbeTest {

    private final SocketProxyHealthProbe probe = new SocketProxyHealthProbe();

    @Test
    void reportsReachableWhenATcpServerListensOnThePort() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            ProxyHealthProbe.ProbeResult result = probe.probe("127.0.0.1", server.getLocalPort(), 2_000);

            assertThat(result.reachable()).isTrue();
            assertThat(result.latencyMs()).isGreaterThanOrEqualTo(0L);
        }
    }

    @Test
    void reportsUnreachableWhenThePortIsClosed() throws Exception {
        int closedPort;
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = server.getLocalPort();
        }

        ProxyHealthProbe.ProbeResult result = probe.probe("127.0.0.1", closedPort, 2_000);

        assertThat(result.reachable()).isFalse();
        assertThat(result.latencyMs()).isEqualTo(-1L);
    }

    @Test
    void probeResultFactoriesCarryReachabilityAndLatencySemantics() {
        ProxyHealthProbe.ProbeResult reachable = ProxyHealthProbe.ProbeResult.reachable(-3L);
        assertThat(reachable.reachable()).isTrue();
        assertThat(reachable.latencyMs()).isZero();

        ProxyHealthProbe.ProbeResult unreachable = ProxyHealthProbe.ProbeResult.unreachable();
        assertThat(unreachable.reachable()).isFalse();
        assertThat(unreachable.latencyMs()).isEqualTo(-1L);
    }
}
