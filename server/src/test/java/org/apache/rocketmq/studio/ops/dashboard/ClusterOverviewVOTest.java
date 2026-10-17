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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins one cluster card of {@link ClusterOverviewVO}: the identity, health, counters and the
 * throughput series the dashboard sparkline renders, round-tripping through the builder.
 */
class ClusterOverviewVOTest {

    @Test
    void theClusterCardRoundTrips() {
        ClusterOverviewVO cluster = ClusterOverviewVO.builder()
                .id("cluster-1")
                .name("prod")
                .type(ClusterType.V5_PROXY_CLUSTER)
                .status(ClusterStatus.healthy)
                .brokers(3)
                .proxies(2)
                .topics(120)
                .groups(45)
                .tpsIn(100L)
                .tpsOut(150L)
                .version("5.5.0")
                .throughput(List.of(100, 200, 300))
                .build();

        assertThat(cluster.getId()).isEqualTo("cluster-1");
        assertThat(cluster.getName()).isEqualTo("prod");
        assertThat(cluster.getType()).isEqualTo(ClusterType.V5_PROXY_CLUSTER);
        assertThat(cluster.getStatus()).isEqualTo(ClusterStatus.healthy);
        assertThat(cluster.getBrokers()).isEqualTo(3);
        assertThat(cluster.getProxies()).isEqualTo(2);
        assertThat(cluster.getTopics()).isEqualTo(120);
        assertThat(cluster.getGroups()).isEqualTo(45);
        assertThat(cluster.getTpsIn()).isEqualTo(100L);
        assertThat(cluster.getTpsOut()).isEqualTo(150L);
        assertThat(cluster.getVersion()).isEqualTo("5.5.0");
        assertThat(cluster.getThroughput()).containsExactly(100, 200, 300);
    }

    @Test
    void aFreshCardCountsNothing() {
        ClusterOverviewVO cluster = new ClusterOverviewVO();
        assertThat(cluster.getBrokers()).isZero();
        assertThat(cluster.getTopics()).isZero();
        assertThat(cluster.getThroughput()).isNull();
    }
}
