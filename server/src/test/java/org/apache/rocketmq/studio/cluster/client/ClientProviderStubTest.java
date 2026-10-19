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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the degradation contract of {@link ClientProviderStub}: when no real client provider is
 * configured, every lookup fails loudly with a 501 instead of answering an empty list that the
 * UI would read as "no connections".
 */
class ClientProviderStubTest {

    private static void assertNotConfigured(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode()).isEqualTo(501))
                .hasMessage("Client connection provider is not configured");
    }

    @Test
    void findConnectionsFailsLoudlyWithNotImplemented() {
        assertNotConfigured(() -> new ClientProviderStub().findConnections("instance-1", "cluster-1", "PRODUCER"));
    }

    @Test
    void findProducerGroupsFailsLoudlyWithNotImplemented() {
        assertNotConfigured(() -> new ClientProviderStub().findProducerGroups("instance-1", "topic-1", "group", 10));
    }

    @Test
    void findProducerConnectionsFailsLoudlyWithNotImplemented() {
        assertNotConfigured(() -> new ClientProviderStub().findProducerConnections("instance-1", "topic-1", "group-1"));
    }
}
