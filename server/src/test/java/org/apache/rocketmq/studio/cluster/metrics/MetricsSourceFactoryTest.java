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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.common.util.NoRedirectClientHttpRequestFactory;
import org.apache.rocketmq.studio.model.MetricsDataSourceConfig;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MetricsSourceFactory}: each stored provider type resolves to the
 * concrete Prometheus-compatible backend that shares query/parse semantics but differs in
 * the URL path it mounts the API under, and every source is wired through the shared
 * redirect-guarded {@link RestClient} configuration.
 */
class MetricsSourceFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static MetricsDataSourceConfig config(String providerType) {
        MetricsDataSourceConfig config = new MetricsDataSourceConfig();
        config.setProviderType(providerType);
        config.setUrl("http://metrics:9090");
        return config;
    }

    private static RestClient.Builder mockBuilder() {
        RestClient.Builder builder = mock(RestClient.Builder.class);
        when(builder.requestFactory(any())).thenReturn(builder);
        return builder;
    }

    @Test
    void mapsEachProviderTypeToItsConcreteBackendSource() {
        MetricsSourceFactory factory = new MetricsSourceFactory(mockBuilder(), objectMapper);

        assertSource(factory, "Prometheus", PrometheusMetricsSource.class, MetricsBackendType.PROMETHEUS);
        assertSource(factory, "VictoriaMetrics", VictoriaMetricsMetricsSource.class,
                MetricsBackendType.VICTORIA_METRICS);
        assertSource(factory, "Thanos", ThanosMetricsSource.class, MetricsBackendType.THANOS);
        assertSource(factory, "Cortex", CortexMetricsSource.class, MetricsBackendType.CORTEX);
        assertSource(factory, "Mimir", MimirMetricsSource.class, MetricsBackendType.MIMIR);
        assertSource(factory, "ARMS", ArmsMetricsSource.class, MetricsBackendType.ARMS);
        // CUSTOM shares the Prometheus implementation but keeps its own backend identity.
        assertSource(factory, "Custom", PrometheusMetricsSource.class, MetricsBackendType.CUSTOM);
    }

    @Test
    void defaultsUnknownAndMissingProviderTypesToPrometheus() {
        MetricsSourceFactory factory = new MetricsSourceFactory(mockBuilder(), objectMapper);

        assertSource(factory, null, PrometheusMetricsSource.class, MetricsBackendType.PROMETHEUS);
        assertSource(factory, "some future backend", PrometheusMetricsSource.class,
                MetricsBackendType.PROMETHEUS);
        // Provider name normalization also tolerates whitespace/underscores.
        assertSource(factory, "victoria_metrics", VictoriaMetricsMetricsSource.class,
                MetricsBackendType.VICTORIA_METRICS);
    }

    @Test
    void configuresARedirectGuardedRestClientPerSource() {
        RestClient.Builder builder = mock(RestClient.Builder.class);
        when(builder.requestFactory(any())).thenReturn(builder);
        MetricsSourceFactory factory = new MetricsSourceFactory(builder, objectMapper);

        factory.create(config("Mimir"));

        verify(builder).requestFactory(any(NoRedirectClientHttpRequestFactory.class));
        verify(builder).build();
    }

    private static void assertSource(MetricsSourceFactory factory, String providerType,
            Class<?> expectedType, MetricsBackendType expectedBackend) {
        MetricsSource source = factory.create(config(providerType));
        assertThat(source).isInstanceOf(expectedType);
        assertThat(((AbstractPrometheusCompatibleMetricsSource) source).backendType())
                .isEqualTo(expectedBackend);
    }
}
