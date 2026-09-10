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
package org.apache.rocketmq.studio.cluster.nameserver;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NameServerConfigDiffVOTest {

    @Test
    void builderDefaultsDescribeEmptyDiff() {
        NameServerConfigDiffVO vo = NameServerConfigDiffVO.builder().build();

        assertNull(vo.getCluster());
        assertFalse(vo.isComplete());
        assertFalse(vo.isDriftDetected());
        assertEquals(0, vo.getNodeCount());
        assertEquals(0, vo.getReachableNodeCount());
        assertNull(vo.getComparedKeys());
        assertNull(vo.getNodes());
        assertNull(vo.getDifferences());
    }

    @Test
    void allArgsCarryDriftStateWithNestedSections() {
        NameServerConfigDiffVO.NodeStatusVO node = NameServerConfigDiffVO.NodeStatusVO.builder()
            .address("10.0.0.1:9876")
            .reachable(true)
            .build();
        NameServerConfigDiffVO.ConfigDifferenceVO diff =
                NameServerConfigDiffVO.ConfigDifferenceVO.builder()
                    .key("brokerPermission")
                    .values(List.of())
                    .build();

        NameServerConfigDiffVO vo = NameServerConfigDiffVO.builder()
            .cluster("DefaultCluster")
            .complete(true)
            .driftDetected(true)
            .nodeCount(2)
            .reachableNodeCount(1)
            .comparedKeys(List.of("brokerPermission"))
            .nodes(List.of(node))
            .differences(List.of(diff))
            .build();

        assertEquals("DefaultCluster", vo.getCluster());
        assertTrue(vo.isComplete());
        assertTrue(vo.isDriftDetected());
        assertEquals(2, vo.getNodeCount());
        assertEquals(1, vo.getReachableNodeCount());
        assertEquals(List.of(node), vo.getNodes());
        assertEquals(List.of(diff), vo.getDifferences());
        assertEquals("10.0.0.1:9876", node.getAddress());
        assertTrue(node.isReachable());
    }
}
