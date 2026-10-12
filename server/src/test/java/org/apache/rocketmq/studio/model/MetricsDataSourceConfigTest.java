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
 * Pins the metrics data source config of {@link MetricsDataSourceConfig}: the provider type
 * defaults to PROMETHEUS, a fresh config is disabled with TLS off, and the credentials
 * (password, bearer token) are carried as raw configuration - never persisted by the store.
 */
class MetricsDataSourceConfigTest {

    @Test
    void theProviderDefaultsToPrometheus() {
        assertThat(new MetricsDataSourceConfig().getProviderType()).isEqualTo("PROMETHEUS");
    }

    @Test
    void aFreshConfigIsDisabledWithoutTls() {
        MetricsDataSourceConfig config = new MetricsDataSourceConfig();
        assertThat(config.isEnabled()).isFalse();
        assertThat(config.isTlsEnabled()).isFalse();
        assertThat(config.getScrapeInterval()).isZero();
    }

    @Test
    void everyFieldRoundTrips() {
        MetricsDataSourceConfig config = new MetricsDataSourceConfig();
        config.setName("prod-prometheus");
        config.setUrl("http://prometheus:9090");
        config.setAuthType("basic");
        config.setUsername("alice");
        config.setPassword("secret");
        config.setBearerToken("token");
        config.setProviderType("MIMIR");
        config.setTlsEnabled(true);
        config.setDefaultLabels(java.util.Map.of("env", "prod"));
        config.setScrapeInterval(30);
        config.setEnabled(true);

        assertThat(config.getName()).isEqualTo("prod-prometheus");
        assertThat(config.getUrl()).isEqualTo("http://prometheus:9090");
        assertThat(config.getAuthType()).isEqualTo("basic");
        assertThat(config.getUsername()).isEqualTo("alice");
        assertThat(config.getPassword()).isEqualTo("secret");
        assertThat(config.getBearerToken()).isEqualTo("token");
        assertThat(config.getProviderType()).isEqualTo("MIMIR");
        assertThat(config.isTlsEnabled()).isTrue();
        assertThat(config.getDefaultLabels()).containsEntry("env", "prod");
        assertThat(config.getScrapeInterval()).isEqualTo(30);
        assertThat(config.isEnabled()).isTrue();
    }
}
