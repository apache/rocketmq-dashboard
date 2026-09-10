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
 * Locks the alert severity vocabulary serialized by alert rule payloads.
 */
class AlertLevelTest {

    @Test
    void exposesAllSeverityLevels() {
        assertEquals(3, AlertLevel.values().length);
        assertTrue(Arrays.asList(AlertLevel.values()).contains(AlertLevel.error));
        assertTrue(Arrays.asList(AlertLevel.values()).contains(AlertLevel.warning));
        assertTrue(Arrays.asList(AlertLevel.values()).contains(AlertLevel.info));
    }

    @Test
    void enumNamesStayLowerCamelForJsonSerialization() {
        assertEquals("error", AlertLevel.error.name());
        assertEquals("warning", AlertLevel.warning.name());
        assertEquals("info", AlertLevel.info.name());
    }

    @Test
    void namesRoundTripThroughValueOf() {
        for (AlertLevel level : AlertLevel.values()) {
            assertEquals(level, AlertLevel.valueOf(level.name()));
        }
    }
}
