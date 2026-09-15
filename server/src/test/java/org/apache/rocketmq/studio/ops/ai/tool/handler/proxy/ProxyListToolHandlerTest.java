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
package org.apache.rocketmq.studio.ops.ai.tool.handler.proxy;

import org.apache.rocketmq.studio.cluster.broker.ClusterProvider;
import org.apache.rocketmq.studio.cluster.proxy.ProxyVO;
import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.proxy.ProxyListInput;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver.ManagedCluster;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

@ExtendWith(MockitoExtension.class)
class ProxyListToolHandlerTest {

    @Mock
    private PlatformClusterResolver clusterResolver;

    @Mock
    private ClusterProvider clusterProvider;

    @InjectMocks
    private ProxyListToolHandler handler;

    @Test
    void listsProxiesOfResolvedClusterTest() {
        assertThat(handler.name()).isEqualTo("rmq.proxy.list");
        when(clusterResolver.require("rmq-a")).thenReturn(new ManagedCluster(
                "rmq-a", "instance-a", List.of(), List.of()));
        when(clusterProvider.discoverProxies("instance-a"))
                .thenReturn(List.of(proxy("10.0.0.1:8081")));

        ListOutput<ProxyVO> result = handler.execute(
                new ProxyListInput("rmq-a"), context("instance-a"));

        assertThat(result.items())
                .extracting(ProxyVO::getAddr)
                .containsExactly("10.0.0.1:8081");
    }

    @Test
    void aggregatesAcrossInstancesAndDedupesByAddrTest() {
        when(clusterResolver.manageableInstances()).thenReturn(List.of(
                instance("instance-a"), instance("instance-b")));
        when(clusterProvider.discoverProxies("instance-a"))
                .thenReturn(List.of(proxy("10.0.0.1:8081")));
        when(clusterProvider.discoverProxies("instance-b"))
                .thenReturn(List.of(proxy("10.0.0.1:8081"), proxy("10.0.0.2:8081")));

        ListOutput<ProxyVO> result = handler.execute(
                new ProxyListInput(null), context("instance-a"));

        assertThat(result.items())
                .extracting(ProxyVO::getAddr)
                .containsExactly("10.0.0.1:8081", "10.0.0.2:8081");
    }

    @Test
    void skipsFailingInstancesDuringAggregationTest() {
        when(clusterResolver.manageableInstances()).thenReturn(List.of(
                instance("instance-down"), instance("instance-up")));
        when(clusterProvider.discoverProxies("instance-down"))
                .thenThrow(new IllegalStateException("unreachable"));
        when(clusterProvider.discoverProxies("instance-up"))
                .thenReturn(List.of(proxy("10.0.0.3:8081")));

        ListOutput<ProxyVO> result = handler.execute(
                new ProxyListInput(null), context("instance-a"));

        assertThat(result.items())
                .extracting(ProxyVO::getAddr)
                .containsExactly("10.0.0.3:8081");
    }

    private static InstanceVO instance(String name) {
        return InstanceVO.builder().name(name).vendor(InstanceVendor.APACHE).build();
    }

    private static ProxyVO proxy(String addr) {
        return ProxyVO.builder()
                .addr(addr)
                .status(ClusterStatus.healthy)
                .connections(0)
                .grpcPort(8081)
                .remotingPort(8080)
                .build();
    }
}
