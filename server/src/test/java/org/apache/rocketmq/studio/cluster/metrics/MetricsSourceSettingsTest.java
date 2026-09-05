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
package org.apache.rocketmq.studio.cluster.metrics;

import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MetricsSourceSettingsTest {

    @Test
    void builderDefaultsDescribeUntouchedSettings() {
        MetricsSourceSettings settings = MetricsSourceSettings.builder().build();

        assertEquals(MetricsBackendType.PROMETHEUS, settings.getBackendType());
        assertNull(settings.getBaseUrl());
        assertEquals(Duration.ofSeconds(3), settings.getConnectTimeout());
        assertEquals(Duration.ofSeconds(10), settings.getReadTimeout());
        assertEquals("none", settings.getAuthType());
        assertNull(settings.getUsername());
        assertNull(settings.getPassword());
        assertNull(settings.getBearerToken());
    }

    @Test
    void allArgsCarryConfiguredSettings() {
        MetricsSourceSettings settings = MetricsSourceSettings.builder()
            .backendType(MetricsBackendType.VICTORIA_METRICS)
            .baseUrl("https://victoria.example.com")
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(20))
            .authType("basic")
            .username("reader")
            .password("sk-1")
            .bearerToken("token-1")
            .build();

        assertEquals(MetricsBackendType.VICTORIA_METRICS, settings.getBackendType());
        assertEquals("https://victoria.example.com", settings.getBaseUrl());
        assertEquals(Duration.ofSeconds(5), settings.getConnectTimeout());
        assertEquals(Duration.ofSeconds(20), settings.getReadTimeout());
        assertEquals("basic", settings.getAuthType());
        assertEquals("reader", settings.getUsername());
        assertEquals("sk-1", settings.getPassword());
        assertEquals("token-1", settings.getBearerToken());
    }

    @Test
    void queryPathDelegatesToBackendType() {
        MetricsSourceSettings prometheus = MetricsSourceSettings.builder().build();
        assertEquals("/api/v1/query_range", prometheus.getQueryPath());

        MetricsSourceSettings mimir = MetricsSourceSettings.builder()
            .backendType(MetricsBackendType.MIMIR)
            .build();
        assertEquals("/prometheus/api/v1/query_range", mimir.getQueryPath());
    }
}
