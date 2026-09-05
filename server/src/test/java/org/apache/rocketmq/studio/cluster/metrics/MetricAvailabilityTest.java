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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the metric availability vocabulary used by the native alert evaluation results.
 */
class MetricAvailabilityTest {

    @Test
    void exposesAllAvailabilityStates() {
        assertEquals(4, MetricAvailability.values().length);
        assertTrue(Arrays.asList(MetricAvailability.values()).contains(MetricAvailability.AVAILABLE));
        assertTrue(Arrays.asList(MetricAvailability.values()).contains(MetricAvailability.UNAVAILABLE));
        assertTrue(Arrays.asList(MetricAvailability.values()).contains(MetricAvailability.UNSUPPORTED));
        assertTrue(Arrays.asList(MetricAvailability.values()).contains(MetricAvailability.STALE));
    }

    @Test
    void enumNamesUseUpperSnakeCase() {
        assertEquals("AVAILABLE", MetricAvailability.AVAILABLE.name());
        assertEquals("UNSUPPORTED", MetricAvailability.UNSUPPORTED.name());
    }

    @Test
    void namesRoundTripThroughValueOf() {
        for (MetricAvailability availability : MetricAvailability.values()) {
            assertEquals(availability, MetricAvailability.valueOf(availability.name()));
        }
    }
}
