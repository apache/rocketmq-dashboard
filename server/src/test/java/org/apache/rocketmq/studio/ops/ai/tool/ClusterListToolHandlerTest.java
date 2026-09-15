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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.ops.ai.tool.handler.cluster.ClusterListToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.contract.cluster.ClusterListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.cluster.ClusterListItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;

import org.apache.rocketmq.studio.cluster.broker.ClusterService;
import org.apache.rocketmq.studio.cluster.broker.ClusterVO;
import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.apache.rocketmq.studio.common.domain.enums.ClusterType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

class ClusterListToolHandlerTest {

    @Test
    void nullClusterVersionIsOmitted() {
        ClusterVO cluster = ClusterVO.builder()
                .name("DefaultCluster")
                .type(ClusterType.V4_DIRECT)
                .status(ClusterStatus.healthy)
                .build();
        cluster.setId("DefaultCluster");

        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.listClusters("instance-a")).thenReturn(List.of(cluster));

        ListOutput<ClusterListItem> output =
                new ClusterListToolHandler(clusterService).execute(
                        new ClusterListInput("DefaultCluster", null), context("instance-a"));

        assertThat(output.items()).hasSize(1);
        ClusterListItem row = output.items().getFirst();
        assertThat(row.id()).isEqualTo("DefaultCluster");
        assertThat(row.name()).isEqualTo("DefaultCluster");
        assertThat(row.type()).isEqualTo(ClusterType.V4_DIRECT);
        assertThat(row.status()).isEqualTo(ClusterStatus.healthy);
        assertThat(row.version()).isNull();
        assertThat(new ObjectMapper().convertValue(row, Map.class))
                .doesNotContainKey("version");
    }

    @Test
    void populatedClusterVersionIsPassedThrough() {
        ClusterVO cluster = ClusterVO.builder()
                .name("VersionedCluster")
                .type(ClusterType.V4_DIRECT)
                .status(ClusterStatus.healthy)
                .version("V5_3_1")
                .build();
        cluster.setId("VersionedCluster");

        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.listClusters("instance-a")).thenReturn(List.of(cluster));

        ListOutput<ClusterListItem> output =
                new ClusterListToolHandler(clusterService).execute(
                        new ClusterListInput("VersionedCluster", null), context("instance-a"));

        assertThat(output.items().getFirst().version()).isEqualTo("V5_3_1");
    }

    @Test
    void filtersClustersByStatus() {
        ClusterVO healthy = ClusterVO.builder()
                .name("HealthyCluster")
                .type(ClusterType.V4_DIRECT)
                .status(ClusterStatus.healthy)
                .build();
        healthy.setId("HealthyCluster");
        ClusterVO offline = ClusterVO.builder()
                .name("OfflineCluster")
                .type(ClusterType.V4_DIRECT)
                .status(ClusterStatus.offline)
                .build();
        offline.setId("OfflineCluster");

        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.listClusters("instance-a")).thenReturn(List.of(healthy, offline));

        ListOutput<ClusterListItem> output =
                new ClusterListToolHandler(clusterService).execute(
                        new ClusterListInput("OfflineCluster", "OFFLINE"), context("instance-a"));

        assertThat(output.items()).singleElement()
                .extracting(ClusterListItem::id)
                .isEqualTo("OfflineCluster");
    }
}
