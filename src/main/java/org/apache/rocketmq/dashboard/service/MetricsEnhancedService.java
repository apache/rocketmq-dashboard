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
import java.util.Map;

/**
 * Enhanced metrics service providing dashboard templates, pre-built queries,
 * alert rules, and Grafana JSON export — built on top of {@link MetricsProvider}.
 * <p>
 * This service does NOT introduce new data sources; it composes PromQL from
 * predefined templates that assume RocketMQ 5.x OpenTelemetry PROM exporter is active.
 * </p>
 */
public interface MetricsEnhancedService {

    /**
     * Run a full health check against the configured Prometheus-compatible data source.
     *
     * @return health report with connection status, latency, missing / available metric families
     */
    MetricsHealthResult checkDataSourceHealth();

    /**
     * Retrieve a single dashboard panel definition by its ID.
     *
     * @param panelId unique identifier such as "cluster-overview"
     * @return map containing title, description, promql, graphPanel settings,
     *         legendFormat, threshold, alertCondition
     */
    Map<String, Object> getDashboardPanel(String panelId);

    /**
     * List all available dashboard panels.
     *
     * @return list of panel summary maps (each with id, title, category)
     */
    List<Map<String, Object>> listDashboards();

    /**
     * Export a single panel as a self-contained Grafana Dashboard JSON v8 string.
     *
     * @param panelId panel identifier
     * @return valid Grafana Dashboard JSON (v8 schema) containing only the requested panel
     */
    String exportGrafanaJson(String panelId);

    /**
     * Export multiple panels (or all if null/empty) as Grafana Dashboard JSON.
     * RIP-1 METRICS-01: One-click Grafana dashboard export for external visualization.
     *
     * @param panelIds list of panel IDs to export; if null or empty, exports all panels
     * @return map of panelId -> Grafana Dashboard JSON (v8 schema)
     */
    Map<String, Object> exportGrafanaJson(List<String> panelIds);

    /**
     * Return all pre-built PromQL queries grouped by category.
     *
     * @return map of category name -> list of query definitions
     */
    Map<String, Object> getPrebuiltQueries();

    /**
     * Return recommended alert rules in YAML format.
     * Rules are defined per RIP-1 requirements for RocketMQ monitoring.
     *
     * @return YAML string containing Group/RuleSet definition compatible with
     *         Alertmanager / VictoriaMetrics alerting
     */
    String getAlertRulesYaml();
}
