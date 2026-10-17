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
 * Pins the resolved connection settings of {@link MetricsSourceSettings}: the builder defaults
 * (PROMETHEUS backend, 3s/10s budget, unauthenticated) and the query-path delegation to the
 * selected backend - Mimir and VictoriaMetrics mount the Prometheus API under their own prefix.
 */
class MetricsSourceSettingsTest {

    @Test
    void theBuilderDefaultsMatchTheSafeConnectionProfile() {
        MetricsSourceSettings settings = MetricsSourceSettings.builder()
                .baseUrl("http://prometheus:9090")
                .build();

        assertThat(settings.getBackendType()).isEqualTo(MetricsBackendType.PROMETHEUS);
        assertThat(settings.getConnectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(settings.getReadTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(settings.getAuthType()).isEqualTo("none");
    }

    @Test
    void theQueryPathDelegatesToTheSelectedBackend() {
        assertThat(MetricsSourceSettings.builder().baseUrl("http://p:9090").build().getQueryPath())
                .isEqualTo("/api/v1/query_range");
        assertThat(MetricsSourceSettings.builder().baseUrl("http://m:9009")
                .backendType(MetricsBackendType.MIMIR).build().getQueryPath())
                .isEqualTo("/prometheus/api/v1/query_range");
        assertThat(MetricsSourceSettings.builder().baseUrl("http://v:8428")
                .backendType(MetricsBackendType.VICTORIA_METRICS).build().getQueryPath())
                .isEqualTo("/select/0/prometheus/api/v1/query_range");
    }
}
