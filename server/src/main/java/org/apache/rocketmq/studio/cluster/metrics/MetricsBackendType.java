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

/**
 * Prometheus-compatible metrics backend types supported by RocketMQ Studio.
 * <p>
 * All backends speak the Prometheus HTTP query API, but differ in the URL path
 * they expose it under (e.g. Mimir and VictoriaMetrics mount the API behind a
 * tenant/prefix path). The query/parse semantics are otherwise identical, which
 * is why every type shares {@link AbstractPrometheusCompatibleMetricsSource}.
 * </p>
 */
public enum MetricsBackendType {

    PROMETHEUS("/api/v1/query_range"),
    VICTORIA_METRICS("/select/0/prometheus/api/v1/query_range"),
    THANOS("/api/v1/query_range"),
    CORTEX("/api/v1/query_range"),
    MIMIR("/prometheus/api/v1/query_range"),
    ARMS("/api/v1/query_range"),
    CUSTOM("/api/v1/query_range");

    private final String queryPath;

    MetricsBackendType(String queryPath) {
        this.queryPath = queryPath;
    }

    public String getQueryPath() {
        return queryPath;
    }

    /**
     * Resolves a provider type name (as stored in {@code MetricsDataSourceConfig.providerType})
     * to a backend type, defaulting to {@link #PROMETHEUS} for unknown values.
     */
    public static MetricsBackendType fromProviderType(String providerType) {
        if (providerType == null) {
            return PROMETHEUS;
        }
        return switch (providerType.trim().toUpperCase()) {
            case "PROMETHEUS" -> PROMETHEUS;
            case "VICTORIAMETRICS", "VICTORIA_METRICS", "VICTORIA" -> VICTORIA_METRICS;
            case "THANOS" -> THANOS;
            case "CORTEX" -> CORTEX;
            case "MIMIR" -> MIMIR;
            case "ARMS" -> ARMS;
            case "CUSTOM" -> CUSTOM;
            default -> PROMETHEUS;
        };
    }
}
