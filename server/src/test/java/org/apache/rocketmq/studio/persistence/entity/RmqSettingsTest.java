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
package org.apache.rocketmq.studio.persistence.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RmqSettingsTest {

    @Test
    void toStringShouldNotExposePersistedSettingsJson() {
        RmqSettings settings = new RmqSettings();
        settings.setId(1L);
        settings.setJson("{\"apiKey\":\"sk-secret\",\"model\":\"gpt-4o\"}");
        settings.setGmtModified(LocalDateTime.of(2026, 8, 3, 12, 0));

        String text = settings.toString();

        assertThat(text).contains("id=1");
        assertThat(text).contains("gmtModified=2026-08-03T12:00");
        assertThat(text).doesNotContain("json");
        assertThat(text).doesNotContain("sk-secret");
    }

    @Test
    void dataEqualityCoversKeyJsonAndTimestampsTest() {
        LocalDateTime created = LocalDateTime.of(2026, 8, 1, 9, 0);
        RmqSettings first = new RmqSettings();
        first.setId(1L);
        first.setSettingsKey("llm.openai");
        first.setJson("{\"apiKey\":\"sk-secret\"}");
        first.setGmtCreate(created);

        RmqSettings same = new RmqSettings();
        same.setId(1L);
        same.setSettingsKey("llm.openai");
        same.setJson("{\"apiKey\":\"sk-secret\"}");
        same.setGmtCreate(created);

        RmqSettings differentJson = new RmqSettings();
        differentJson.setId(1L);
        differentJson.setSettingsKey("llm.openai");
        differentJson.setJson("{\"apiKey\":\"sk-other\"}");
        differentJson.setGmtCreate(created);

        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(first).isNotEqualTo(differentJson);
    }
}
