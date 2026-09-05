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

import org.apache.rocketmq.studio.cluster.config.ClusterConfigVO;
import org.apache.rocketmq.studio.cluster.nameserver.NameServerVO;

import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.apache.rocketmq.studio.common.domain.enums.ClusterType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClusterVOTest {

    @Test
    void builderDefaultsDescribeEmptyCluster() {
        ClusterVO vo = ClusterVO.builder().build();

        assertNull(vo.getId());
        assertNull(vo.getName());
        assertNull(vo.getType());
        assertNull(vo.getStatus());
        assertNull(vo.getBrokers());
        assertNull(vo.getProxies());
        assertNull(vo.getNameServers());
        assertNull(vo.getConfig());
        assertEquals(0, vo.getTopicCount());
        assertEquals(0, vo.getGroupCount());
        assertNull(vo.getTpsHistory());
    }

    @Test
    void allArgsCarryClusterState() {
        LocalDateTime created = LocalDateTime.parse("2026-09-01T08:00:00");
        BrokerVO broker = BrokerVO.builder().name("broker-a").build();
        NameServerVO ns = NameServerVO.builder().addr("10.0.0.1:9876").build();

        ClusterVO vo = ClusterVO.builder()
            .id("cluster-1")
            .gmtCreate(created)
            .gmtModified(created)
            .name("DefaultCluster")
            .nsClusterName("DefaultCluster")
            .type(ClusterType.V5_PROXY_CLUSTER)
            .endpoint("10.0.0.1:8080")
            .status(ClusterStatus.healthy)
            .version("5.3.2")
            .brokers(List.of(broker))
            .proxies(List.of())
            .nameServers(List.of(ns))
            .config(ClusterConfigVO.builder().writeQueueNums(8).build())
            .topicCount(30)
            .groupCount(10)
            .tpsHistory(List.of(1, 2))
            .build();

        assertEquals("cluster-1", vo.getId());
        assertEquals(ClusterType.V5_PROXY_CLUSTER, vo.getType());
        assertEquals(ClusterStatus.healthy, vo.getStatus());
        assertEquals(30, vo.getTopicCount());
        assertEquals(8, vo.getConfig().getWriteQueueNums());
        assertEquals("broker-a", broker.getName());
        assertEquals("10.0.0.1:9876", ns.getAddr());
        assertEquals(List.of(1, 2), vo.getTpsHistory());
    }
}
