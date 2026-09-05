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

import org.apache.rocketmq.studio.cluster.broker.ClusterService;
import org.apache.rocketmq.studio.cluster.broker.ClusterVO;
import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.apache.rocketmq.studio.common.domain.enums.ClusterType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClusterListToolHandlerTest {

    @Test
    void nullClusterVersionIsEmittedAsBlankString() {
        // The Apache runtime provider reports no cluster version; the projection must not
        // emit a null into the schema-required "version" string.
        ClusterVO cluster = ClusterVO.builder()
                .name("DefaultCluster")
                .type(ClusterType.V4_DIRECT)
                .status(ClusterStatus.healthy)
                .build();
        cluster.setId("DefaultCluster");

        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.listClusters()).thenReturn(List.of(cluster));

        Object output = new ClusterListToolHandler(clusterService).execute(Map.of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) output;
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("id")).isEqualTo("DefaultCluster");
        assertThat(row.get("name")).isEqualTo("DefaultCluster");
        assertThat(row.get("type")).isEqualTo("V4_DIRECT");
        assertThat(row.get("status")).isEqualTo("healthy");
        assertThat(row.get("version")).isEqualTo("");
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
        when(clusterService.listClusters()).thenReturn(List.of(cluster));

        Object output = new ClusterListToolHandler(clusterService).execute(Map.of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) output;
        @SuppressWarnings("unchecked")
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("version")).isEqualTo("V5_3_1");
    }

    @Test
    void reportsItsToolName() {
        ClusterService clusterService = mock(ClusterService.class);
        assertThat(new ClusterListToolHandler(clusterService).name()).isEqualTo("rmq.cluster.list");
    }

    @Test
    @SuppressWarnings("unchecked")
    void projectsEveryClusterInInputOrder() {
        ClusterVO first = ClusterVO.builder().name("A").type(ClusterType.V4_DIRECT)
                .status(ClusterStatus.healthy).build();
        first.setId("cluster-a");
        ClusterVO second = ClusterVO.builder().name("B").type(ClusterType.V5_PROXY_LOCAL)
                .status(ClusterStatus.warning).build();
        second.setId("cluster-b");
        ClusterService clusterService = mock(ClusterService.class);
        when(clusterService.listClusters()).thenReturn(List.of(first, second));

        Object output = new ClusterListToolHandler(clusterService).execute(Map.of());

        List<Map<String, Object>> rows = (List<Map<String, Object>>) output;
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("id")).isEqualTo("cluster-a");
        assertThat(rows.get(1).get("id")).isEqualTo("cluster-b");
        assertThat(rows.get(1).get("type")).isEqualTo("V5_PROXY_LOCAL");
        assertThat(rows.get(1).get("status")).isEqualTo("warning");
    }

    @Test
    void rejectsClustersMissingTypeOrStatus() {
        ClusterService clusterService = mock(ClusterService.class);
        ClusterVO noType = ClusterVO.builder().name("X").status(ClusterStatus.healthy).build();
        noType.setId("no-type");
        when(clusterService.listClusters()).thenReturn(List.of(noType));
        ClusterListToolHandler handler = new ClusterListToolHandler(clusterService);

        assertThatThrownBy(() -> handler.execute(Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("type")
                .hasMessageContaining("no-type");

        ClusterVO noStatus = ClusterVO.builder().name("Y").type(ClusterType.V4_DIRECT).build();
        noStatus.setId("no-status");
        when(clusterService.listClusters()).thenReturn(List.of(noStatus));
        assertThatThrownBy(() -> handler.execute(Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("status");
    }
}
