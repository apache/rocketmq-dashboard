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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the dashboard payload of {@link DashboardDataVO}: the stats block and the cluster cards
 * travel together, and a fresh payload carries neither - the page renders its own empty state.
 */
class DashboardDataVOTest {

    @Test
    void theStatsAndClustersTravelTogether() {
        DashboardStatsVO stats = DashboardStatsVO.builder().totalClusters(1).build();
        ClusterOverviewVO cluster = ClusterOverviewVO.builder().id("cluster-1").build();
        DashboardDataVO data = DashboardDataVO.builder()
                .stats(stats)
                .clusters(List.of(cluster))
                .build();

        assertThat(data.getStats()).isSameAs(stats);
        assertThat(data.getClusters()).containsExactly(cluster);
    }

    @Test
    void aFreshPayloadCarriesNeither() {
        DashboardDataVO data = new DashboardDataVO();
        assertThat(data.getStats()).isNull();
        assertThat(data.getClusters()).isNull();
    }
}
