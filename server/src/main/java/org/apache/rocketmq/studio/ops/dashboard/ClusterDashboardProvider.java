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

import org.apache.rocketmq.studio.cluster.broker.BrokerVO;
import org.apache.rocketmq.studio.cluster.broker.ClusterRepository;
import org.apache.rocketmq.studio.cluster.broker.ClusterVO;
import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ClusterDashboardProvider implements DashboardProvider {

    private final ClusterRepository clusterRepository;

    @Override
    public DashboardDataVO getDashboardData() {
        List<ClusterVO> clusters = clusterRepository.findAll();
        List<ClusterOverviewVO> overview = clusters.stream()
                .map(this::toOverview)
                .toList();

        return DashboardDataVO.builder()
                .stats(toStats(clusters))
                .clusters(overview)
                .build();
    }

    private DashboardStatsVO toStats(List<ClusterVO> clusters) {
        int totalBrokers = clusters.stream().mapToInt(cluster -> safeList(cluster.getBrokers()).size()).sum();
        int totalProxies = clusters.stream().mapToInt(cluster -> safeList(cluster.getProxies()).size()).sum();
        int totalNameServers = clusters.stream().mapToInt(cluster -> safeList(cluster.getNameServers()).size()).sum();
        long tpsIn = clusters.stream().flatMap(cluster -> safeList(cluster.getBrokers()).stream())
                .mapToLong(BrokerVO::getTpsIn)
                .sum();
        long tpsOut = clusters.stream().flatMap(cluster -> safeList(cluster.getBrokers()).stream())
                .mapToLong(BrokerVO::getTpsOut)
                .sum();

        return DashboardStatsVO.builder()
                .totalClusters(clusters.size())
                .healthyClusters((int) clusters.stream()
                        .filter(cluster -> ClusterStatus.healthy == cluster.getStatus())
                        .count())
                .totalBrokers(totalBrokers)
                .totalProxies(totalProxies)
                .totalNameServers(totalNameServers)
                .totalTopics(clusters.stream().mapToInt(ClusterVO::getTopicCount).sum())
                .totalConsumerGroups(clusters.stream().mapToInt(ClusterVO::getGroupCount).sum())
                .totalMessagesToday(0)
                .messagesPerSecond(tpsIn + tpsOut)
                .tpsIn(tpsIn)
                .tpsOut(tpsOut)
                .build();
    }

    private ClusterOverviewVO toOverview(ClusterVO cluster) {
        int tpsIn = safeList(cluster.getBrokers()).stream().mapToInt(BrokerVO::getTpsIn).sum();
        int tpsOut = safeList(cluster.getBrokers()).stream().mapToInt(BrokerVO::getTpsOut).sum();

        return ClusterOverviewVO.builder()
                .id(cluster.getId())
                .name(cluster.getName())
                .type(cluster.getType())
                .status(cluster.getStatus())
                .brokers(safeList(cluster.getBrokers()).size())
                .proxies(safeList(cluster.getProxies()).size())
                .topics(cluster.getTopicCount())
                .groups(cluster.getGroupCount())
                .tpsIn(tpsIn)
                .tpsOut(tpsOut)
                .version(cluster.getVersion())
                .throughput(safeList(cluster.getTpsHistory()))
                .build();
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
