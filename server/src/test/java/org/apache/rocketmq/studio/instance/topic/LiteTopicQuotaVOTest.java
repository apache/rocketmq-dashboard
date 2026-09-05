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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LiteTopicQuotaVOTest {

    @Test
    void builderDefaultsDescribeEmptyQuota() {
        LiteTopicQuotaVO vo = LiteTopicQuotaVO.builder().build();

        assertNull(vo.getCurrentTopicCount());
        assertNull(vo.getMaxTopicCount());
        assertNull(vo.getCurrentSessionCount());
        assertNull(vo.getUsageRate());
        assertNull(vo.getSessionUsageRate());
        assertNull(vo.getDefaultTTL());
        assertNull(vo.getMaxTTL());
        assertNull(vo.getRemainingQuota());
        assertNull(vo.getConsumerDensity());
    }

    @Test
    void allArgsCarryQuotaState() {
        LiteTopicQuotaVO vo = LiteTopicQuotaVO.builder()
            .currentTopicCount(3)
            .maxTopicCount(10)
            .currentSessionCount(2)
            .maxSessionCount(20)
            .currentCreationRate(1)
            .maxCreationRate(5)
            .usageRate(0.3)
            .sessionUsageRate(0.1)
            .defaultTTL(3600L)
            .maxTTL(86400L)
            .remainingQuota(7)
            .consumerDensity(0.5)
            .build();

        assertEquals(3, vo.getCurrentTopicCount());
        assertEquals(10, vo.getMaxTopicCount());
        assertEquals(0.3, vo.getUsageRate());
        assertEquals(0.1, vo.getSessionUsageRate());
        assertEquals(3600L, vo.getDefaultTTL());
        assertEquals(7, vo.getRemainingQuota());
        assertEquals(0.5, vo.getConsumerDensity());
    }
}
