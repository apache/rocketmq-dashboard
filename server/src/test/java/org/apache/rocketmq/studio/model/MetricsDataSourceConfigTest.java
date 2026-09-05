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
package org.apache.rocketmq.studio.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MetricsDataSourceConfig}: the raw data-source configuration model
 * carries credentials and must never leak them through {@code toString} while still
 * comparing equal on them.
 */
class MetricsDataSourceConfigTest {

    @Test
    void toStringRedactsPasswordAndBearerToken() {
        MetricsDataSourceConfig config = new MetricsDataSourceConfig();
        config.setName("prometheus");
        config.setUrl("https://metrics.example.test");
        config.setPassword("plain-password");
        config.setBearerToken("plain-bearer");

        String value = config.toString();

        assertThat(value).contains("name=prometheus").contains("url=https://metrics.example.test");
        assertThat(value).doesNotContain("password").doesNotContain("bearerToken");
        assertThat(value).doesNotContain("plain-password").doesNotContain("plain-bearer");
    }

    @Test
    void dataEqualityCoversPasswordAndBearerToken() {
        MetricsDataSourceConfig first = new MetricsDataSourceConfig();
        first.setName("prometheus");
        first.setPassword("pw-1");
        first.setBearerToken("bt-1");

        MetricsDataSourceConfig same = new MetricsDataSourceConfig();
        same.setName("prometheus");
        same.setPassword("pw-1");
        same.setBearerToken("bt-1");

        MetricsDataSourceConfig rotated = new MetricsDataSourceConfig();
        rotated.setName("prometheus");
        rotated.setPassword("pw-1");
        rotated.setBearerToken("bt-2");

        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(first).isNotEqualTo(rotated);
    }
}
