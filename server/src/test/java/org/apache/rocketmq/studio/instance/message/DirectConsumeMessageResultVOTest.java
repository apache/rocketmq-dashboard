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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectConsumeMessageResultVOTest {

    @Test
    void builderDefaultsDescribeEmptyConsume() {
        DirectConsumeMessageResultVO vo = DirectConsumeMessageResultVO.builder().build();

        assertEquals(0L, vo.getSpentTimeMillis());
        assertFalse(vo.isOrder());
        assertFalse(vo.isAutoCommit());
    }

    @Test
    void allArgsCarryConsumeOutcome() {
        DirectConsumeMessageResultVO vo = DirectConsumeMessageResultVO.builder()
            .consumeResult("CONSUME_SUCCESS")
            .remark("consumed")
            .spentTimeMillis(12L)
            .order(true)
            .autoCommit(true)
            .build();

        assertEquals("CONSUME_SUCCESS", vo.getConsumeResult());
        assertEquals("consumed", vo.getRemark());
        assertEquals(12L, vo.getSpentTimeMillis());
        assertTrue(vo.isOrder());
        assertTrue(vo.isAutoCommit());
    }
}
