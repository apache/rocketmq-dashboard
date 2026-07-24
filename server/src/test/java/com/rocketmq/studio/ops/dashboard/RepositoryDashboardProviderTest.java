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
import com.rocketmq.studio.cluster.nameserver.NameServerVO;
import com.rocketmq.studio.cluster.proxy.ProxyVO;
import com.rocketmq.studio.common.domain.enums.BrokerStatus;
import com.rocketmq.studio.common.domain.enums.ClusterStatus;
import com.rocketmq.studio.common.domain.enums.ClusterType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryDashboardProviderTest {

    @Test
    void getDashboardDataShouldSummarizeRepositoryClusters() {
        RepositoryDashboardProvider provider = new RepositoryDashboardProvider(new StaticClusterRepository(List.of(
                cluster("cluster-001", ClusterStatus.healthy, 12, 4, List.of(10, 20), 120, 80),
                cluster("cluster-002", ClusterStatus.warning, 8, 3, List.of(30), 50, 40)
        )));

        DashboardDataVO dashboard = provider.getDashboardData();

        assertThat(dashboard.getStats().getTotalClusters()).isEqualTo(2);
        assertThat(dashboard.getStats().getHealthyClusters()).isEqualTo(1);
        assertThat(dashboard.getStats().getTotalBrokers()).isEqualTo(4);
        assertThat(dashboard.getStats().getTotalProxies()).isEqualTo(2);
        assertThat(dashboard.getStats().getTotalNameServers()).isEqualTo(4);
        assertThat(dashboard.getStats().getTotalTopics()).isEqualTo(20);
        assertThat(dashboard.getStats().getTotalConsumerGroups()).isEqualTo(7);
        assertThat(dashboard.getStats().getTpsIn()).isEqualTo(170L);
        assertThat(dashboard.getStats().getTpsOut()).isEqualTo(120L);
        assertThat(dashboard.getStats().getMessagesPerSecond()).isEqualTo(290L);
        assertThat(dashboard.getStats().getTotalMessagesToday()).isZero();
        assertThat(dashboard.getClusters())
                .extracting(ClusterOverviewVO::getId)
                .containsExactly("cluster-001", "cluster-002");
    }

    @Test
    void getDashboardDataShouldHandleMissingNodeLists() {
        ClusterVO cluster = ClusterVO.builder()
                .name("empty")
                .type(ClusterType.V4_DIRECT)
                .status(ClusterStatus.healthy)
                .version("4.9.8")
                .topicCount(2)
                .groupCount(1)
                .build();
        cluster.setId("cluster-empty");
        RepositoryDashboardProvider provider = new RepositoryDashboardProvider(new StaticClusterRepository(List.of(cluster)));

        DashboardDataVO dashboard = provider.getDashboardData();

        assertThat(dashboard.getStats().getTotalBrokers()).isZero();
        assertThat(dashboard.getStats().getTotalProxies()).isZero();
        assertThat(dashboard.getStats().getTotalNameServers()).isZero();
        assertThat(dashboard.getClusters().getFirst().getThroughput()).isEmpty();
        assertThat(dashboard.getClusters().getFirst().getTpsIn()).isZero();
        assertThat(dashboard.getClusters().getFirst().getTpsOut()).isZero();
    }

    private ClusterVO cluster(String id, ClusterStatus status, int topics, int groups,
                              List<Integer> tpsHistory, int firstBrokerIn, int firstBrokerOut) {
        ClusterVO cluster = ClusterVO.builder()
                .name("cluster-" + id)
                .type(ClusterType.V5_PROXY_CLUSTER)
                .status(status)
                .version("5.2.0")
                .brokers(List.of(
                        broker(firstBrokerIn, firstBrokerOut),
                        broker(0, 0)
                ))
                .proxies(List.of(ProxyVO.builder().addr(id + "-proxy").status(status).build()))
                .nameServers(List.of(
                        NameServerVO.builder().addr(id + "-namesrv-a").status(ClusterStatus.healthy).build(),
                        NameServerVO.builder().addr(id + "-namesrv-b").status(ClusterStatus.healthy).build()
                ))
                .topicCount(topics)
                .groupCount(groups)
                .tpsHistory(tpsHistory)
                .build();
        cluster.setId(id);
        return cluster;
    }

    private BrokerVO broker(int tpsIn, int tpsOut) {
        return BrokerVO.builder()
                .name("broker-a")
                .status(BrokerStatus.running)
                .tpsIn(tpsIn)
                .tpsOut(tpsOut)
                .build();
    }

    private record StaticClusterRepository(List<ClusterVO> clusters) implements ClusterRepository {
        @Override
        public List<ClusterVO> findAll() {
            return clusters;
        }

        @Override
        public Optional<ClusterVO> findById(String id) {
            return clusters.stream().filter(cluster -> cluster.getId().equals(id)).findFirst();
        }

        @Override
        public void updateConfig(String clusterId, com.rocketmq.studio.cluster.config.ClusterConfigVO config) {
        }
    }
}
