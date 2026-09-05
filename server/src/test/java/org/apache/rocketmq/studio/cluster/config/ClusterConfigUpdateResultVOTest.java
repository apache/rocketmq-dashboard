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
import static org.junit.jupiter.api.Assertions.assertNull;

class ClusterConfigUpdateResultVOTest {

    @Test
    void exposesAllStatusValues() {
        assertEquals(3, ClusterConfigUpdateResultVO.Status.values().length);
        assertEquals("SUCCESS", ClusterConfigUpdateResultVO.Status.SUCCESS.name());
        assertEquals("PARTIAL", ClusterConfigUpdateResultVO.Status.PARTIAL.name());
        assertEquals("FAILED", ClusterConfigUpdateResultVO.Status.FAILED.name());
    }

    @Test
    void builderDefaultsDescribeEmptyResult() {
        ClusterConfigUpdateResultVO vo = ClusterConfigUpdateResultVO.builder().build();

        assertNull(vo.getCluster());
        assertNull(vo.getStatus());
        assertNull(vo.getSuccessfulBrokers());
        assertNull(vo.getFailedBrokers());
    }

    @Test
    void allArgsCarryResultState() {
        ClusterConfigUpdateResultVO vo = ClusterConfigUpdateResultVO.builder()
            .cluster(null)
            .status(ClusterConfigUpdateResultVO.Status.PARTIAL)
            .successfulBrokers(List.of("broker-a"))
            .failedBrokers(List.of(BrokerConfigUpdateFailureVO.builder()
                    .address("broker-b")
                    .message("rejected")
                    .build()))
            .build();

        assertEquals(ClusterConfigUpdateResultVO.Status.PARTIAL, vo.getStatus());
        assertEquals(List.of("broker-a"), vo.getSuccessfulBrokers());
        assertEquals("broker-b", vo.getFailedBrokers().get(0).getAddress());
    }
}
