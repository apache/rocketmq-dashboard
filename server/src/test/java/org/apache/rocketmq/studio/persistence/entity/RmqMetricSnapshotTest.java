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

class RmqMetricSnapshotTest {

    @Test
    void freshEntityCarriesNullFields() {
        RmqMetricSnapshot entity = new RmqMetricSnapshot();

        assertNull(entity.getId());
        assertNull(entity.getInstanceId());
        assertNull(entity.getMetricKey());
        assertNull(entity.getLabelsHash());
        assertNull(entity.getValue());
        assertNull(entity.getAvailability());
        assertNull(entity.getCollectedAt());
    }

    @Test
    void settersRoundTripEveryField() {
        RmqMetricSnapshot entity = new RmqMetricSnapshot();
        LocalDateTime collected = LocalDateTime.parse("2026-09-01T08:00:00");

        entity.setId(1L);
        entity.setInstanceId("inst-1");
        entity.setMetricKey("broker.disk.usage_ratio");
        entity.setDomain("CLUSTER");
        entity.setClusterId("cluster-1");
        entity.setLabelsHash("hash-1");
        entity.setLabelsJson("{\"node\":\"broker-a\"}");
        entity.setValue(0.85);
        entity.setAvailability("AVAILABLE");
        entity.setCollectedAt(collected);
        entity.setGmtCreate(collected);

        assertEquals(1L, entity.getId());
        assertEquals("inst-1", entity.getInstanceId());
        assertEquals("broker.disk.usage_ratio", entity.getMetricKey());
        assertEquals("hash-1", entity.getLabelsHash());
        assertEquals(0.85, entity.getValue());
        assertEquals("AVAILABLE", entity.getAvailability());
        assertEquals(collected, entity.getCollectedAt());
    }
}
