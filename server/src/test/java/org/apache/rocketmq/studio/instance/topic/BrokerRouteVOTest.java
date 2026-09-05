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
package org.apache.rocketmq.studio.instance.topic;

import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrokerRouteVOTest {

    @Test
    void builderDefaultsDescribeEmptyRoute() {
        BrokerRouteVO vo = BrokerRouteVO.builder().build();

        assertNull(vo.getBrokerName());
        assertNull(vo.getBrokerAddr());
        assertNull(vo.getBrokerAddrs());
        assertNull(vo.getBrokerIds());
        assertEquals(0, vo.getReplicaCount());
        assertEquals(0, vo.getWriteQueues());
        assertNull(vo.getPerm());
        assertEquals(0, vo.getPermCode());
        assertFalse(vo.isReadable());
        assertFalse(vo.isWritable());
        assertEquals(0, vo.getTopicSysFlag());
    }

    @Test
    void allArgsCarryRouteState() {
        BrokerRouteVO vo = BrokerRouteVO.builder()
            .brokerName("broker-a")
            .brokerAddr("10.0.0.1:10911")
            .masterAddr("10.0.0.1:10911")
            .brokerAddrs(Map.of(0L, "10.0.0.1:10911"))
            .brokerIds(List.of(0L))
            .replicaCount(1)
            .writeQueues(8)
            .readQueues(8)
            .perm(TopicPerm.RW)
            .permCode(6)
            .readable(true)
            .writable(true)
            .topicSysFlag(0)
            .build();

        assertEquals("broker-a", vo.getBrokerName());
        assertEquals(Map.of(0L, "10.0.0.1:10911"), vo.getBrokerAddrs());
        assertEquals(1, vo.getReplicaCount());
        assertEquals(TopicPerm.RW, vo.getPerm());
        assertTrue(vo.isReadable());
        assertTrue(vo.isWritable());
    }
}
