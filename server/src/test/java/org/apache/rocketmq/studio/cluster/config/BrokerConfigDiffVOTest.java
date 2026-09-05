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
package org.apache.rocketmq.studio.cluster.config;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrokerConfigDiffVOTest {

    @Test
    void builderDefaultsDescribeEmptyDiff() {
        BrokerConfigDiffVO vo = BrokerConfigDiffVO.builder().build();

        assertNull(vo.getCluster());
        assertFalse(vo.isComplete());
        assertFalse(vo.isDriftDetected());
        assertEquals(0, vo.getBrokerCount());
        assertEquals(0, vo.getReachableBrokerCount());
        assertNull(vo.getComparedFields());
        assertNull(vo.getBrokers());
        assertNull(vo.getDifferences());
    }

    @Test
    void allArgsCarryDiffWithNestedSections() {
        BrokerConfigDiffVO.BrokerStatusVO broker = BrokerConfigDiffVO.BrokerStatusVO.builder()
            .name("broker-a")
            .address("10.0.0.1:10911")
            .reachable(true)
            .message("ok")
            .build();
        BrokerConfigDiffVO.ConfigDifferenceVO diff = BrokerConfigDiffVO.ConfigDifferenceVO.builder()
            .field("writeQueueNums")
            .brokerProperty("writeQueueNums")
            .values(List.of())
            .build();

        BrokerConfigDiffVO vo = BrokerConfigDiffVO.builder()
            .cluster("DefaultCluster")
            .complete(true)
            .driftDetected(true)
            .brokerCount(2)
            .reachableBrokerCount(1)
            .comparedFields(List.of("writeQueueNums"))
            .brokers(List.of(broker))
            .differences(List.of(diff))
            .build();

        assertEquals("DefaultCluster", vo.getCluster());
        assertTrue(vo.isComplete());
        assertTrue(vo.isDriftDetected());
        assertEquals(1, vo.getReachableBrokerCount());
        assertEquals("broker-a", broker.getName());
        assertTrue(broker.isReachable());
        assertEquals("writeQueueNums", diff.getField());
    }
}
