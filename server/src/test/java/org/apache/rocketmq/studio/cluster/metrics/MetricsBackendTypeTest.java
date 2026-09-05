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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MetricsBackendTypeTest {

    @Test
    void exposesAllBackendKinds() {
        assertEquals(7, MetricsBackendType.values().length);
    }

    @Test
    void providerTypeStringsRoundTripThroughLookup() {
        for (MetricsBackendType backend : MetricsBackendType.values()) {
            assertEquals(backend, MetricsBackendType.fromProviderType(backend.getProviderType()));
        }
    }

    @Test
    void normalizesProviderTypeCasingAndSeparators() {
        assertEquals(MetricsBackendType.VICTORIA_METRICS,
                MetricsBackendType.fromProviderType(" victoria-metrics "));
        assertEquals(MetricsBackendType.VICTORIA_METRICS,
                MetricsBackendType.fromProviderType("victoria_metrics"));
        assertEquals(MetricsBackendType.VICTORIA_METRICS,
                MetricsBackendType.fromProviderType("Victoria metrics"));
        assertEquals(MetricsBackendType.CUSTOM, MetricsBackendType.fromProviderType(" custom "));
    }

    @Test
    void acceptsVictoriaAlias() {
        assertEquals(MetricsBackendType.VICTORIA_METRICS,
                MetricsBackendType.fromProviderType("victoria"));
    }

    @Test
    void nullProviderTypeFallsBackToPrometheus() {
        assertEquals(MetricsBackendType.PROMETHEUS, MetricsBackendType.fromProviderType(null));
    }

    @Test
    void unknownProviderTypeFallsBackToPrometheus() {
        assertEquals(MetricsBackendType.PROMETHEUS, MetricsBackendType.fromProviderType("datadog"));
    }

    @Test
    void prometheusCarriesItsQueryPaths() {
        assertEquals("/api/v1/query_range", MetricsBackendType.PROMETHEUS.getQueryPath());
        assertEquals("/api/v1/query", MetricsBackendType.PROMETHEUS.getInstantQueryPath());
    }

    @Test
    void victoriaMetricsCarriesItsQueryPaths() {
        assertEquals("/select/0/prometheus/api/v1/query_range",
                MetricsBackendType.VICTORIA_METRICS.getQueryPath());
        assertEquals("/select/0/prometheus/api/v1/query",
                MetricsBackendType.VICTORIA_METRICS.getInstantQueryPath());
    }

    @Test
    void mimirCarriesItsQueryPaths() {
        assertEquals("/prometheus/api/v1/query_range", MetricsBackendType.MIMIR.getQueryPath());
    }

    @Test
    void providerTypesAreUniqueAcrossBackends() {
        long distinct = java.util.Arrays.stream(MetricsBackendType.values())
                .map(MetricsBackendType::getProviderType)
                .distinct()
                .count();

        assertEquals(MetricsBackendType.values().length, distinct);
    }

    @Test
    void queryPathsStayDistinctWhereBackendsDiffer() {
        assertNotEquals(MetricsBackendType.VICTORIA_METRICS.getQueryPath(),
                MetricsBackendType.PROMETHEUS.getQueryPath());
        assertNotEquals(MetricsBackendType.MIMIR.getQueryPath(),
                MetricsBackendType.PROMETHEUS.getQueryPath());
    }
}
