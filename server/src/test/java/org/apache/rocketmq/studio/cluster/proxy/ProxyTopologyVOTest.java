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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the proxy topology row of {@link ProxyTopologyVO}: the registered address, the derived
 * three-state status, the gRPC/remoting reachability pair, the derivable remoting port
 * ({@code grpcPort - 1}, null when not derivable) and the -1 latency sentinel.
 */
class ProxyTopologyVOTest {

    @Test
    void aReachableProxyRoundTrips() {
        ProxyTopologyVO row = ProxyTopologyVO.builder()
                .proxyAddr("10.0.0.1:8080")
                .status("UP")
                .grpcPort(8080)
                .remotingPort(8079)
                .grpcReachable(true)
                .remotingReachable(true)
                .latencyMs(3L)
                .build();

        assertThat(row.getProxyAddr()).isEqualTo("10.0.0.1:8080");
        assertThat(row.getStatus()).isEqualTo("UP");
        assertThat(row.getGrpcPort()).isEqualTo(8080);
        // the remoting port is derived as grpcPort - 1
        assertThat(row.getRemotingPort()).isEqualTo(8079);
        assertThat(row.isGrpcReachable()).isTrue();
        assertThat(row.isRemotingReachable()).isTrue();
        assertThat(row.getLatencyMs()).isEqualTo(3L);
    }

    @Test
    void aDownProxyCarriesTheSentinelLatency() {
        ProxyTopologyVO row = ProxyTopologyVO.builder()
                .proxyAddr("10.0.0.2:8080")
                .status("DOWN")
                .grpcPort(8080)
                .remotingPort(null)
                .grpcReachable(false)
                .remotingReachable(false)
                .latencyMs(-1L)
                .build();

        assertThat(row.getStatus()).isEqualTo("DOWN");
        assertThat(row.getRemotingPort()).isNull();
        assertThat(row.isGrpcReachable()).isFalse();
        assertThat(row.getLatencyMs()).isEqualTo(-1L);
    }
}
