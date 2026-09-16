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

    @Test
    void limitPropertiesShouldNotSplitASurrogatePairTest() {
        // The cap lands between the two chars of the emoji when the value is counted in chars.
        String value = "a".repeat(MessagePropertyDisplay.MAX_PROPERTY_VALUE_CHARS - 1) + EMOJI + "tail";
        String abbreviated = MessagePropertyDisplay.limitProperties(Map.of("big", value)).get("big");
        assertThat(hasUnpairedSurrogate(abbreviated)).isFalse();
        assertThat(abbreviated)
                .isEqualTo("a".repeat(MessagePropertyDisplay.MAX_PROPERTY_VALUE_CHARS - 1) + EMOJI + "...");
    }

    @Test
    void limitPropertiesShouldKeepASupplementaryCharacterThatFitsTheCapTest() {
        String value = "a".repeat(MessagePropertyDisplay.MAX_PROPERTY_VALUE_CHARS - 2) + EMOJI + "tail";
        String abbreviated = MessagePropertyDisplay.limitProperties(Map.of("big", value)).get("big");
        assertThat(hasUnpairedSurrogate(abbreviated)).isFalse();
        // 1022 a's + the emoji + 4 tail code points is 1027, so the cut keeps everything up to and
        // including the first tail character, and the emoji survives whole.
        assertThat(abbreviated)
                .isEqualTo("a".repeat(MessagePropertyDisplay.MAX_PROPERTY_VALUE_CHARS - 2) + EMOJI + "t...");
    }

    @Test
    void hasOversizedPropertyShouldCountCodePointsNotCharsTest() {
        // 1024 code points, 1624 chars: over the char cap, exactly at the code point cap, and
        // abbreviate leaves it untouched. Counting chars would call it oversized.
        String atCap = EMOJI.repeat(600) + "a".repeat(MessagePropertyDisplay.MAX_PROPERTY_VALUE_CHARS - 600);
        assertThat(atCap.length()).isGreaterThan(MessagePropertyDisplay.MAX_PROPERTY_VALUE_CHARS);
        assertThat(MessagePropertyDisplay.hasOversizedProperty(Map.of("big", atCap))).isFalse();
        assertThat(MessagePropertyDisplay.limitProperties(Map.of("big", atCap)).get("big")).isEqualTo(atCap);

        // One more code point is over the cap, and is abbreviated.
        String overCap = atCap + "a";
        assertThat(MessagePropertyDisplay.hasOversizedProperty(Map.of("big", overCap))).isTrue();
        assertThat(MessagePropertyDisplay.limitProperties(Map.of("big", overCap)).get("big")).endsWith("...");
    }

    /** Emoji is one code point, two UTF-16 chars: the case the cap has to survive. */
    private static final String EMOJI = "\uD83D\uDE00";

    private static boolean hasUnpairedSurrogate(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return true;
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                return true;
            }
        }
        return false;
    }
}
