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
import org.apache.rocketmq.studio.cluster.nameserver.NameServerVO;
import org.apache.rocketmq.studio.cluster.proxy.ProxyVO;
import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.apache.rocketmq.studio.common.domain.enums.ClusterType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ClusterDashboardProviderTest {

    @Test
    void getDashboardDataShouldSummarizeClusterRepository() {
        ClusterDashboardProvider provider = new ClusterDashboardProvider(new InMemoryClusterRepository(List.of(
                ClusterVO.builder()
                        .name("prod")
                        .type(ClusterType.V5_PROXY_CLUSTER)
                        .status(ClusterStatus.healthy)
                        .version("5.3.0")
                        .brokers(List.of(
                                BrokerVO.builder().name("broker-a").tpsIn(10).tpsOut(7).build(),
                                BrokerVO.builder().name("broker-b").tpsIn(20).tpsOut(9).build()))
                        .proxies(List.of(ProxyVO.builder().addr("proxy:8081").build()))
                        .nameServers(List.of(NameServerVO.builder().addr("namesrv:9876").build()))
                        .topicCount(12)
                        .groupCount(4)
                        .tpsHistory(List.of(1, 2, 3))
                        .build(),
                ClusterVO.builder()
                        .name("staging")
                        .type(ClusterType.V4_DIRECT)
                        .status(ClusterStatus.warning)
                        .brokers(List.of(BrokerVO.builder().name("broker-c").tpsIn(3).tpsOut(2).build()))
                        .topicCount(2)
                        .groupCount(1)
                        .build())));

        DashboardDataVO dashboard = provider.getDashboardData();

        assertThat(dashboard.getStats().getTotalClusters()).isEqualTo(2);
        assertThat(dashboard.getStats().getHealthyClusters()).isEqualTo(1);
        assertThat(dashboard.getStats().getTotalBrokers()).isEqualTo(3);
        assertThat(dashboard.getStats().getTotalProxies()).isEqualTo(1);
        assertThat(dashboard.getStats().getTotalNameServers()).isEqualTo(1);
        assertThat(dashboard.getStats().getTotalTopics()).isEqualTo(14);
        assertThat(dashboard.getStats().getTotalConsumerGroups()).isEqualTo(5);
        assertThat(dashboard.getStats().getTpsIn()).isEqualTo(33);
        assertThat(dashboard.getStats().getTpsOut()).isEqualTo(18);
        assertThat(dashboard.getStats().getMessagesPerSecond()).isEqualTo(51);
        assertThat(dashboard.getStats().getTotalMessagesToday()).isZero();

        assertThat(dashboard.getClusters()).hasSize(2);
        assertThat(dashboard.getClusters().get(0).getName()).isEqualTo("prod");
        assertThat(dashboard.getClusters().get(0).getBrokers()).isEqualTo(2);
        assertThat(dashboard.getClusters().get(0).getProxies()).isEqualTo(1);
        assertThat(dashboard.getClusters().get(0).getTpsIn()).isEqualTo(30);
        assertThat(dashboard.getClusters().get(0).getTpsOut()).isEqualTo(16);
        assertThat(dashboard.getClusters().get(0).getThroughput()).containsExactly(1, 2, 3);
    }

    @Test
    void getDashboardDataShouldReturnEmptySummaryWhenNoClustersExist() {
        ClusterDashboardProvider provider = new ClusterDashboardProvider(new InMemoryClusterRepository(List.of()));

        DashboardDataVO dashboard = provider.getDashboardData();

        assertThat(dashboard.getStats().getTotalClusters()).isZero();
        assertThat(dashboard.getStats().getMessagesPerSecond()).isZero();
        assertThat(dashboard.getClusters()).isEmpty();
    }

    private record InMemoryClusterRepository(List<ClusterVO> clusters) implements ClusterRepository {

        @Override
        public List<ClusterVO> findAll() {
            return clusters;
        }

        @Override
        public Optional<ClusterVO> findById(String id) {
            return Optional.empty();
        }

        @Override
        public void updateConfig(String clusterId,
                                 org.apache.rocketmq.studio.cluster.config.ClusterConfigVO config) {
        }
    }
}
