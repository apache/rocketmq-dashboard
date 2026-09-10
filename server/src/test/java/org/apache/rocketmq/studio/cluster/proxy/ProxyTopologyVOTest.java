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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyTopologyVOTest {

    @Test
    void builderDefaultsDescribeUnprobedNode() {
        ProxyTopologyVO vo = ProxyTopologyVO.builder().build();

        assertNull(vo.getProxyAddr());
        assertNull(vo.getStatus());
        assertEquals(0, vo.getGrpcPort());
        assertNull(vo.getRemotingPort());
        assertFalse(vo.isGrpcReachable());
        assertFalse(vo.isRemotingReachable());
        assertEquals(0L, vo.getLatencyMs());
    }

    @Test
    void allArgsCarryProbeOutcome() {
        ProxyTopologyVO vo = ProxyTopologyVO.builder()
            .proxyAddr("10.0.0.1:8081")
            .status("UP")
            .grpcPort(8081)
            .remotingPort(8080)
            .grpcReachable(true)
            .remotingReachable(true)
            .latencyMs(5L)
            .build();

        assertEquals("10.0.0.1:8081", vo.getProxyAddr());
        assertEquals("UP", vo.getStatus());
        assertEquals(8081, vo.getGrpcPort());
        assertEquals(8080, vo.getRemotingPort());
        assertTrue(vo.isGrpcReachable());
        assertTrue(vo.isRemotingReachable());
        assertEquals(5L, vo.getLatencyMs());
    }
}
