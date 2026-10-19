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

import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link ProxyVO}, one row of the proxy list: its address, the health verdict, the
 * connection count and the two ports (gRPC and remoting) the clients connect through. The
 * proxy table renders exactly these columns, so all construction paths must agree and the
 * no-args path must read as an empty row rather than a defaulted one.
 */
class ProxyVOTest {

    @Test
    void aProxyCarriesItsAddressHealthAndPorts() {
        ProxyVO proxy = ProxyVO.builder()
                .addr("10.0.0.1:8080")
                .status(ClusterStatus.healthy)
                .connections(42)
                .grpcPort(8081)
                .remotingPort(8082)
                .build();

        assertThat(proxy.getAddr()).isEqualTo("10.0.0.1:8080");
        assertThat(proxy.getStatus()).isEqualTo(ClusterStatus.healthy);
        assertThat(proxy.getConnections()).isEqualTo(42);
        assertThat(proxy.getGrpcPort()).isEqualTo(8081);
        assertThat(proxy.getRemotingPort()).isEqualTo(8082);
    }

    @Test
    void allConstructionPathsAgree() {
        ProxyVO built = ProxyVO.builder()
                .addr("10.0.0.1:8080")
                .status(ClusterStatus.offline)
                .connections(0)
                .grpcPort(8081)
                .remotingPort(8082)
                .build();
        ProxyVO allArgs = new ProxyVO("10.0.0.1:8080", ClusterStatus.offline, 0, 8081, 8082);
        ProxyVO defaulted = new ProxyVO();
        defaulted.setAddr("10.0.0.1:8080");
        defaulted.setStatus(ClusterStatus.offline);
        defaulted.setConnections(0);
        defaulted.setGrpcPort(8081);
        defaulted.setRemotingPort(8082);

        assertThat(built).isEqualTo(allArgs).isEqualTo(defaulted);

        ProxyVO empty = new ProxyVO();
        assertThat(empty.getAddr()).isNull();
        assertThat(empty.getStatus()).isNull();
        assertThat(empty.getConnections()).isZero();
        assertThat(empty.getGrpcPort()).isZero();
        assertThat(empty.getRemotingPort()).isZero();
    }
}
