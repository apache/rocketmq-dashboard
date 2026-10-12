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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the dashboard totals of {@link DashboardStatsVO}: the headline counters round-trip
 * through the builder, and a fresh stats block reads as an empty deployment - zero clusters,
 * zero traffic - rather than nulls the cards would have to guard against.
 */
class DashboardStatsVOTest {

    @Test
    void everyCounterRoundTripsThroughTheBuilder() {
        DashboardStatsVO stats = DashboardStatsVO.builder()
                .totalClusters(3)
                .healthyClusters(2)
                .totalBrokers(9)
                .totalProxies(4)
                .totalNameServers(6)
                .totalTopics(120)
                .totalConsumerGroups(45)
                .totalMessagesToday(1_000_000L)
                .messagesPerSecond(250L)
                .tpsIn(100L)
                .tpsOut(150L)
                .build();

        assertThat(stats.getTotalClusters()).isEqualTo(3);
        assertThat(stats.getHealthyClusters()).isEqualTo(2);
        assertThat(stats.getTotalBrokers()).isEqualTo(9);
        assertThat(stats.getTotalProxies()).isEqualTo(4);
        assertThat(stats.getTotalNameServers()).isEqualTo(6);
        assertThat(stats.getTotalTopics()).isEqualTo(120);
        assertThat(stats.getTotalConsumerGroups()).isEqualTo(45);
        assertThat(stats.getTotalMessagesToday()).isEqualTo(1_000_000L);
        assertThat(stats.getMessagesPerSecond()).isEqualTo(250L);
        assertThat(stats.getTpsIn()).isEqualTo(100L);
        assertThat(stats.getTpsOut()).isEqualTo(150L);
    }

    @Test
    void aFreshStatsBlockReadsAsAnEmptyDeployment() {
        DashboardStatsVO stats = new DashboardStatsVO();
        assertThat(stats.getTotalClusters()).isZero();
        assertThat(stats.getHealthyClusters()).isZero();
        assertThat(stats.getTotalMessagesToday()).isZero();
        assertThat(stats.getTpsIn()).isZero();
        assertThat(stats.getTpsOut()).isZero();
    }
}
