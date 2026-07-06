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

import org.apache.rocketmq.dashboard.model.request.MetricsDataSourceRequest;

import java.util.List;
import java.util.Map;

/**
 * Metrics collection and export service interface
 * Provides Prometheus-compatible metrics for RocketMQ monitoring
 * and PromQL proxy query capabilities with data source management.
 */
public interface MetricsService {

    // ==================== Core Metrics Collection ====================

    /**
     * Get cluster-level metrics
     */
    Map<String, Object> getClusterMetrics();

    /**
     * Get broker-specific metrics
     */
    Map<String, Object> getBrokerMetrics(String brokerName);

    /**
     * Get topic-specific metrics
     */
    Map<String, Object> getTopicMetrics(String topic);

    /**
     * Get consumer group metrics
     */
    Map<String, Object> getConsumerGroupMetrics(String groupName);

    /**
     * Get metrics from all brokers
     */
    List<Map<String, Object>> getAllBrokersMetrics();

    /**
     * Get metrics from all topics
     */
    List<Map<String, Object>> getAllTopicsMetrics();

    /**
     * Get client connection metrics
     */
    Map<String, Object> getClientMetrics();

    /**
     * Get system-level metrics (CPU, memory, disk)
     */
    Map<String, Object> getSystemMetrics();

    /**
     * Get custom metrics by type
     */
    Map<String, Object> getCustomMetrics(String metricType);

    /**
     * Configure metrics export settings
     */
    boolean configureMetricsExport(String config);

    /**
     * Get metrics availability summary
     */
    Map<String, Object> getMetricsSummary();

    // ==================== PromQL Proxy Query ====================

    /**
     * Execute a PromQL instant query against the configured data source.
     *
     * @param params query parameters including "query", "time" (optional), "datasourceId" (optional)
     * @return query results from Prometheus
     * @throws UnsupportedOperationException if no data source is configured
     */
    Map<String, Object> executePromqlQuery(Map<String, Object> params);

    /**
     * Execute a PromQL range query against the configured data source.
     *
     * @param params query parameters including "query", "start", "end", "step", "datasourceId" (optional)
     * @return range query results from Prometheus
     * @throws UnsupportedOperationException if no data source is configured
     */
    Map<String, Object> executePromqlRangeQuery(Map<String, Object> params);

    // ==================== Data Source Management ====================

    /**
     * List all configured data sources.
     *
     * @return list of data source configurations
     */
    List<Map<String, Object>> listDataSources();

    /**
     * Create a new data source configuration.
     *
     * @param request data source configuration
     * @return created data source with generated ID
     * @throws IllegalArgumentException if required fields are missing
     */
    Map<String, Object> createDataSource(MetricsDataSourceRequest request);

    /**
     * Update an existing data source configuration.
     *
     * @param id      data source ID
     * @param request updated configuration
     * @return updated data source
     * @throws IllegalArgumentException if data source not found
     */
    Map<String, Object> updateDataSource(String id, MetricsDataSourceRequest request);

    /**
     * Delete a data source configuration.
     *
     * @param id data source ID
     * @return true if deleted, false if not found
     */
    boolean deleteDataSource(String id);

    /**
     * Test connectivity to a data source.
     *
     * @param id data source ID
     * @return test result with "success", "latencyMs", and "message" fields
     */
    Map<String, Object> testDataSource(String id);
}