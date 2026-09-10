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
package org.apache.rocketmq.studio.ops.alert;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeAlertMetricInfoTest {

    @Test
    void exposesAllRecordComponents() {
        NativeAlertMetricInfo info = new NativeAlertMetricInfo("broker.disk.usage_ratio",
                "Broker disk usage", "ratio", true);

        assertEquals("broker.disk.usage_ratio", info.key());
        assertEquals("Broker disk usage", info.label());
        assertEquals("ratio", info.thresholdUnit());
        assertTrue(info.supportsConsumerGroup());
    }

    @Test
    void consumerGroupSupportMayBeAbsent() {
        NativeAlertMetricInfo info = new NativeAlertMetricInfo("broker.availability",
                "Broker availability", "none", false);

        assertFalse(info.supportsConsumerGroup());
    }

    @Test
    void equalityFollowsRecordComponents() {
        NativeAlertMetricInfo a = new NativeAlertMetricInfo("broker.availability",
                "Broker availability", "none", false);
        NativeAlertMetricInfo same = new NativeAlertMetricInfo("broker.availability",
                "Broker availability", "none", false);
        NativeAlertMetricInfo different = new NativeAlertMetricInfo("broker.disk.usage_ratio",
                "Broker disk usage", "ratio", false);

        assertEquals(a, same);
        assertEquals(a.hashCode(), same.hashCode());
        assertNotEquals(a, different);
    }
}
