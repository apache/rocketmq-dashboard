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
import org.apache.rocketmq.studio.common.domain.enums.BrokerStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DashboardDataVOTest {

    @Test
    void builderDefaultsDescribeEmptyDashboard() {
        DashboardDataVO vo = DashboardDataVO.builder().build();

        assertNull(vo.getStats());
        assertNull(vo.getClusters());
    }

    @Test
    void allArgsCarryDashboardData() {
        DashboardStatsVO stats = DashboardStatsVO.builder().totalClusters(1).totalBrokers(2).build();
        BrokerVO broker = BrokerVO.builder().name("broker-a").status(BrokerStatus.running).build();
        ClusterOverviewVO cluster = ClusterOverviewVO.builder().id("cluster-1").brokers(1).build();

        DashboardDataVO vo = DashboardDataVO.builder()
            .stats(stats)
            .clusters(java.util.List.of(cluster))
            .build();

        assertEquals(1, vo.getStats().getTotalClusters());
        assertEquals("cluster-1", vo.getClusters().get(0).getId());
        assertEquals("broker-a", broker.getName());
        assertEquals(BrokerStatus.running, broker.getStatus());
    }
}
