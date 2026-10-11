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
package org.apache.rocketmq.studio.cluster.broker;

import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.apache.rocketmq.studio.common.domain.enums.ClusterType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the cluster view of {@link ClusterVO}: the whole cluster - brokers, proxies,
 * nameservers, config - round-trips through the builder with its counters and TPS history,
 * and a fresh cluster carries no sub-lists and zero counters.
 */
class ClusterVOTest {

    @Test
    void theWholeClusterRoundTrips() {
        BrokerVO broker = BrokerVO.builder().name("broker-a").build();
        ClusterVO cluster = ClusterVO.builder()
                .id("cluster-1")
                .gmtCreate(LocalDateTime.of(2026, 1, 2, 3, 4))
                .gmtModified(LocalDateTime.of(2026, 1, 3, 4, 5))
                .name("prod")
                .nsClusterName("ns-prod")
                .type(ClusterType.V5_PROXY_CLUSTER)
                .endpoint("10.0.0.1:9876")
                .status(ClusterStatus.healthy)
                .version("5.5.0")
                .brokers(List.of(broker))
                .proxies(List.of())
                .nameServers(List.of())
                .topicCount(42)
                .groupCount(7)
                .tpsHistory(List.of(100, 200, 300))
                .build();

        assertThat(cluster.getId()).isEqualTo("cluster-1");
        assertThat(cluster.getName()).isEqualTo("prod");
        assertThat(cluster.getNsClusterName()).isEqualTo("ns-prod");
        assertThat(cluster.getType()).isEqualTo(ClusterType.V5_PROXY_CLUSTER);
        assertThat(cluster.getEndpoint()).isEqualTo("10.0.0.1:9876");
        assertThat(cluster.getStatus()).isEqualTo(ClusterStatus.healthy);
        assertThat(cluster.getVersion()).isEqualTo("5.5.0");
        assertThat(cluster.getBrokers()).hasSize(1);
        assertThat(cluster.getBrokers().get(0).getName()).isEqualTo("broker-a");
        assertThat(cluster.getTopicCount()).isEqualTo(42);
        assertThat(cluster.getGroupCount()).isEqualTo(7);
        assertThat(cluster.getTpsHistory()).containsExactly(100, 200, 300);
    }

    @Test
    void aFreshClusterCarriesNoSubListsAndZeroCounters() {
        ClusterVO cluster = new ClusterVO();
        assertThat(cluster.getBrokers()).isNull();
        assertThat(cluster.getProxies()).isNull();
        assertThat(cluster.getNameServers()).isNull();
        assertThat(cluster.getTopicCount()).isZero();
        assertThat(cluster.getGroupCount()).isZero();
    }
}
