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
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

/**
 * Builds a {@link MetricsSource} for a given {@link org.apache.rocketmq.studio.model.MetricsDataSourceConfig}.
 * <p>
 * The concrete implementation is selected by {@code MetricsDataSourceConfig.providerType},
 * which maps to a {@link MetricsBackendType}. Every backend shares the same
 * query/parse logic via {@link AbstractPrometheusCompatibleMetricsSource}.
 * </p>
 */
@RequiredArgsConstructor
@Component
public class MetricsSourceFactory {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    public MetricsSource create(org.apache.rocketmq.studio.model.MetricsDataSourceConfig config) {
        MetricsBackendType backendType = MetricsBackendType.fromProviderType(config.getProviderType());
        MetricsSourceSettings settings = MetricsSourceSettings.builder()
                .backendType(backendType)
                .baseUrl(config.getUrl())
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(10))
                .authType(config.getAuthType())
                .username(config.getUsername())
                .password(config.getPassword())
                .bearerToken(config.getBearerToken())
                .build();
        return switch (backendType) {
            case VICTORIA_METRICS -> new VictoriaMetricsMetricsSource(restClientBuilder, objectMapper, settings);
            case THANOS -> new ThanosMetricsSource(restClientBuilder, objectMapper, settings);
            case CORTEX -> new CortexMetricsSource(restClientBuilder, objectMapper, settings);
            case MIMIR -> new MimirMetricsSource(restClientBuilder, objectMapper, settings);
            case ARMS -> new ArmsMetricsSource(restClientBuilder, objectMapper, settings);
            case CUSTOM, PROMETHEUS -> new PrometheusMetricsSource(restClientBuilder, objectMapper, settings);
        };
    }
}
