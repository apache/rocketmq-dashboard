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

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MetricsSourceSettings}, the resolved connection settings shared by
 * every Prometheus-compatible backend: sensible defaults when a field is omitted and the
 * backend-specific query path that distinguishes the sources at query time.
 */
class MetricsSourceSettingsTest {

    @Test
    void appliesSensibleDefaultsForOmittedFields() {
        MetricsSourceSettings settings = MetricsSourceSettings.builder()
                .baseUrl("http://prometheus:9090")
                .build();

        assertThat(settings.getBackendType()).isEqualTo(MetricsBackendType.PROMETHEUS);
        assertThat(settings.getConnectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(settings.getReadTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(settings.getAuthType()).isEqualTo("none");
    }

    @Test
    void exposesTheBackendSpecificQueryPath() {
        assertThat(MetricsSourceSettings.builder().backendType(MetricsBackendType.PROMETHEUS).build()
                .getQueryPath()).isEqualTo("/api/v1/query_range");
        assertThat(MetricsSourceSettings.builder().backendType(MetricsBackendType.MIMIR).build()
                .getQueryPath()).isEqualTo("/prometheus/api/v1/query_range");
        assertThat(MetricsSourceSettings.builder().backendType(MetricsBackendType.VICTORIA_METRICS).build()
                .getQueryPath()).isEqualTo("/select/0/prometheus/api/v1/query_range");
    }

    @Test
    void carriesTheExplicitlyConfiguredValues() {
        MetricsSourceSettings settings = MetricsSourceSettings.builder()
                .backendType(MetricsBackendType.THANOS)
                .baseUrl("http://thanos:10902")
                .connectTimeout(Duration.ofSeconds(1))
                .readTimeout(Duration.ofSeconds(2))
                .authType("bearer")
                .bearerToken("token")
                .build();

        assertThat(settings.getBackendType()).isEqualTo(MetricsBackendType.THANOS);
        assertThat(settings.getBaseUrl()).isEqualTo("http://thanos:10902");
        assertThat(settings.getConnectTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(settings.getReadTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(settings.getAuthType()).isEqualTo("bearer");
        assertThat(settings.getBearerToken()).isEqualTo("token");
        assertThat(settings.getQueryPath()).isEqualTo("/api/v1/query_range");
    }
}
