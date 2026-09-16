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
package org.apache.rocketmq.studio.ops.ai.tool;

import org.apache.rocketmq.studio.ops.ai.tool.contract.cluster.ClusterListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.cluster.ClusterListItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.handler.cluster.ClusterListToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver.ManagedBroker;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver.ManagedCluster;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver;
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
class ClusterListToolHandlerTest {

    @Mock
    private PlatformClusterResolver clusterResolver;

    @InjectMocks
    private ClusterListToolHandler handler;

    @Test
    void aggregatesPhysicalClusterBrokerRowsTest() {
        assertThat(handler.name()).isEqualTo("rmq.cluster.list");
        ManagedCluster cluster = new ManagedCluster("rmq-a", "instance-a", List.of("ns-a:9876"),
                List.of(
                        new ManagedBroker("broker-a", 0L, "10.0.0.1:10911", true, "V5_5_0"),
                        new ManagedBroker("broker-a", 1L, "10.0.0.2:10911", false, null)));
        when(clusterResolver.scanWithBrokerVersions()).thenReturn(List.of(cluster));

        ListOutput<ClusterListItem> output = handler.execute(
                new ClusterListInput(null), context("instance-a"));

        assertThat(output.items())
                .extracting(ClusterListItem::cluster, ClusterListItem::address,
                        ClusterListItem::brokerName, ClusterListItem::brokerId, ClusterListItem::version)
                .containsExactly(
                        tuple("rmq-a", "10.0.0.1:10911", "broker-a", 0L, "V5_5_0"),
                        tuple("rmq-a", "10.0.0.2:10911", "broker-a", 1L, null));
    }

    @Test
    void filtersClustersByStatusTest() {
        ManagedCluster healthy = new ManagedCluster("rmq-healthy", "instance-a", List.of(),
                List.of(new ManagedBroker("broker-h", 0L, "10.0.0.1:10911", true, "V5_5_0")));
        ManagedCluster degraded = new ManagedCluster("rmq-degraded", "instance-b", List.of(),
                List.of(new ManagedBroker("broker-d", 0L, "10.0.0.2:10911", true, null)));
        when(clusterResolver.scanWithBrokerVersions()).thenReturn(List.of(healthy, degraded));

        ListOutput<ClusterListItem> output = handler.execute(
                new ClusterListInput("HEALTHY"), context("instance-a"));

        assertThat(output.items())
                .extracting(ClusterListItem::cluster)
                .containsExactly("rmq-healthy");
    }
}
