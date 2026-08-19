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

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;

/**
 * Resolved connection settings for a Prometheus-compatible metrics backend.
 * <p>
 * Unlike {@code MetricsDataSourceConfig} (which is the serializable user-facing
 * model), this object carries only the values required to issue a query and is
 * produced by {@link MetricsSourceFactory} from a data source configuration.
 * </p>
 */
@Getter
@Builder
public class MetricsSourceSettings {

    @Builder.Default
    private final MetricsBackendType backendType = MetricsBackendType.PROMETHEUS;
    private final String baseUrl;
    @Builder.Default
    private final Duration connectTimeout = Duration.ofSeconds(3);
    @Builder.Default
    private final Duration readTimeout = Duration.ofSeconds(10);
    @Builder.Default
    private final String authType = "none";
    private final String username;
    private final String password;
    private final String bearerToken;

    public String getQueryPath() {
        return backendType.getQueryPath();
    }
}
