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

import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicConsumerVOTest {

    @Test
    void builderDefaultsDescribeFreshConsumer() {
        TopicConsumerVO vo = TopicConsumerVO.builder().build();

        assertNull(vo.getGroup());
        assertNull(vo.getConsumeType());
        assertNull(vo.getMessageModel());
        assertEquals(0.0, vo.getConsumeTps());
        assertEquals(0L, vo.getDiffTotal());
        assertTrue(vo.isMetricsAvailable());
    }

    @Test
    void allArgsCarryConsumerState() {
        TopicConsumerVO vo = TopicConsumerVO.builder()
            .group("cg-orders")
            .consumeType(ConsumeType.CLUSTERING)
            .messageModel("CLUSTERING")
            .consumeTps(12.5)
            .diffTotal(100L)
            .metricsAvailable(false)
            .build();

        assertEquals("cg-orders", vo.getGroup());
        assertEquals(ConsumeType.CLUSTERING, vo.getConsumeType());
        assertEquals(12.5, vo.getConsumeTps());
        assertEquals(100L, vo.getDiffTotal());
    }
}
