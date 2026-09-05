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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProducerConnectionVOTest {

    @Test
    void builderDefaultsDescribeEmptyConnection() {
        ProducerConnectionVO vo = ProducerConnectionVO.builder().build();

        assertNull(vo.getClientId());
        assertNull(vo.getClientAddr());
        assertNull(vo.getTopic());
        assertNull(vo.getProducerGroup());
        assertNull(vo.getLanguage());
        assertNull(vo.getVersionDesc());
    }

    @Test
    void allArgsCarryConnectionState() {
        ProducerConnectionVO vo = ProducerConnectionVO.builder()
            .clientId("client-1")
            .clientAddr("10.0.0.1:8080")
            .topic("orders")
            .producerGroup("cg-orders")
            .language("Java")
            .versionDesc("5.3.2")
            .build();

        assertEquals("client-1", vo.getClientId());
        assertEquals("10.0.0.1:8080", vo.getClientAddr());
        assertEquals("orders", vo.getTopic());
        assertEquals("cg-orders", vo.getProducerGroup());
        assertEquals("Java", vo.getLanguage());
        assertEquals("5.3.2", vo.getVersionDesc());
    }
}
