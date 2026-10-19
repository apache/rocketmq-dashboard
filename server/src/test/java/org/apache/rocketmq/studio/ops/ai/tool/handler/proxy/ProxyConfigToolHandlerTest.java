/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.studio.ops.ai.tool.handler.proxy;

import org.apache.rocketmq.studio.cluster.broker.ClusterProvider;
import org.apache.rocketmq.studio.cluster.proxy.ProxyAddressService;
import org.apache.rocketmq.studio.cluster.proxy.ProxyTopologyVO;
import org.apache.rocketmq.studio.cluster.proxy.ProxyVO;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.proxy.ProxyConfigInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.proxy.ProxyConfigItem;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver.ManagedCluster;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

@ExtendWith(MockitoExtension.class)
class ProxyConfigToolHandlerTest {

    @Mock
    private PlatformClusterResolver clusterResolver;

    @Mock
    private ClusterProvider clusterProvider;

    @Mock
    private ProxyAddressService proxyAddressService;

    @InjectMocks
    private ProxyConfigToolHandler handler;

    @Test
    void snapshotsTopologyForTheResolvedInstancesProxiesTest() {
        assertThat(handler.name()).isEqualTo("rmq.proxy.config");
        stubOwnedCluster();
        when(clusterProvider.discoverProxies("instance-a")).thenReturn(List.of(
                proxy("127.0.0.1:8081"), proxy("10.0.0.1:8081"), proxy(" 10.0.0.1:8081 ")));
        when(proxyAddressService.buildTopology(List.of("127.0.0.1:8081", "10.0.0.1:8081")))
                .thenReturn(List.of(
                        topology("127.0.0.1:8081", "UP", true, true),
                        topology("10.0.0.1:8081", "DOWN", false, false)));

        ListOutput<ProxyConfigItem> result = handler.execute(
                new ProxyConfigInput("rmq-a", null), context("instance-a"));

        // The probe set is exactly what the owning instance discovered — deduplicated and trimmed —
        // never the global registry that also carries other instances' addresses.
        verify(proxyAddressService).buildTopology(List.of("127.0.0.1:8081", "10.0.0.1:8081"));
        assertThat(result.items()).hasSize(2);
        ProxyConfigItem up = result.items().getFirst();
        assertThat(up.addr()).isEqualTo("127.0.0.1:8081");
        assertThat(up.status()).isEqualTo("UP");
        assertThat(up.grpcReachable()).isTrue();
        assertThat(up.remotingReachable()).isTrue();
        assertThat(up.grpcPort()).isEqualTo(8081);
        assertThat(up.remotingPort()).isEqualTo(8080);
        assertThat(up.connections()).isNull();
        assertThat(up.version()).isNull();
        assertThat(result.items().get(1).status()).isEqualTo("DOWN");
    }

    @Test
    void filtersSnapshotByAddrWithinTheScopedSetTest() {
        stubOwnedCluster();
        when(clusterProvider.discoverProxies("instance-a"))
                .thenReturn(List.of(proxy("127.0.0.1:8081")));
        when(proxyAddressService.buildTopology(List.of("127.0.0.1:8081")))
                .thenReturn(List.of(topology("127.0.0.1:8081", "UP", true, true)));

        ListOutput<ProxyConfigItem> result = handler.execute(
                new ProxyConfigInput("rmq-a", "127.0.0.1:8081"), context("instance-a"));

        assertThat(result.items()).singleElement()
                .extracting(ProxyConfigItem::addr)
                .isEqualTo("127.0.0.1:8081");
    }

    @Test
    void anAddrOutsideTheScopedSetFiltersToNothingTest() {
        // 10.0.0.9 belongs to another instance's registry. The filter runs against the scoped
        // snapshot only, so it cannot pull that row in — before, the global registry answered.
        stubOwnedCluster();
        when(clusterProvider.discoverProxies("instance-a"))
                .thenReturn(List.of(proxy("127.0.0.1:8081")));
        when(proxyAddressService.buildTopology(List.of("127.0.0.1:8081")))
                .thenReturn(List.of(topology("127.0.0.1:8081", "UP", true, true)));

        ListOutput<ProxyConfigItem> result = handler.execute(
                new ProxyConfigInput("rmq-a", "10.0.0.9:8081"), context("instance-a"));

        assertThat(result.items()).isEmpty();
    }

    @Test
    void rejectsUnownedClusterTest() {
        when(clusterResolver.require("rmq-x"))
                .thenThrow(new BusinessException(404, "Cluster not found: rmq-x"));

        assertThatThrownBy(() -> handler.execute(
                new ProxyConfigInput("rmq-x", null), context("instance-a")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cluster not found: rmq-x");
        verifyNoInteractions(proxyAddressService, clusterProvider);
    }

    private void stubOwnedCluster() {
        when(clusterResolver.require("rmq-a")).thenReturn(new ManagedCluster(
                "rmq-a", "instance-a", List.of(), List.of()));
    }

    private static ProxyVO proxy(String addr) {
        return ProxyVO.builder().addr(addr).build();
    }

    private static ProxyTopologyVO topology(
            String addr, String status, boolean grpcReachable, boolean remotingReachable) {
        return ProxyTopologyVO.builder()
                .proxyAddr(addr)
                .status(status)
                .grpcPort(8081)
                .remotingPort(8080)
                .grpcReachable(grpcReachable)
                .remotingReachable(remotingReachable)
                .latencyMs(grpcReachable ? 3L : -1L)
                .build();
    }
}
