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

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterProbeResultTest {

    @Test
    void builderDefaultsDescribeUntriedProbe() {
        ClusterProbeResult result = ClusterProbeResult.builder().build();

        assertFalse(result.isConnected());
        assertNull(result.getNamesrvAddr());
        assertNull(result.getClusterName());
        assertEquals(0, result.getBrokerCount());
        assertNull(result.getBrokerNames());
        assertEquals(0L, result.getElapsedMillis());
        assertNull(result.getMessage());
    }

    @Test
    void allArgsCarryProbeOutcome() {
        ClusterProbeResult result = ClusterProbeResult.builder()
            .connected(true)
            .namesrvAddr("10.132.218.11:9876")
            .clusterName("DefaultCluster")
            .brokerCount(2)
            .brokerNames(List.of("broker-a", "broker-b"))
            .elapsedMillis(12L)
            .message("cluster reachable")
            .build();

        assertTrue(result.isConnected());
        assertEquals("10.132.218.11:9876", result.getNamesrvAddr());
        assertEquals("DefaultCluster", result.getClusterName());
        assertEquals(2, result.getBrokerCount());
        assertEquals(List.of("broker-a", "broker-b"), result.getBrokerNames());
        assertEquals(12L, result.getElapsedMillis());
        assertEquals("cluster reachable", result.getMessage());
    }
}
