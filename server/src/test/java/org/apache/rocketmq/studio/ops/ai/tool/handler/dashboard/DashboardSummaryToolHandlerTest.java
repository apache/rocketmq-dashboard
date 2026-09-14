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
package org.apache.rocketmq.studio.ops.ai.tool.handler.dashboard;

import org.apache.rocketmq.studio.ops.ai.tool.contract.ops.DashboardSummaryInput;
import org.apache.rocketmq.studio.ops.dashboard.ClusterOverviewVO;
import org.apache.rocketmq.studio.ops.dashboard.DashboardDataVO;
import org.apache.rocketmq.studio.ops.dashboard.DashboardService;
import org.apache.rocketmq.studio.ops.dashboard.DashboardStatsVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardSummaryToolHandlerTest {

    @Test
    void summaryAggregatesEveryInstanceOfTheDeploymentTest() {
        DashboardService dashboardService = mock(DashboardService.class);
        DashboardSummaryToolHandler handler = new DashboardSummaryToolHandler(dashboardService);
        DashboardDataVO dashboard = DashboardDataVO.builder()
                .clusters(List.of(
                        ClusterOverviewVO.builder().id("instance-a/cluster-a").build(),
                        ClusterOverviewVO.builder().id("instance-b/cluster-b").build()))
                .stats(DashboardStatsVO.builder()
                        .totalClusters(2)
                        .totalBrokers(4)
                        .totalTopics(6)
                        .totalConsumerGroups(7)
                        .tpsOut(40)
                        .build())
                .build();
        when(dashboardService.getDashboard()).thenReturn(dashboard);

        assertThat(handler.name()).isEqualTo("rmq.dashboard.summary");
        var result = handler.execute(new DashboardSummaryInput(), context("instance-a"));

        assertThat(result.clusters()).extracting(DashboardSummaryToolHandler.Cluster::id)
                .containsExactly("instance-a/cluster-a", "instance-b/cluster-b");
        assertThat(result.stats().totalBrokers()).isEqualTo(4);
        assertThat(result.stats().totalTopics()).isEqualTo(6);
        assertThat(result.stats().totalConsumerGroups()).isEqualTo(7);
        assertThat(result.stats().tpsOut()).isEqualTo(40L);
        // Platform-level scope: the authenticated Instance must not narrow the aggregation.
        verify(dashboardService, never()).getDashboard(anyString());
    }
}
