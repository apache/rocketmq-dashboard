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
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterConfigPreviewVOTest {

    @Test
    void builderDefaultsDescribeEmptyPreview() {
        ClusterConfigPreviewVO vo = ClusterConfigPreviewVO.builder().build();

        assertNull(vo.getCluster());
        assertNull(vo.getCurrentConfig());
        assertNull(vo.getProposedConfig());
        assertNull(vo.getTargetBrokers());
        assertNull(vo.getBrokerProperties());
        assertNull(vo.getChanges());
        assertFalse(vo.isChanged());
    }

    @Test
    void allArgsCarryPreviewStateWithNestedSections() {
        ClusterConfigPreviewVO.BrokerTargetVO target = ClusterConfigPreviewVO.BrokerTargetVO.builder()
            .name("broker-a")
            .address("10.0.0.1:10911")
            .build();
        ClusterConfigPreviewVO.ConfigChangeVO change = ClusterConfigPreviewVO.ConfigChangeVO.builder()
            .field("writeQueueNums")
            .currentValue("4")
            .proposedValue("8")
            .brokerProperty("writeQueueNums")
            .build();

        ClusterConfigPreviewVO vo = ClusterConfigPreviewVO.builder()
            .cluster(null)
            .currentConfig(ClusterConfigVO.builder().writeQueueNums(4).build())
            .proposedConfig(ClusterConfigVO.builder().writeQueueNums(8).build())
            .targetBrokers(List.of(target))
            .brokerProperties(Map.of("writeQueueNums", "8"))
            .changes(List.of(change))
            .changed(true)
            .build();

        assertEquals(4, vo.getCurrentConfig().getWriteQueueNums());
        assertEquals(8, vo.getProposedConfig().getWriteQueueNums());
        assertEquals("broker-a", target.getName());
        assertEquals("writeQueueNums", change.getField());
        assertEquals("8", change.getProposedValue());
        assertTrue(vo.isChanged());
    }
}
