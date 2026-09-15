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
package org.apache.rocketmq.studio.ops.ai.tool.handler.broker;

import org.apache.rocketmq.studio.cluster.broker.BrokerVO;
import org.apache.rocketmq.studio.cluster.broker.ClusterProvider;
import org.apache.rocketmq.studio.common.domain.enums.BrokerStatus;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.broker.BrokerListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver.ManagedBroker;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver.ManagedCluster;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

@ExtendWith(MockitoExtension.class)
class BrokerListToolHandlerTest {

    @Mock
    private PlatformClusterResolver clusterResolver;

    @Mock
    private ClusterProvider clusterProvider;

    @InjectMocks
    private BrokerListToolHandler handler;

    @Test
    void aggregatesAcrossInstancesAndDedupesTest() {
        assertThat(handler.name()).isEqualTo("rmq.broker.list");
        when(clusterResolver.manageableInstances()).thenReturn(List.of(
                instance("instance-a"), instance("instance-b")));
        when(clusterProvider.discoverBrokers("instance-a", null))
                .thenReturn(List.of(broker("broker-a", "10.0.0.1:10911")));
        when(clusterProvider.discoverBrokers("instance-b", null))
                .thenReturn(List.of(
                        broker("broker-a", "10.0.0.1:10911"),
                        broker("broker-b", "10.0.0.2:10911")));

        ListOutput<BrokerVO> result = handler.execute(
                new BrokerListInput(null), context("instance-a"));

        assertThat(result.items())
                .extracting(BrokerVO::getName, BrokerVO::getAddr)
                .containsExactly(
                        tuple("broker-a", "10.0.0.1:10911"),
                        tuple("broker-b", "10.0.0.2:10911"));
    }

    @Test
    void filtersByResolvedClusterTest() {
        when(clusterResolver.require("rmq-a")).thenReturn(new ManagedCluster(
                "rmq-a", "instance-a", List.of(),
                List.of(new ManagedBroker("broker-a", 0L, "10.0.0.1:10911", true, null))));
        when(clusterProvider.discoverBrokers("instance-a", null))
                .thenReturn(List.of(
                        broker("broker-a", "10.0.0.1:10911"),
                        broker("broker-x", "10.0.0.9:10911")));

        ListOutput<BrokerVO> result = handler.execute(
                new BrokerListInput("rmq-a"), context("instance-a"));

        assertThat(result.items())
                .extracting(BrokerVO::getName)
                .containsExactly("broker-a");
    }

    @Test
    void skipsFailingInstancesDuringAggregationTest() {
        when(clusterResolver.manageableInstances()).thenReturn(List.of(
                instance("instance-down"), instance("instance-up")));
        when(clusterProvider.discoverBrokers("instance-down", null))
                .thenThrow(new IllegalStateException("unreachable"));
        when(clusterProvider.discoverBrokers("instance-up", null))
                .thenReturn(List.of(broker("broker-u", "10.0.0.3:10911")));

        ListOutput<BrokerVO> result = handler.execute(
                new BrokerListInput(null), context("instance-a"));

        assertThat(result.items())
                .extracting(BrokerVO::getName)
                .containsExactly("broker-u");
    }

    private static InstanceVO instance(String name) {
        return InstanceVO.builder().name(name).vendor(InstanceVendor.APACHE).build();
    }

    private static BrokerVO broker(String name, String addr) {
        return BrokerVO.builder()
                .name(name)
                .addr(addr)
                .version("V5_5_0")
                .status(BrokerStatus.running)
                .runtimeStatsAvailable(true)
                .build();
    }
}
