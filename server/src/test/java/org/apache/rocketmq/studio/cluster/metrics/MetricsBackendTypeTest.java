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

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsBackendTypeTest {

    @Test
    void shouldResolveEverySupportedProviderType() {
        assertThat(MetricsBackendType.fromProviderType("PROMETHEUS")).isEqualTo(MetricsBackendType.PROMETHEUS);
        assertThat(MetricsBackendType.fromProviderType("VICTORIAMETRICS"))
                .isEqualTo(MetricsBackendType.VICTORIA_METRICS);
        assertThat(MetricsBackendType.fromProviderType("VICTORIA_METRICS"))
                .isEqualTo(MetricsBackendType.VICTORIA_METRICS);
        assertThat(MetricsBackendType.fromProviderType("victoria metrics"))
                .isEqualTo(MetricsBackendType.VICTORIA_METRICS);
        assertThat(MetricsBackendType.fromProviderType("victoria-metrics"))
                .isEqualTo(MetricsBackendType.VICTORIA_METRICS);
        assertThat(MetricsBackendType.fromProviderType("THANOS")).isEqualTo(MetricsBackendType.THANOS);
        assertThat(MetricsBackendType.fromProviderType("CORTEX")).isEqualTo(MetricsBackendType.CORTEX);
        assertThat(MetricsBackendType.fromProviderType("MIMIR")).isEqualTo(MetricsBackendType.MIMIR);
        assertThat(MetricsBackendType.fromProviderType("ARMS")).isEqualTo(MetricsBackendType.ARMS);
        assertThat(MetricsBackendType.fromProviderType("CUSTOM")).isEqualTo(MetricsBackendType.CUSTOM);
    }

    @Test
    void shouldDefaultUnknownProviderTypeToPrometheus() {
        assertThat(MetricsBackendType.fromProviderType(null)).isEqualTo(MetricsBackendType.PROMETHEUS);
        assertThat(MetricsBackendType.fromProviderType("")).isEqualTo(MetricsBackendType.PROMETHEUS);
        assertThat(MetricsBackendType.fromProviderType("unknown-backend")).isEqualTo(MetricsBackendType.PROMETHEUS);
    }

    @Test
    void shouldResolveProviderTypeIndependentlyOfDefaultLocale() {
        Locale originalLocale = Locale.getDefault();

        MetricsBackendType backendType;
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            backendType = MetricsBackendType.fromProviderType("mimir");
        } finally {
            Locale.setDefault(originalLocale);
        }

        assertThat(backendType).isEqualTo(MetricsBackendType.MIMIR);
    }

    @Test
    void shouldExposeDistinctQueryPathsForBackends() {
        assertThat(MetricsBackendType.PROMETHEUS.getQueryPath()).isEqualTo("/api/v1/query_range");
        assertThat(MetricsBackendType.VICTORIA_METRICS.getQueryPath())
                .isEqualTo("/select/0/prometheus/api/v1/query_range");
        assertThat(MetricsBackendType.MIMIR.getQueryPath()).isEqualTo("/prometheus/api/v1/query_range");
        assertThat(MetricsBackendType.THANOS.getQueryPath()).isEqualTo("/api/v1/query_range");
        assertThat(MetricsBackendType.CORTEX.getQueryPath()).isEqualTo("/api/v1/query_range");
        assertThat(MetricsBackendType.ARMS.getQueryPath()).isEqualTo("/api/v1/query_range");
    }

    @Test
    void shouldExposeCanonicalProviderType() {
        assertThat(MetricsBackendType.PROMETHEUS.getProviderType()).isEqualTo("Prometheus");
        assertThat(MetricsBackendType.VICTORIA_METRICS.getProviderType()).isEqualTo("VictoriaMetrics");
        assertThat(MetricsBackendType.ARMS.getProviderType()).isEqualTo("ARMS");
    }

    @Test
    void shouldExposeDistinctInstantQueryPathsForBackends() {
        assertThat(MetricsBackendType.PROMETHEUS.getInstantQueryPath()).isEqualTo("/api/v1/query");
        assertThat(MetricsBackendType.VICTORIA_METRICS.getInstantQueryPath())
                .isEqualTo("/select/0/prometheus/api/v1/query");
        assertThat(MetricsBackendType.MIMIR.getInstantQueryPath()).isEqualTo("/prometheus/api/v1/query");
        assertThat(MetricsBackendType.THANOS.getInstantQueryPath()).isEqualTo("/api/v1/query");
        assertThat(MetricsBackendType.CORTEX.getInstantQueryPath()).isEqualTo("/api/v1/query");
        assertThat(MetricsBackendType.ARMS.getInstantQueryPath()).isEqualTo("/api/v1/query");
    }
}
