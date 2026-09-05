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

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the test-only client provider fallback: every operation must stay loud (501)
 * instead of silently degrading when no real client provider is wired up.
 */
class ClientProviderStubTest {

    private final ClientProviderStub stub = new ClientProviderStub();

    private BusinessException unsupportedFrom(org.junit.jupiter.api.function.Executable action) {
        return assertThrows(BusinessException.class, action);
    }

    @Test
    void findConnectionsThrowsUnsupported() {
        BusinessException ex = unsupportedFrom(() -> stub.findConnections("inst-1", "cluster-1", "PRODUCER"));

        assertEquals(501, ex.getCode());
        assertTrue(ex.getMessage().contains("Client connection provider is not configured"));
    }

    @Test
    void findProducerGroupsThrowsUnsupported() {
        BusinessException ex = unsupportedFrom(
                () -> stub.findProducerGroups("inst-1", "orders", "order", 10));

        assertEquals(501, ex.getCode());
        assertTrue(ex.getMessage().contains("Client connection provider is not configured"));
    }

    @Test
    void findProducerConnectionsThrowsUnsupported() {
        BusinessException ex = unsupportedFrom(
                () -> stub.findProducerConnections("inst-1", "orders", "cg-1"));

        assertEquals(501, ex.getCode());
        assertTrue(ex.getMessage().contains("Client connection provider is not configured"));
    }
}
