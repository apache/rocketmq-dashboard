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
 * Pins {@link MetricsSourceSettings}: the resolved connection settings every
 * Prometheus-compatible backend is queried through. The defaults are the operational budget - 3s
 * connect / 10s read - and the query path is the backend's own, delegated by type.
 */
class MetricsSourceSettingsTest {

    @Test
    void theBuilderDefaultsMatchTheOperationalBudget() {
        MetricsSourceSettings settings = MetricsSourceSettings.builder().build();

        assertThat(settings.getBackendType()).isEqualTo(MetricsBackendType.PROMETHEUS);
        assertThat(settings.getConnectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(settings.getReadTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(settings.getAuthType()).isEqualTo("none");
    }

    @Test
    void everySuppliedValueOverridesItsDefault() {
        MetricsSourceSettings settings = MetricsSourceSettings.builder()
                .backendType(MetricsBackendType.VICTORIA_METRICS)
                .baseUrl("http://vm:8428")
                .connectTimeout(Duration.ofSeconds(1))
                .readTimeout(Duration.ofSeconds(5))
                .authType("bearer")
                .bearerToken("token")
                .build();

        assertThat(settings.getBackendType()).isEqualTo(MetricsBackendType.VICTORIA_METRICS);
        assertThat(settings.getBaseUrl()).isEqualTo("http://vm:8428");
        assertThat(settings.getConnectTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(settings.getReadTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(settings.getAuthType()).isEqualTo("bearer");
        assertThat(settings.getBearerToken()).isEqualTo("token");
    }

    @Test
    void theQueryPathComesFromTheBackendType() {
        assertThat(MetricsSourceSettings.builder().build().getQueryPath())
                .isEqualTo("/api/v1/query_range");
        assertThat(MetricsSourceSettings.builder()
                        .backendType(MetricsBackendType.VICTORIA_METRICS).build().getQueryPath())
                .isEqualTo(MetricsBackendType.VICTORIA_METRICS.getQueryPath());
    }
}
