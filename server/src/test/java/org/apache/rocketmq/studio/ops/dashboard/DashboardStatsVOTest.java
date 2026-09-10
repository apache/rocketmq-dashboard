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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DashboardStatsVOTest {

    @Test
    void builderDefaultsDescribeEmptyStats() {
        DashboardStatsVO vo = DashboardStatsVO.builder().build();

        assertEquals(0, vo.getTotalClusters());
        assertEquals(0, vo.getHealthyClusters());
        assertEquals(0, vo.getTotalBrokers());
        assertNull(vo.getTotalProxies());
        assertNull(vo.getTotalNameServers());
        assertEquals(0, vo.getTotalTopics());
        assertEquals(0L, vo.getTotalMessagesToday());
        assertEquals(0L, vo.getMessagesPerSecond());
    }

    @Test
    void allArgsCarryStatsState() {
        DashboardStatsVO vo = DashboardStatsVO.builder()
            .totalClusters(3)
            .healthyClusters(2)
            .totalBrokers(12)
            .totalProxies(4)
            .totalNameServers(3)
            .totalTopics(50)
            .totalConsumerGroups(20)
            .totalMessagesToday(100000L)
            .messagesPerSecond(500L)
            .tpsIn(300L)
            .tpsOut(200L)
            .build();

        assertEquals(3, vo.getTotalClusters());
        assertEquals(12, vo.getTotalBrokers());
        assertEquals(4, vo.getTotalProxies());
        assertEquals(50, vo.getTotalTopics());
        assertEquals(100000L, vo.getTotalMessagesToday());
        assertEquals(300L, vo.getTpsIn());
        assertEquals(200L, vo.getTpsOut());
    }
}
