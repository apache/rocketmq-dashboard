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
package org.apache.rocketmq.studio.ops.ai.tool.contract.proxy;

import org.apache.rocketmq.studio.cluster.proxy.ProxyTopologyVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link ProxyConfigItem}: the read-only Proxy configuration/reachability snapshot
 * (decision 15). The connections and version fields are reserved for a future proxy read
 * endpoint and must stay absent (null) until then — surfacing them half-populated would
 * read as a measurement that was never taken.
 */
class ProxyConfigItemTest {

    @Test
    void mapsTheReachabilitySnapshot() {
        ProxyTopologyVO topology = ProxyTopologyVO.builder()
                .proxyAddr("10.0.0.1:8080")
                .status("HEALTHY")
                .grpcPort(8081)
                .remotingPort(8080)
                .grpcReachable(true)
                .remotingReachable(false)
                .build();

        ProxyConfigItem item = ProxyConfigItem.from(topology);

        assertThat(item.addr()).isEqualTo("10.0.0.1:8080");
        assertThat(item.status()).isEqualTo("HEALTHY");
        assertThat(item.grpcPort()).isEqualTo(8081);
        assertThat(item.remotingPort()).isEqualTo(8080);
        assertThat(item.grpcReachable()).isTrue();
        assertThat(item.remotingReachable()).isFalse();
    }

    @Test
    void connectionsAndVersionStayAbsentUntilTheReadEndpointExists() {
        ProxyConfigItem item = ProxyConfigItem.from(ProxyTopologyVO.builder().build());

        assertThat(item.connections()).isNull();
        assertThat(item.version()).isNull();
    }
}
