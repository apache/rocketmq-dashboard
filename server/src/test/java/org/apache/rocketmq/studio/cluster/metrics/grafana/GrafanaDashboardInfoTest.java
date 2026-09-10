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
package org.apache.rocketmq.studio.cluster.metrics.grafana;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GrafanaDashboardInfoTest {

    @Test
    void exposesAllRecordComponents() {
        GrafanaDashboardInfo info = new GrafanaDashboardInfo("uid-1", "RocketMQ Overview",
                "Overview dashboard", List.of("rocketmq", "metrics"));

        assertEquals("uid-1", info.uid());
        assertEquals("RocketMQ Overview", info.title());
        assertEquals("Overview dashboard", info.description());
        assertEquals(List.of("rocketmq", "metrics"), info.tags());
    }

    @Test
    void tagsMayBeNull() {
        GrafanaDashboardInfo info = new GrafanaDashboardInfo("uid-1", "t", "d", null);

        assertNull(info.tags());
    }

    @Test
    void equalityFollowsRecordComponents() {
        GrafanaDashboardInfo a = new GrafanaDashboardInfo("uid-1", "t", "d", List.of("x"));
        GrafanaDashboardInfo same = new GrafanaDashboardInfo("uid-1", "t", "d", List.of("x"));
        GrafanaDashboardInfo different = new GrafanaDashboardInfo("uid-2", "t", "d", List.of("x"));

        assertEquals(a, same);
        assertEquals(a.hashCode(), same.hashCode());
        assertNotEquals(a, different);
    }
}
