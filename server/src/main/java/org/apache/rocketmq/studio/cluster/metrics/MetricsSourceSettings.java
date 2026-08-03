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

import java.time.Duration;

/**
 * Resolved connection settings for a Prometheus-compatible metrics backend.
 * <p>
 * Unlike {@code MetricsDataSourceConfig} (which is the serializable user-facing
 * model), this object carries only the values required to issue a query and is
 * produced by {@link MetricsSourceFactory} from a data source configuration.
 * </p>
 */
public class MetricsSourceSettings {

    private final MetricsBackendType backendType;
    private final String baseUrl;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final String username;
    private final String password;
    private final String bearerToken;

    private MetricsSourceSettings(Builder builder) {
        this.backendType = builder.backendType;
        this.baseUrl = builder.baseUrl;
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;
        this.username = builder.username;
        this.password = builder.password;
        this.bearerToken = builder.bearerToken;
    }

    public MetricsBackendType getBackendType() {
        return backendType;
    }

    public String getQueryPath() {
        return backendType.getQueryPath();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getBearerToken() {
        return bearerToken;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private MetricsBackendType backendType = MetricsBackendType.PROMETHEUS;
        private String baseUrl;
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration readTimeout = Duration.ofSeconds(10);
        private String username;
        private String password;
        private String bearerToken;

        public Builder backendType(MetricsBackendType backendType) {
            this.backendType = backendType;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder bearerToken(String bearerToken) {
            this.bearerToken = bearerToken;
            return this;
        }

        public MetricsSourceSettings build() {
            return new MetricsSourceSettings(this);
        }
    }
}
