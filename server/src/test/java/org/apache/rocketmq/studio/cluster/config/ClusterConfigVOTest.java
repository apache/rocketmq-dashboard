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

import org.apache.rocketmq.studio.common.domain.enums.FlushDiskType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClusterConfigVOTest {

    @Test
    void builderDefaultsDescribeEmptyConfig() {
        ClusterConfigVO vo = ClusterConfigVO.builder().build();

        assertEquals(0, vo.getWriteQueueNums());
        assertEquals(0, vo.getReadQueueNums());
        assertEquals(0, vo.getMaxMessageSize());
        assertNull(vo.getMsgTraceTopicName());
        assertFalse(vo.isAutoCreateTopicEnable());
        assertFalse(vo.isAutoCreateSubscriptionGroup());
        assertNull(vo.getDeleteWhen());
        assertEquals(0, vo.getFileReservedTime());
        assertNull(vo.getFlushDiskType());
        assertEquals(0, vo.getBrokerPermission());
    }

    @Test
    void allArgsCarryConfigState() {
        ClusterConfigVO vo = ClusterConfigVO.builder()
            .writeQueueNums(8)
            .readQueueNums(8)
            .maxMessageSize(4 * 1024 * 1024)
            .msgTraceTopicName("RMQ_SYS_TRACE_TOPIC")
            .autoCreateTopicEnable(true)
            .autoCreateSubscriptionGroup(false)
            .deleteWhen("16")
            .fileReservedTime(72)
            .flushDiskType(FlushDiskType.ASYNC_FLUSH)
            .brokerPermission(6)
            .build();

        assertEquals(8, vo.getWriteQueueNums());
        assertEquals(8, vo.getReadQueueNums());
        assertEquals(4 * 1024 * 1024, vo.getMaxMessageSize());
        assertEquals("RMQ_SYS_TRACE_TOPIC", vo.getMsgTraceTopicName());
        assertTrue(vo.isAutoCreateTopicEnable());
        assertFalse(vo.isAutoCreateSubscriptionGroup());
        assertEquals(72, vo.getFileReservedTime());
        assertEquals(FlushDiskType.ASYNC_FLUSH, vo.getFlushDiskType());
        assertEquals(6, vo.getBrokerPermission());
    }
}
