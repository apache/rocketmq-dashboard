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

import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProxyVOTest {

    @Test
    void builderDefaultsDescribeEmptyProxy() {
        ProxyVO vo = ProxyVO.builder().build();

        assertNull(vo.getAddr());
        assertNull(vo.getStatus());
        assertEquals(0, vo.getConnections());
        assertEquals(0, vo.getGrpcPort());
        assertEquals(0, vo.getRemotingPort());
    }

    @Test
    void allArgsCarryProxyState() {
        ProxyVO vo = ProxyVO.builder()
            .addr("10.0.0.1:8081")
            .status(ClusterStatus.healthy)
            .connections(12)
            .grpcPort(8081)
            .remotingPort(8080)
            .build();

        assertEquals("10.0.0.1:8081", vo.getAddr());
        assertEquals(ClusterStatus.healthy, vo.getStatus());
        assertEquals(12, vo.getConnections());
        assertEquals(8081, vo.getGrpcPort());
        assertEquals(8080, vo.getRemotingPort());
    }
}
