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

package org.apache.rocketmq.studio.ops.dashboard;

import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.apache.rocketmq.studio.common.domain.enums.ClusterType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.isNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @Test
    void getDashboardShouldReturnStatsAndClusters() throws Exception {
        DashboardStatsVO stats = DashboardStatsVO.builder()
                .totalClusters(2)
                .healthyClusters(2)
                .totalBrokers(6)
                .totalProxies(2)
                .totalNameServers(4)
                .totalTopics(80)
                .totalConsumerGroups(30)
                .totalMessagesToday(500_000L)
                .messagesPerSecond(250L)
                .tpsIn(150L)
                .tpsOut(100L)
                .build();

        ClusterOverviewVO cluster = ClusterOverviewVO.builder()
                .id("c1")
                .name("prod-cluster")
                .type(ClusterType.V5_PROXY_CLUSTER)
                .status(ClusterStatus.healthy)
                .brokers(3)
                .proxies(1)
                .topics(40)
                .groups(15)
                .tpsIn(100)
                .tpsOut(80)
                .version("5.1.0")
                .throughput(List.of(5, 10, 15))
                .build();

        DashboardDataVO data = DashboardDataVO.builder()
                .stats(stats)
                .clusters(List.of(cluster))
                .build();

        when(dashboardService.getDashboard(isNull())).thenReturn(data);

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.stats.totalClusters").value(2))
                .andExpect(jsonPath("$.data.stats.healthyClusters").value(2))
                .andExpect(jsonPath("$.data.stats.totalBrokers").value(6))
                .andExpect(jsonPath("$.data.stats.totalProxies").value(2))
                .andExpect(jsonPath("$.data.stats.totalNameServers").value(4))
                .andExpect(jsonPath("$.data.stats.totalTopics").value(80))
                .andExpect(jsonPath("$.data.stats.messagesPerSecond").value(250))
                .andExpect(jsonPath("$.data.clusters").isArray())
                .andExpect(jsonPath("$.data.clusters[0].id").value("c1"))
                .andExpect(jsonPath("$.data.clusters[0].name").value("prod-cluster"))
                .andExpect(jsonPath("$.data.clusters[0].status").value("healthy"))
                .andExpect(jsonPath("$.data.clusters[0].version").value("5.1.0"));

        verify(dashboardService).getDashboard(isNull());
    }

    @Test
    void getDashboardShouldPreserveUnavailableTopologyCounts() throws Exception {
        DashboardStatsVO stats = DashboardStatsVO.builder()
                .totalClusters(1)
                .healthyClusters(1)
                .totalBrokers(2)
                .totalProxies(null)
                .totalNameServers(null)
                .build();
        ClusterOverviewVO cluster = ClusterOverviewVO.builder()
                .id("proxy-cluster")
                .name("proxy-cluster")
                .type(ClusterType.V5_PROXY_CLUSTER)
                .status(ClusterStatus.healthy)
                .brokers(2)
                .proxies(null)
                .build();
        DashboardDataVO data = DashboardDataVO.builder()
                .stats(stats)
                .clusters(List.of(cluster))
                .build();
        when(dashboardService.getDashboard(isNull())).thenReturn(data);

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stats.totalProxies").doesNotExist())
                .andExpect(jsonPath("$.data.stats.totalNameServers").doesNotExist())
                .andExpect(jsonPath("$.data.clusters[0].proxies").doesNotExist());
    }

    @Test
    void getDashboardShouldReturnEmptyClustersWhenNoneExist() throws Exception {
        DashboardDataVO data = DashboardDataVO.builder()
                .stats(DashboardStatsVO.builder().totalClusters(0).build())
                .clusters(List.of())
                .build();

        when(dashboardService.getDashboard(isNull())).thenReturn(data);

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stats.totalClusters").value(0))
                .andExpect(jsonPath("$.data.clusters").isArray())
                .andExpect(jsonPath("$.data.clusters").isEmpty());
    }

    @Test
    void getDashboardShouldForwardSelectedInstance() throws Exception {
        DashboardDataVO data = DashboardDataVO.builder()
                .stats(DashboardStatsVO.builder().totalClusters(1).build())
                .clusters(List.of())
                .build();
        when(dashboardService.getDashboard("instance-prod")).thenReturn(data);

        mockMvc.perform(get("/api/dashboard").param("instanceId", "instance-prod"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stats.totalClusters").value(1));

        verify(dashboardService).getDashboard("instance-prod");
    }
}
