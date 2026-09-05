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
package org.apache.rocketmq.studio.persistence.entity;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RmqAlertRuleTest {

    @Test
    void freshEntityCarriesNullFields() {
        RmqAlertRule entity = new RmqAlertRule();

        assertNull(entity.getId());
        assertNull(entity.getDomain());
        assertNull(entity.getName());
        assertNull(entity.getMetric());
        assertNull(entity.getThreshold());
        assertNull(entity.getEnabled());
        assertNull(entity.getClusterName());
    }

    @Test
    void settersRoundTripRepresentativeFields() {
        RmqAlertRule entity = new RmqAlertRule();
        LocalDateTime created = LocalDateTime.parse("2026-09-01T08:00:00");

        entity.setId(7L);
        entity.setDomain("CLUSTER");
        entity.setName("disk-high");
        entity.setMetric("broker.disk.usage_ratio");
        entity.setOperator(">");
        entity.setThreshold(0.9);
        entity.setThresholdUnit("ratio");
        entity.setDuration("5m");
        entity.setAggregation("MAX");
        entity.setWindowSeconds(60);
        entity.setChannels("email,sms");
        entity.setEnabled(true);
        entity.setDescription("high disk usage");
        entity.setBrokerName("broker-a");
        entity.setClusterName("DefaultCluster");
        entity.setGmtCreate(created);
        entity.setGmtModified(created);

        assertEquals(7L, entity.getId());
        assertEquals("CLUSTER", entity.getDomain());
        assertEquals("disk-high", entity.getName());
        assertEquals(0.9, entity.getThreshold());
        assertEquals(Boolean.TRUE, entity.getEnabled());
        assertEquals("broker-a", entity.getBrokerName());
        assertEquals("DefaultCluster", entity.getClusterName());
        assertEquals(created, entity.getGmtCreate());
    }
}
