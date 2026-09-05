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
package org.apache.rocketmq.studio.common.domain.enums;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the broker runtime state vocabulary surfaced by broker status views.
 */
class BrokerStatusTest {

    @Test
    void exposesAllBrokerRuntimeStates() {
        assertEquals(3, BrokerStatus.values().length);
        assertTrue(Arrays.asList(BrokerStatus.values()).contains(BrokerStatus.running));
        assertTrue(Arrays.asList(BrokerStatus.values()).contains(BrokerStatus.readonly));
        assertTrue(Arrays.asList(BrokerStatus.values()).contains(BrokerStatus.maintenance));
    }

    @Test
    void enumNamesStayLowerCamelForJsonSerialization() {
        assertEquals("running", BrokerStatus.running.name());
        assertEquals("readonly", BrokerStatus.readonly.name());
        assertEquals("maintenance", BrokerStatus.maintenance.name());
    }

    @Test
    void namesRoundTripThroughValueOf() {
        for (BrokerStatus status : BrokerStatus.values()) {
            assertEquals(status, BrokerStatus.valueOf(status.name()));
        }
    }
}
