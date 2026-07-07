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
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class DashboardProviderStub implements DashboardProvider {

    @Override
    public DashboardDataVO getDashboardData() {
        DashboardStatsVO stats = DashboardStatsVO.builder()
                .totalClusters(3)
                .healthyClusters(2)
                .totalBrokers(12)
                .totalProxies(6)
                .totalNameServers(6)
                .totalTopics(156)
                .totalConsumerGroups(89)
                .totalMessagesToday(12_500_000L)
                .messagesPerSecond(14_467L)
                .tpsIn(7_200L)
                .tpsOut(7_267L)
                .build();

        List<ClusterOverviewVO> clusters = List.of(
                ClusterOverviewVO.builder()
                        .id("cluster-1")
                        .name("production-cluster")
                        .type(ClusterType.V5_PROXY_CLUSTER)
                        .status(ClusterStatus.healthy)
                        .brokers(6)
                        .proxies(3)
                        .topics(80)
                        .groups(45)
                        .tpsIn(4200)
                        .tpsOut(4300)
                        .version("5.1.4")
                        .throughput(Arrays.asList(3200, 3500, 4100, 4200, 3800, 4000,
                                4500, 4200, 3900, 4100, 4300, 4200))
                        .build(),
                ClusterOverviewVO.builder()
                        .id("cluster-2")
                        .name("staging-cluster")
                        .type(ClusterType.V5_PROXY_LOCAL)
                        .status(ClusterStatus.warning)
                        .brokers(4)
                        .proxies(2)
                        .topics(50)
                        .groups(30)
                        .tpsIn(2000)
                        .tpsOut(1967)
                        .version("5.1.4")
                        .throughput(Arrays.asList(1800, 1900, 2100, 2000, 1700, 2200,
                                2000, 1800, 1900, 2100, 2000, 1967))
                        .build(),
                ClusterOverviewVO.builder()
                        .id("cluster-3")
                        .name("dev-cluster")
                        .type(ClusterType.V4_DIRECT)
                        .status(ClusterStatus.healthy)
                        .brokers(2)
                        .proxies(1)
                        .topics(26)
                        .groups(14)
                        .tpsIn(1000)
                        .tpsOut(1000)
                        .version("4.9.8")
                        .throughput(Arrays.asList(800, 900, 1100, 1000, 950, 1050,
                                1000, 900, 1100, 1000, 950, 1000))
                        .build()
        );

        return DashboardDataVO.builder()
                .stats(stats)
                .clusters(clusters)
                .build();
    }
}
