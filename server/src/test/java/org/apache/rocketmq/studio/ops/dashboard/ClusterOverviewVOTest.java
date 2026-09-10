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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClusterOverviewVOTest {

    @Test
    void builderDefaultsDescribeEmptyOverview() {
        ClusterOverviewVO vo = ClusterOverviewVO.builder().build();

        assertNull(vo.getId());
        assertNull(vo.getType());
        assertNull(vo.getStatus());
        assertEquals(0, vo.getBrokers());
        assertNull(vo.getProxies());
        assertEquals(0, vo.getTopics());
        assertEquals(0L, vo.getTpsIn());
        assertNull(vo.getThroughput());
    }

    @Test
    void allArgsCarryOverviewState() {
        ClusterOverviewVO vo = ClusterOverviewVO.builder()
            .id("cluster-1")
            .name("DefaultCluster")
            .type(ClusterType.V5_PROXY_CLUSTER)
            .status(ClusterStatus.healthy)
            .brokers(6)
            .proxies(2)
            .topics(30)
            .groups(10)
            .tpsIn(100L)
            .tpsOut(80L)
            .version("5.3.2")
            .throughput(List.of(1, 2, 3))
            .build();

        assertEquals("cluster-1", vo.getId());
        assertEquals(ClusterType.V5_PROXY_CLUSTER, vo.getType());
        assertEquals(ClusterStatus.healthy, vo.getStatus());
        assertEquals(6, vo.getBrokers());
        assertEquals(2, vo.getProxies());
        assertEquals(List.of(1, 2, 3), vo.getThroughput());
    }
}
