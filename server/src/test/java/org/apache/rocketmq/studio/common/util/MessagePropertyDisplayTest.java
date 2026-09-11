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
package org.apache.rocketmq.studio.common.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MessagePropertyDisplayTest {

    @Test
    void userPropertiesShouldDropBrokerSystemKeysTest() {
        Map<String, String> properties = new HashMap<>();
        properties.put("REAL_TOPIC", "%RETRY%group");
        properties.put("KEYS", "k1");
        properties.put("traceId", "abc-123");
        assertThat(MessagePropertyDisplay.userProperties(properties))
                .containsEntry("traceId", "abc-123")
                .doesNotContainKeys("REAL_TOPIC", "KEYS");
    }

    @Test
    void userPropertiesShouldReturnEmptyForNullOrEmptyTest() {
        assertThat(MessagePropertyDisplay.userProperties(null)).isEmpty();
        assertThat(MessagePropertyDisplay.userProperties(Map.of())).isEmpty();
    }

    @Test
    void limitPropertiesShouldCapEntryCountTest() {
        Map<String, String> properties = new HashMap<>();
        for (int i = 0; i < 80; i++) {
            properties.put(String.format("k%02d", i), "v" + i);
        }
        assertThat(MessagePropertyDisplay.limitProperties(properties))
                .hasSize(MessagePropertyDisplay.MAX_PROPERTIES);
    }

    @Test
    void limitPropertiesShouldAbbreviateOversizedValueTest() {
        Map<String, String> limited = MessagePropertyDisplay.limitProperties(Map.of("big", "x".repeat(1500)));
        assertThat(limited.get("big")).isEqualTo("x".repeat(1024) + "...");
    }

    @Test
    void hasOversizedPropertyShouldDetectLongValuesTest() {
        assertThat(MessagePropertyDisplay.hasOversizedProperty(Map.of("big", "x".repeat(1500)))).isTrue();
        assertThat(MessagePropertyDisplay.hasOversizedProperty(Map.of("k", "short"))).isFalse();
        assertThat(MessagePropertyDisplay.hasOversizedProperty(null)).isFalse();
    }
}
