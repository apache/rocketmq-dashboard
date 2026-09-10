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
package org.apache.rocketmq.studio.cluster.metrics;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricProfileTest {

    @Test
    void exposesBothKnownProfiles() {
        assertEquals(2, MetricProfile.values().length);
        assertTrue(Arrays.asList(MetricProfile.values()).contains(MetricProfile.ROCKETMQ_4_EXPORTER));
        assertTrue(Arrays.asList(MetricProfile.values()).contains(MetricProfile.ROCKETMQ_5_NATIVE));
    }

    @Test
    void rocketmq4ProfileExposesItsMetadata() {
        MetricProfile profile = MetricProfile.ROCKETMQ_4_EXPORTER;

        assertEquals("rocketmq4-exporter", profile.getId());
        assertEquals("RocketMQ 4.x Exporter", profile.getDisplayName());
        assertTrue(profile.getDescription().contains("rocketmq-exporter"));
    }

    @Test
    void rocketmq5ProfileExposesItsMetadata() {
        MetricProfile profile = MetricProfile.ROCKETMQ_5_NATIVE;

        assertEquals("rocketmq5-native", profile.getId());
        assertEquals("RocketMQ 5.x Native", profile.getDisplayName());
        assertTrue(profile.getDescription().contains("OpenTelemetry"));
    }

    @Test
    void profileIdsAreUnique() {
        long distinctIds = Arrays.stream(MetricProfile.values())
            .map(MetricProfile::getId)
            .distinct()
            .count();

        assertEquals(MetricProfile.values().length, distinctIds);
        assertNotEquals(MetricProfile.ROCKETMQ_4_EXPORTER.getId(),
                MetricProfile.ROCKETMQ_5_NATIVE.getId());
    }
}
