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
package org.apache.rocketmq.dashboard.service.metrics;

import java.util.Map;

/**
 * Metrics aggregation service that combines data from Prometheus and the native
 * {@link org.apache.rocketmq.dashboard.architecture.MetadataProvider} into a
 * unified view for the RocketMQ Dashboard.
 *
 * <p>This service provides:</p>
 * <ul>
 *   <li>Unified dashboard metrics aggregating both Prometheus time-series data
 *       and native MetadataProvider operational data</li>
 *   <li>Broker-specific aggregated metrics</li>
 *   <li>Topic-specific aggregated metrics</li>
 *   <li>Consumer group aggregated metrics</li>
 *   <li>System-level metrics</li>
 *   <li>Health status across all data sources</li>
 * </ul>
 */
public interface MetricsAggregationService {

    /**
     * Get aggregated dashboard metrics combining Prometheus and native sources.
     *
     * @return unified dashboard metrics map containing cluster overview,
     *         broker status, topic summary, consumer summary, and system health
     */
    Map<String, Object> getDashboardMetrics();

    /**
     * Get aggregated metrics for a specific broker.
     *
     * @param brokerName the broker name
     * @return aggregated broker metrics including runtime stats, disk usage,
     *         JVM stats, and throughput data
     */
    Map<String, Object> getAggregatedBrokerMetrics(String brokerName);

    /**
     * Get aggregated metrics for a specific topic.
     *
     * @param topicName the topic name
     * @return aggregated topic metrics including throughput, accumulation,
     *         and per-consumer-group consumption rates
     */
    Map<String, Object> getAggregatedTopicMetrics(String topicName);

    /**
     * Get aggregated metrics for a specific consumer group.
     *
     * @param groupName the consumer group name
     * @return aggregated consumer group metrics including offset, lag,
     *         consumption rate, and connection status
     */
    Map<String, Object> getAggregatedConsumerGroupMetrics(String groupName);

    /**
     * Get aggregated system-level metrics.
     *
     * @return system metrics including JVM stats, process info, and
     *         infrastructure health across all components
     */
    Map<String, Object> getAggregatedSystemMetrics();

    /**
     * Get the health status of all metrics data sources.
     *
     * @return map of source name to health status (connected, latency, errors)
     */
    Map<String, Object> getDataSourcesHealth();

    /**
     * Execute a raw PromQL query and return parsed results.
     * Convenience method for proxying PromQL queries through the dashboard.
     *
     * @param promQL       the PromQL expression
     * @param datasourceId optional datasource ID (null for default)
     * @return parsed query results
     */
    Map<String, Object> executePromQL(String promQL, String datasourceId);

    /**
     * Execute a PromQL range query and return parsed results.
     *
     * @param promQL       the PromQL expression
     * @param start        start timestamp
     * @param end          end timestamp
     * @param step         query resolution step
     * @param datasourceId optional datasource ID
     * @return parsed range query results
     */
    Map<String, Object> executePromQLRange(String promQL, String start, String end, String step, String datasourceId);
}
