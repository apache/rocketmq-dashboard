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
package org.apache.rocketmq.studio.instance.dlq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DLQResendResultVOTest {

    @Test
    void builderDefaultsDescribeZeroedResult() {
        DLQResendResultVO vo = DLQResendResultVO.builder().build();

        assertEquals(0, vo.getMatched());
        assertEquals(0, vo.getResent());
        assertEquals(0, vo.getFailed());
        assertFalse(vo.isScanIncomplete());
        assertEquals(0, vo.getFailedQueueCount());
    }

    @Test
    void allArgsCarryResendSummary() {
        DLQResendResultVO vo = DLQResendResultVO.builder()
            .matched(5)
            .resent(4)
            .failed(1)
            .outcome("partially resent")
            .scanIncomplete(true)
            .failedQueueCount(1)
            .build();

        assertEquals(5, vo.getMatched());
        assertEquals(4, vo.getResent());
        assertEquals(1, vo.getFailed());
        assertEquals("partially resent", vo.getOutcome());
        assertTrue(vo.isScanIncomplete());
        assertEquals(1, vo.getFailedQueueCount());
    }
}
