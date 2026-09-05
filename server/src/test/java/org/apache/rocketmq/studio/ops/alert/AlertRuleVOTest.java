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
package org.apache.rocketmq.studio.ops.alert;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertRuleVOTest {

    @Test
    void builderDefaultsDescribeFreshRule() {
        AlertRuleVO vo = AlertRuleVO.builder().build();

        assertNull(vo.getId());
        assertEquals(AlertDomain.BUSINESS, vo.getDomain());
        assertNull(vo.getName());
        assertEquals(0.0, vo.getThreshold());
        assertEquals("LAST", vo.getAggregation());
        assertEquals(0, vo.getWindowSeconds());
        assertNull(vo.getChannels());
        assertFalse(vo.isEnabled());
        assertNull(vo.getSeverity());
        assertEquals(1, vo.getConsecutiveSamples());
        assertEquals("30m", vo.getReminderInterval());
        assertNull(vo.getNotificationTemplate());
    }

    @Test
    void allArgsCarryRuleState() {
        AlertRuleVO vo = AlertRuleVO.builder()
            .id(7L)
            .domain(AlertDomain.CLUSTER)
            .name("disk-high")
            .metric("broker.disk.usage_ratio")
            .operator(">")
            .threshold(0.9)
            .thresholdUnit("ratio")
            .duration("5m")
            .aggregation("MAX")
            .windowSeconds(60)
            .channels(List.of("email"))
            .enabled(true)
            .description("high disk usage")
            .brokerName("broker-a")
            .clusterName("DefaultCluster")
            .severity("warning")
            .instanceId("inst-1")
            .consumerGroup(null)
            .topic("orders")
            .consecutiveSamples(2)
            .reminderInterval("10m")
            .notificationTemplate("disk {{.Value}}")
            .build();

        assertEquals(7L, vo.getId());
        assertEquals(AlertDomain.CLUSTER, vo.getDomain());
        assertEquals("broker.disk.usage_ratio", vo.getMetric());
        assertEquals("MAX", vo.getAggregation());
        assertEquals(60, vo.getWindowSeconds());
        assertTrue(vo.isEnabled());
        assertEquals("warning", vo.getSeverity());
        assertEquals(2, vo.getConsecutiveSamples());
        assertEquals("10m", vo.getReminderInterval());
        assertEquals("disk {{.Value}}", vo.getNotificationTemplate());
    }
}
