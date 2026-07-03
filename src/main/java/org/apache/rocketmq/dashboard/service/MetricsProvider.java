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
package org.apache.rocketmq.dashboard.service;

import org.apache.rocketmq.dashboard.model.MetricsHealthResult;

import java.util.List;

/**
 * SPI for metrics data source management and query proxy.
 * <p>
 * This interface does NOT store metrics locally — it proxies PromQL queries to
 * external Prometheus-compatible backends (Prometheus, VictoriaMetrics, Thanos,
 * Mimir, etc.). Credentials never leave the backend layer.
 * </p>
 */
public interface MetricsProvider {

    /**
     * Execute an instant PromQL query against the configured data source.
     *
     * @param promQL  the PromQL expression
     * @param time    target timestamp (Unix milliseconds)
     * @param step    query resolution step in seconds (ignored for instant queries,
     *                but kept for API parity)
     * @return parsed result object: {@code Map} containing status + data fields
     *         matching the Prometheus /api/v1/query JSON response shape
     */
    Object queryInstant(String promQL, long time, double step);

    /**
     * Execute a range PromQL query (time series).
     *
     * @param promQL  the PromQL expression
     * @param startTime start timestamp (Unix milliseconds)
     * @param endTime   end timestamp (Unix milliseconds)
     * @param step      query resolution step in seconds
     * @return parsed result object in matrix format per Prometheus /api/v1/query_range spec
     */
    Object queryRange(String promQL, long startTime, long endTime, double step);

    /**
     * Query all label values for a given metric name and label key.
     *
     * @param metricName the metric family name
     * @param labelKey   the label whose values to retrieve
     * @return list of unique label values
     */
    List<String> queryLabelValues(String metricName, String labelKey);

    /**
     * List all available metric-family names (by scanning label entries starting
     * with "rocketmq_" prefix from the remote endpoint).
     *
     * @return list of metric family strings, e.g. ["rocketmq_broker_sentTPS", ...]
     */
    List<String> listMetricFamilies();

    /**
     * Health check: test connectivity to the data source and validate that
     * the required RocketMQ metric families exist.
     *
     * @return health report including connection status, latency, missing and
     *         available metric families
     */
    MetricsHealthResult healthCheck();
}
