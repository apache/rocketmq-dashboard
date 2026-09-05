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
package org.apache.rocketmq.studio.cluster.proxy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyHealthProbeTest {

    @Test
    void unreachableOutcomeCarriesNegativeLatency() {
        ProxyHealthProbe.ProbeResult result = ProxyHealthProbe.ProbeResult.unreachable();

        assertFalse(result.reachable());
        assertEquals(-1L, result.latencyMs());
    }

    @Test
    void reachableOutcomeKeepsPositiveLatency() {
        ProxyHealthProbe.ProbeResult result = ProxyHealthProbe.ProbeResult.reachable(5L);

        assertTrue(result.reachable());
        assertEquals(5L, result.latencyMs());
    }

    @Test
    void reachableOutcomeClampsNegativeLatencyToZero() {
        ProxyHealthProbe.ProbeResult result = ProxyHealthProbe.ProbeResult.reachable(-3L);

        assertTrue(result.reachable());
        assertEquals(0L, result.latencyMs());
    }

    @Test
    void reachableOutcomeKeepsZeroLatency() {
        ProxyHealthProbe.ProbeResult result = ProxyHealthProbe.ProbeResult.reachable(0L);

        assertTrue(result.reachable());
        assertEquals(0L, result.latencyMs());
    }

    @Test
    void equalityFollowsRecordComponents() {
        ProxyHealthProbe.ProbeResult a = ProxyHealthProbe.ProbeResult.reachable(5L);
        ProxyHealthProbe.ProbeResult same = ProxyHealthProbe.ProbeResult.reachable(5L);
        ProxyHealthProbe.ProbeResult slower = ProxyHealthProbe.ProbeResult.reachable(9L);
        ProxyHealthProbe.ProbeResult down = ProxyHealthProbe.ProbeResult.unreachable();

        assertEquals(a, same);
        assertEquals(a.hashCode(), same.hashCode());
        assertNotEquals(a, slower);
        assertNotEquals(a, down);
    }
}
