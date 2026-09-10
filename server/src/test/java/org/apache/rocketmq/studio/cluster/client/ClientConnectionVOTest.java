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
package org.apache.rocketmq.studio.cluster.client;

import org.apache.rocketmq.studio.common.domain.enums.ClientLanguage;
import org.apache.rocketmq.studio.common.domain.enums.ClientType;
import org.apache.rocketmq.studio.common.domain.enums.Protocol;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class ClientConnectionVOTest {

    @Test
    void builderDefaultsDescribeEmptyConnection() {
        ClientConnectionVO vo = ClientConnectionVO.builder().build();

        assertNull(vo.getClientId());
        assertNull(vo.getType());
        assertNull(vo.getGroupOrTopic());
        assertNull(vo.getProtocol());
        assertNull(vo.getLanguage());
        assertFalse(vo.isPartial());
        assertNull(vo.getClusterName());
    }

    @Test
    void allArgsCarryConnectionState() {
        LocalDateTime connected = LocalDateTime.parse("2026-09-01T08:00:00");

        ClientConnectionVO vo = ClientConnectionVO.builder()
            .clientId("client-1")
            .type(ClientType.Consumer)
            .groupOrTopic("cg-orders")
            .producerGroup("cg-orders")
            .protocol(Protocol.gRPC)
            .address("10.0.0.1:8081")
            .language(ClientLanguage.Java)
            .version("5.3.2")
            .connectedAt(connected)
            .partial(true)
            .clusterName("DefaultCluster")
            .build();

        assertEquals("client-1", vo.getClientId());
        assertEquals(ClientType.Consumer, vo.getType());
        assertEquals("cg-orders", vo.getGroupOrTopic());
        assertEquals(Protocol.gRPC, vo.getProtocol());
        assertEquals(ClientLanguage.Java, vo.getLanguage());
        assertEquals(connected, vo.getConnectedAt());
        assertEquals(true, vo.isPartial());
        assertEquals("DefaultCluster", vo.getClusterName());
    }
}
