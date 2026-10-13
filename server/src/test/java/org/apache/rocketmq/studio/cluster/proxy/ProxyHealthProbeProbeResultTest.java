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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the probe outcome of {@link ProxyHealthProbe.ProbeResult}: reachable with the measured
 * latency - or the -1 sentinel when unreachable. The result is the single source the topology
 * view and the health column both render from.
 */
class ProxyHealthProbeProbeResultTest {

    @Test
    void aReachableProbeCarriesItsLatency() {
        ProxyHealthProbe.ProbeResult result = new ProxyHealthProbe.ProbeResult(true, 42L);
        assertThat(result.reachable()).isTrue();
        assertThat(result.latencyMs()).isEqualTo(42L);
    }

    @Test
    void anUnreachableProbeCarriesTheSentinel() {
        ProxyHealthProbe.ProbeResult result = new ProxyHealthProbe.ProbeResult(false, -1L);
        assertThat(result.reachable()).isFalse();
        assertThat(result.latencyMs()).isEqualTo(-1L);
    }

    @Test
    void theResultIsValueEqual() {
        assertThat(new ProxyHealthProbe.ProbeResult(true, 42L))
                .isEqualTo(new ProxyHealthProbe.ProbeResult(true, 42L));
        assertThat(new ProxyHealthProbe.ProbeResult(true, 42L))
                .isNotEqualTo(new ProxyHealthProbe.ProbeResult(false, -1L));
    }
}
