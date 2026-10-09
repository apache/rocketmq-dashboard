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
import org.apache.rocketmq.studio.model.MetricsDataSourceConfig;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link MetricsSourceFactory}: the selection of a metrics backend from a data source
 * configuration. Every backend shares the query/parse logic, so the whole contract is that the
 * provider type selects the right implementation carrying the configured connection values.
 */
class MetricsSourceFactoryTest {

    private final MetricsSourceFactory factory = new MetricsSourceFactory(
            RestClient.builder(), new ObjectMapper());

    private static MetricsDataSourceConfig config(String providerType) {
        MetricsDataSourceConfig config = new MetricsDataSourceConfig();
        config.setName("ds");
        config.setUrl("http://metrics:9090");
        config.setProviderType(providerType);
        return config;
    }

    @Test
    void everyProviderTypeSelectsItsOwnImplementation() {
        assertThat(factory.create(config("PROMETHEUS")))
                .isInstanceOf(PrometheusMetricsSource.class);
        assertThat(factory.create(config("VICTORIAMETRICS")))
                .isInstanceOf(VictoriaMetricsMetricsSource.class);
        assertThat(factory.create(config("THANOS")))
                .isInstanceOf(ThanosMetricsSource.class);
        assertThat(factory.create(config("CORTEX")))
                .isInstanceOf(CortexMetricsSource.class);
        assertThat(factory.create(config("MIMIR")))
                .isInstanceOf(MimirMetricsSource.class);
        assertThat(factory.create(config("ARMS")))
                .isInstanceOf(ArmsMetricsSource.class);
    }

    @Test
    void anUnknownProviderTypeFallsBackToPrometheus() {
        assertThat(factory.create(config("SOMETHING_ELSE")))
                .isInstanceOf(PrometheusMetricsSource.class);
    }

    @Test
    void theCreatedSourceCarriesTheConfiguredConnection() throws Exception {
        MetricsDataSourceConfig config = config("VICTORIAMETRICS");
        config.setAuthType("bearer");
        config.setBearerToken("token");

        MetricsSource source = factory.create(config);

        assertThat(source).isInstanceOf(VictoriaMetricsMetricsSource.class);
        // the settings field is protected state; read it through reflection
        // because no public accessor exists on the abstract base
        java.lang.reflect.Field field =
                AbstractPrometheusCompatibleMetricsSource.class.getDeclaredField("settings");
        field.setAccessible(true);
        MetricsSourceSettings settings =
                (MetricsSourceSettings) field.get(source);

        assertThat(settings.getBaseUrl()).isEqualTo("http://metrics:9090");
        assertThat(settings.getBackendType()).isEqualTo(MetricsBackendType.VICTORIA_METRICS);
        assertThat(settings.getAuthType()).isEqualTo("bearer");
        assertThat(settings.getBearerToken()).isEqualTo("token");
        // the operational budget the factory hardcodes
        assertThat(settings.getConnectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(settings.getReadTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(settings.getQueryPath())
                .isEqualTo(MetricsBackendType.VICTORIA_METRICS.getQueryPath());
    }

    @Test
    void theQueryPathMatchesTheSelectedBackend() throws Exception {
        MetricsSource thanos = factory.create(config("THANOS"));

        java.lang.reflect.Field field =
                AbstractPrometheusCompatibleMetricsSource.class.getDeclaredField("settings");
        field.setAccessible(true);
        MetricsSourceSettings settings = (MetricsSourceSettings) field.get(thanos);

        assertThat(settings.getQueryPath())
                .isEqualTo(MetricsBackendType.THANOS.getQueryPath());
    }
}
