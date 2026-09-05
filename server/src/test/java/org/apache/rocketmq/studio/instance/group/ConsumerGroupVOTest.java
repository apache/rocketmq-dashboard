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
package org.apache.rocketmq.studio.instance.group;

import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.domain.enums.SubscriptionMode;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumerGroupVOTest {

    @Test
    void freshVoCarriesEmptyCollectionsAndZeroCounters() {
        ConsumerGroupVO vo = new ConsumerGroupVO();

        assertNull(vo.getId());
        assertNull(vo.getName());
        assertNull(vo.getSubscriptionMode());
        assertNull(vo.getConsumeType());
        assertEquals(0, vo.getOnlineInstances());
        assertEquals(0L, vo.getTotalLag());
        assertTrue(vo.getSubscribedTopics().isEmpty());
        assertEquals(0, vo.getRetryMaxTimes());
        assertEquals(0, vo.getDelaySeconds());
        assertFalse(vo.isConsumeStatsAvailable());
        assertTrue(vo.getInstances().isEmpty());
    }

    @Test
    void settersRoundTripRepresentativeFields() {
        ConsumerGroupVO vo = new ConsumerGroupVO();

        vo.setId(7L);
        vo.setName("cg-orders");
        vo.setClusterId("cluster-1");
        vo.setInstanceId("inst-1");
        vo.setSubscriptionMode(SubscriptionMode.Push);
        vo.setConsumeType(ConsumeType.CLUSTERING);
        vo.setOnlineInstances(2);
        vo.setTotalLag(150L);
        vo.setSubscribedTopics(List.of("orders"));
        vo.setRetryMaxTimes(3);
        vo.setDelaySeconds(10);
        vo.setConsumeStatsAvailable(true);

        assertEquals(7L, vo.getId());
        assertEquals("cg-orders", vo.getName());
        assertEquals(SubscriptionMode.Push, vo.getSubscriptionMode());
        assertEquals(ConsumeType.CLUSTERING, vo.getConsumeType());
        assertEquals(2, vo.getOnlineInstances());
        assertEquals(150L, vo.getTotalLag());
        assertEquals(List.of("orders"), vo.getSubscribedTopics());
        assertTrue(vo.isConsumeStatsAvailable());
    }
}
