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
 * Locks the instance type vocabulary used by instance create/list filters and the UI selector.
 */
class InstanceTypeTest {

    @Test
    void exposesAllInstanceTypes() {
        assertEquals(4, InstanceType.values().length);
        assertTrue(Arrays.asList(InstanceType.values()).contains(InstanceType.CLOUD));
        assertTrue(Arrays.asList(InstanceType.values()).contains(InstanceType.PROXY_LOCAL));
        assertTrue(Arrays.asList(InstanceType.values()).contains(InstanceType.PROXY_CLUSTER));
        assertTrue(Arrays.asList(InstanceType.values()).contains(InstanceType.DIRECT));
    }

    @Test
    void enumNamesUseUpperSnakeCase() {
        assertEquals("CLOUD", InstanceType.CLOUD.name());
        assertEquals("PROXY_LOCAL", InstanceType.PROXY_LOCAL.name());
        assertEquals("PROXY_CLUSTER", InstanceType.PROXY_CLUSTER.name());
        assertEquals("DIRECT", InstanceType.DIRECT.name());
    }

    @Test
    void namesRoundTripThroughValueOf() {
        for (InstanceType type : InstanceType.values()) {
            assertEquals(type, InstanceType.valueOf(type.name()));
        }
    }
}
