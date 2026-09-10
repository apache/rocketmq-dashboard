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
 * Locks the cluster topology vocabulary used by cluster registration payloads.
 */
class ClusterTypeTest {

    @Test
    void exposesAllClusterTopologyTypes() {
        assertEquals(3, ClusterType.values().length);
        assertTrue(Arrays.asList(ClusterType.values()).contains(ClusterType.V4_DIRECT));
        assertTrue(Arrays.asList(ClusterType.values()).contains(ClusterType.V5_PROXY_LOCAL));
        assertTrue(Arrays.asList(ClusterType.values()).contains(ClusterType.V5_PROXY_CLUSTER));
    }

    @Test
    void enumNamesUseUpperSnakeCase() {
        assertEquals("V4_DIRECT", ClusterType.V4_DIRECT.name());
        assertEquals("V5_PROXY_LOCAL", ClusterType.V5_PROXY_LOCAL.name());
        assertEquals("V5_PROXY_CLUSTER", ClusterType.V5_PROXY_CLUSTER.name());
    }

    @Test
    void namesRoundTripThroughValueOf() {
        for (ClusterType type : ClusterType.values()) {
            assertEquals(type, ClusterType.valueOf(type.name()));
        }
    }
}
