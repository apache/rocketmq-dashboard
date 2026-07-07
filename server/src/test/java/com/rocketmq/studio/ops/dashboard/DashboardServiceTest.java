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

package com.rocketmq.studio.ops.dashboard;

import com.rocketmq.studio.common.domain.enums.ClusterStatus;
import com.rocketmq.studio.common.domain.enums.ClusterType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private DashboardProvider dashboardProvider;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    void getDashboardShouldReturnStatsAndClusters() {
        DashboardStatsVO stats = DashboardStatsVO.builder()
                .totalClusters(3)
                .healthyClusters(2)
                .totalBrokers(9)
                .totalProxies(3)
                .totalNameServers(6)
                .totalTopics(120)
                .totalConsumerGroups(45)
                .totalMessagesToday(1_000_000L)
                .messagesPerSecond(500L)
                .tpsIn(300L)
                .tpsOut(200L)
                .build();

        ClusterOverviewVO cluster = ClusterOverviewVO.builder()
                .id("cluster-1")
                .name("production")
                .type(ClusterType.V5_PROXY_CLUSTER)
                .status(ClusterStatus.healthy)
                .brokers(3)
                .proxies(1)
                .topics(50)
                .groups(20)
                .tpsIn(100)
                .tpsOut(80)
                .version("5.1.0")
                .throughput(List.of(10, 20, 30))
                .build();

        DashboardDataVO mockData = DashboardDataVO.builder()
                .stats(stats)
                .clusters(List.of(cluster))
                .build();

        when(dashboardProvider.getDashboardData()).thenReturn(mockData);

        DashboardDataVO result = dashboardService.getDashboard();

        assertThat(result).isNotNull();
        assertThat(result.getStats()).isNotNull();
        assertThat(result.getStats().getTotalClusters()).isEqualTo(3);
        assertThat(result.getStats().getHealthyClusters()).isEqualTo(2);
        assertThat(result.getStats().getTotalBrokers()).isEqualTo(9);
        assertThat(result.getStats().getTotalTopics()).isEqualTo(120);
        assertThat(result.getStats().getMessagesPerSecond()).isEqualTo(500L);

        assertThat(result.getClusters()).hasSize(1);
        assertThat(result.getClusters().get(0).getName()).isEqualTo("production");
        assertThat(result.getClusters().get(0).getStatus()).isEqualTo(ClusterStatus.healthy);

        verify(dashboardProvider).getDashboardData();
    }

    @Test
    void getDashboardShouldDelegateToProvider() {
        DashboardDataVO emptyData = DashboardDataVO.builder()
                .stats(DashboardStatsVO.builder().build())
                .clusters(List.of())
                .build();

        when(dashboardProvider.getDashboardData()).thenReturn(emptyData);

        DashboardDataVO result = dashboardService.getDashboard();

        assertThat(result).isNotNull();
        assertThat(result.getClusters()).isEmpty();
        verify(dashboardProvider).getDashboardData();
    }
}
