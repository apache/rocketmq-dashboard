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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DashboardSummaryToolHandler}: the tool projects the requested
 * cluster plus the dashboard-wide stats, and rejects an unknown cluster id with a 404.
 */
@ExtendWith(MockitoExtension.class)
class DashboardSummaryToolHandlerTest {

    @Mock
    private DashboardService dashboardService;

    @InjectMocks
    private DashboardSummaryToolHandler handler;

    private static ClusterOverviewVO cluster(String id) {
        return ClusterOverviewVO.builder()
                .id(id)
                .name("Cluster " + id)
                .type(ClusterType.V4_DIRECT)
                .status(ClusterStatus.healthy)
                .brokers(2)
                .proxies(1)
                .topics(10)
                .groups(3)
                .tpsIn(100L)
                .tpsOut(50L)
                .version("5.3.3")
                .throughput(null)
                .build();
    }

    @Test
    void reportsItsToolName() {
        assertThat(handler.name()).isEqualTo("rmq.dashboard.summary");
    }

    @Test
    @SuppressWarnings("unchecked")
    void projectsTheMatchingClusterAndDashboardStats() {
        DashboardStatsVO stats = DashboardStatsVO.builder()
                .totalClusters(1)
                .healthyClusters(1)
                .totalBrokers(2)
                .totalProxies(1)
                .totalNameServers(1)
                .totalTopics(10)
                .totalConsumerGroups(3)
                .totalMessagesToday(1_000L)
                .messagesPerSecond(5L)
                .tpsIn(100L)
                .tpsOut(50L)
                .build();
        when(dashboardService.getDashboard()).thenReturn(DashboardDataVO.builder()
                .clusters(List.of(cluster("c1"), cluster("c2")))
                .stats(stats)
                .build());

        Map<String, Object> result = (Map<String, Object>) handler.execute(Map.of("cluster", "c2"));

        Map<String, Object> cluster = (Map<String, Object>) result.get("cluster");
        assertThat(cluster.get("id")).isEqualTo("c2");
        assertThat(cluster.get("name")).isEqualTo("Cluster c2");
        assertThat(cluster.get("type")).isEqualTo("V4_DIRECT");
        assertThat(cluster.get("status")).isEqualTo("healthy");
        assertThat(cluster.get("brokers")).isEqualTo(2);
        // A missing throughput series projects to an empty list.
        assertThat(cluster.get("throughput")).isEqualTo(List.of());

        Map<String, Object> projectedStats = (Map<String, Object>) result.get("stats");
        assertThat(projectedStats.get("totalClusters")).isEqualTo(1);
        assertThat(projectedStats.get("totalBrokers")).isEqualTo(2);
        assertThat(projectedStats.get("messagesPerSecond")).isEqualTo(5L);
    }

    @Test
    void rejectsAnUnknownClusterIdWithNotFound() {
        when(dashboardService.getDashboard()).thenReturn(DashboardDataVO.builder()
                .clusters(List.of(cluster("c1")))
                .build());

        assertThatThrownBy(() -> handler.execute(Map.of("cluster", "missing")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(404));
    }

    @Test
    void rejectsWhenTheDashboardCannotBeLoaded() {
        when(dashboardService.getDashboard()).thenReturn(null);

        assertThatThrownBy(() -> handler.execute(Map.of("cluster", "c1")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(404));
    }

    @Test
    @SuppressWarnings("unchecked")
    void toleratesMissingDashboardStats() {
        when(dashboardService.getDashboard()).thenReturn(DashboardDataVO.builder()
                .clusters(List.of(cluster("c1")))
                .stats(null)
                .build());

        Map<String, Object> result = (Map<String, Object>) handler.execute(Map.of("cluster", "c1"));

        Map<String, Object> projectedStats = (Map<String, Object>) result.get("stats");
        assertThat(projectedStats.get("totalClusters")).isEqualTo(0);
        assertThat(projectedStats.get("tpsIn")).isEqualTo(0L);
    }
}
