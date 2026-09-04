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

import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.apache.rocketmq.studio.common.domain.enums.ClusterType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.ops.dashboard.ClusterOverviewVO;
import org.apache.rocketmq.studio.ops.dashboard.DashboardDataVO;
import org.apache.rocketmq.studio.ops.dashboard.DashboardService;
import org.apache.rocketmq.studio.ops.dashboard.DashboardStatsVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardSummaryToolHandlerTest {

    @Mock
    private DashboardService dashboardService;

    private DashboardSummaryToolHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DashboardSummaryToolHandler(dashboardService);
    }

    @Test
    void rejectsNullInput() {
        assertThatThrownBy(() -> handler.execute(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("input");
    }

    @Test
    void rejectsMissingCluster() {
        assertThatThrownBy(() -> handler.execute(Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cluster");
    }

    @Test
    void summarizesClusterAndStats() {
        ClusterOverviewVO cluster = ClusterOverviewVO.builder()
                .id("cluster-a").name("prod").type(ClusterType.V4_DIRECT)
                .status(ClusterStatus.healthy).build();
        DashboardDataVO dashboard = DashboardDataVO.builder()
                .clusters(List.of(cluster))
                .stats(DashboardStatsVO.builder().totalClusters(1).totalBrokers(4).build())
                .build();
        when(dashboardService.getDashboard()).thenReturn(dashboard);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) handler.execute(Map.of("cluster", "cluster-a"));

        assertThat(result).containsKey("cluster");
        assertThat(result).containsKey("stats");
        @SuppressWarnings("unchecked")
        Map<String, Object> projected = (Map<String, Object>) result.get("cluster");
        assertThat(projected).containsEntry("name", "prod");
    }
}
