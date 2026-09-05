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
package org.apache.rocketmq.studio.instance.message;

import org.apache.rocketmq.studio.common.domain.enums.DeliveryStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConsumerStatusVOTest {

    @Test
    void builderDefaultsDescribeEmptyStatus() {
        ConsumerStatusVO vo = ConsumerStatusVO.builder().build();

        assertNull(vo.getGroup());
        assertNull(vo.getDeliveryStatus());
        assertEquals(0L, vo.getConsumeTime());
        assertEquals(0, vo.getRetryCount());
    }

    @Test
    void allArgsCarryConsumerStatus() {
        ConsumerStatusVO vo = ConsumerStatusVO.builder()
            .group("cg-orders")
            .deliveryStatus(DeliveryStatus.success)
            .consumeTime(1784246400000L)
            .retryCount(1)
            .build();

        assertEquals("cg-orders", vo.getGroup());
        assertEquals(DeliveryStatus.success, vo.getDeliveryStatus());
        assertEquals(1784246400000L, vo.getConsumeTime());
        assertEquals(1, vo.getRetryCount());
    }
}
