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

import com.rocketmq.studio.cluster.broker.BrokerVO;
import com.rocketmq.studio.cluster.broker.ClusterRepository;
import com.rocketmq.studio.cluster.broker.ClusterVO;
import com.rocketmq.studio.common.domain.enums.ClusterStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class RepositoryDashboardProvider implements DashboardProvider {

    private final ClusterRepository clusterRepository;

    @Override
    public DashboardDataVO getDashboardData() {
        List<ClusterVO> sourceClusters = clusterRepository.findAll();
        List<ClusterOverviewVO> clusters = sourceClusters.stream()
                .map(this::toClusterOverview)
                .toList();

        DashboardStatsVO stats = DashboardStatsVO.builder()
                .totalClusters(clusters.size())
                .healthyClusters((int) clusters.stream()
                        .filter(cluster -> cluster.getStatus() == ClusterStatus.healthy)
                        .count())
                .totalBrokers(clusters.stream().mapToInt(ClusterOverviewVO::getBrokers).sum())
                .totalProxies(clusters.stream().mapToInt(ClusterOverviewVO::getProxies).sum())
                .totalNameServers(sourceClusters.stream()
                        .map(ClusterVO::getNameServers)
                        .filter(Objects::nonNull)
                        .mapToInt(Collection::size)
                        .sum())
                .totalTopics(clusters.stream().mapToInt(ClusterOverviewVO::getTopics).sum())
                .totalConsumerGroups(clusters.stream().mapToInt(ClusterOverviewVO::getGroups).sum())
                .totalMessagesToday(0L)
                .messagesPerSecond(clusters.stream()
                        .mapToLong(cluster -> (long) cluster.getTpsIn() + cluster.getTpsOut())
                        .sum())
                .tpsIn(clusters.stream().mapToLong(ClusterOverviewVO::getTpsIn).sum())
                .tpsOut(clusters.stream().mapToLong(ClusterOverviewVO::getTpsOut).sum())
                .build();

        return DashboardDataVO.builder()
                .stats(stats)
                .clusters(clusters)
                .build();
    }

    private ClusterOverviewVO toClusterOverview(ClusterVO cluster) {
        List<BrokerVO> brokers = nullToEmpty(cluster.getBrokers());
        int tpsIn = brokers.stream().mapToInt(BrokerVO::getTpsIn).sum();
        int tpsOut = brokers.stream().mapToInt(BrokerVO::getTpsOut).sum();

        return ClusterOverviewVO.builder()
                .id(cluster.getId())
                .name(cluster.getName())
                .type(cluster.getType())
                .status(cluster.getStatus())
                .brokers(brokers.size())
                .proxies(nullToEmpty(cluster.getProxies()).size())
                .topics(cluster.getTopicCount())
                .groups(cluster.getGroupCount())
                .tpsIn(tpsIn)
                .tpsOut(tpsOut)
                .version(cluster.getVersion())
                .throughput(nullToEmpty(cluster.getTpsHistory()))
                .build();
    }

    private <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }
}
