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
 * Locks the cluster health vocabulary surfaced by the cluster list views.
 */
class ClusterStatusTest {

    @Test
    void exposesAllClusterHealthStates() {
        assertEquals(4, ClusterStatus.values().length);
        assertTrue(Arrays.asList(ClusterStatus.values()).contains(ClusterStatus.healthy));
        assertTrue(Arrays.asList(ClusterStatus.values()).contains(ClusterStatus.warning));
        assertTrue(Arrays.asList(ClusterStatus.values()).contains(ClusterStatus.error));
        assertTrue(Arrays.asList(ClusterStatus.values()).contains(ClusterStatus.offline));
    }

    @Test
    void enumNamesStayLowerCamelForJsonSerialization() {
        assertEquals("healthy", ClusterStatus.healthy.name());
        assertEquals("warning", ClusterStatus.warning.name());
        assertEquals("error", ClusterStatus.error.name());
        assertEquals("offline", ClusterStatus.offline.name());
    }

    @Test
    void namesRoundTripThroughValueOf() {
        for (ClusterStatus status : ClusterStatus.values()) {
            assertEquals(status, ClusterStatus.valueOf(status.name()));
        }
    }
}
