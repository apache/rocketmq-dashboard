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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AlertFingerprintTest {
    @Test
    void isStableRegardlessOfLabelMapOrderTest() {
        Map<String, String> first = new LinkedHashMap<>();
        first.put("broker", "a");
        first.put("queue", "1");
        Map<String, String> second = new LinkedHashMap<>();
        second.put("queue", "1");
        second.put("broker", "a");

        assertThat(AlertFingerprint.of(7L, "local", first))
                .isEqualTo(AlertFingerprint.of(7L, "local", second))
                .hasSize(64);
    }

    @Test
    void separatorCharactersCannotCreateTheSameFingerprintForDifferentLabelsTest() {
        Map<String, String> embeddedLabel = Map.of("a", "b\nc=d");
        Map<String, String> separateLabels = Map.of("a", "b", "c", "d");

        assertThat(AlertFingerprint.of(7L, "local", embeddedLabel))
                .isNotEqualTo(AlertFingerprint.of(7L, "local", separateLabels));
    }
}
