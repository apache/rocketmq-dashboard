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
package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.dashboard.model.CheckItem;
import org.apache.rocketmq.dashboard.model.MetricsHealthResult;
import org.apache.rocketmq.dashboard.model.MetricsSelfCheckResult;
import org.apache.rocketmq.dashboard.service.MetricsProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class MetricsEnhancedServiceImplTest {

    @InjectMocks
    private MetricsEnhancedServiceImpl service;

    @Mock
    private MetricsProvider metricsProvider;

    private MetricsHealthResult health(boolean connected, String message) {
        MetricsHealthResult result = new MetricsHealthResult();
        result.setConnected(connected);
        result.setStatusMessage(message);
        result.setMissingMetricFamilies(Collections.emptyList());
        result.setAvailableMetricFamilies(Collections.emptyList());
        return result;
    }

    // ==================== health check ====================

    @Test
    public void testCheckDataSourceHealthDelegates() {
        when(metricsProvider.healthCheck()).thenReturn(health(true, "OK"));

        MetricsHealthResult result = service.checkDataSourceHealth();
        assertTrue(result.isConnected());
        assertEquals("OK", result.getStatusMessage());
    }

    @Test
    public void testCheckDataSourceHealthFallbackOnException() {
        when(metricsProvider.healthCheck()).thenThrow(new RuntimeException("boom"));

        MetricsHealthResult result = service.checkDataSourceHealth();
        assertFalse(result.isConnected());
        assertTrue(result.getStatusMessage().contains("Health check unavailable"));
        assertTrue(result.getStatusMessage().contains("boom"));
        assertNotNull(result.getMissingMetricFamilies());
        assertNotNull(result.getAvailableMetricFamilies());
    }

    // ==================== dashboard panels ====================

    @Test
    public void testListDashboardsReturnsAllPanels() {
        List<Map<String, Object>> dashboards = service.listDashboards();
        assertEquals(21, dashboards.size());
        Map<String, Object> first = dashboards.get(0);
        assertEquals("cluster-overview", first.get("id"));
        assertEquals("Cluster Overview", first.get("title"));
        assertEquals("Overview", first.get("category"));
    }

    @Test
    public void testGetDashboardPanelSuccess() {
        Map<String, Object> panel = service.getDashboardPanel("consumer-lag");
        assertEquals("consumer-lag", panel.get("id"));
        assertEquals("Consumer Group Lag", panel.get("title"));
        assertEquals("Consumer", panel.get("category"));
        assertNotNull(panel.get("promql"));
        assertNotNull(panel.get("graphPanel"));
        assertEquals(60000, panel.get("threshold"));
        assertNotNull(panel.get("alertCondition"));
    }

    @Test
    public void testGetDashboardPanelWithoutThreshold() {
        Map<String, Object> panel = service.getDashboardPanel("client-online");
        assertEquals("client-online", panel.get("id"));
        // threshold/alertCondition default to null entries
        assertTrue(panel.containsKey("threshold"));
        assertEquals(null, panel.get("threshold"));
    }

    @Test(expected = NoSuchElementException.class)
    public void testGetDashboardPanelNotFound() {
        service.getDashboardPanel("no-such-panel");
    }

    // ==================== grafana export ====================

    @Test
    public void testExportGrafanaJsonSinglePanel() {
        String json = service.exportGrafanaJson("broker-stats");
        assertNotNull(json);
        assertTrue(json.contains("\"title\": \"Broker Statistics (Sent / Born TPS)\""));
        assertTrue(json.contains("rocketmq_broker_sendTPS"));
        assertTrue(json.contains("\"schemaVersion\": 39"));
        assertTrue(json.contains("\"refId\": \"A\""));
    }

    @Test
    public void testExportGrafanaJsonPanelWithThreshold() {
        String json = service.exportGrafanaJson("dlq-count");
        assertTrue(json.contains("\"thresholdsStep\": 100"));
    }

    @Test(expected = NoSuchElementException.class)
    public void testExportGrafanaJsonUnknownPanel() {
        service.exportGrafanaJson("unknown-panel-id");
    }

    @Test
    public void testExportGrafanaJsonListAll() {
        Map<String, Object> result = service.exportGrafanaJson((List<String>) null);
        assertEquals(21, result.size());
        assertTrue(((String) result.get("cluster-overview")).contains("Cluster Overview"));
    }

    @Test
    public void testExportGrafanaJsonListEmptyExportsAll() {
        Map<String, Object> result = service.exportGrafanaJson(Collections.emptyList());
        assertEquals(21, result.size());
    }

    @Test
    public void testExportGrafanaJsonListWithInvalidId() {
        Map<String, Object> result = service.exportGrafanaJson(
            Arrays.asList("topic-throughput", "bogus-id"));
        assertEquals(2, result.size());
        assertTrue(((String) result.get("topic-throughput")).contains("Topic Throughput"));
        assertTrue(((String) result.get("bogus-id")).contains("error"));
    }

    // ==================== prebuilt queries ====================

    @Test
    public void testGetPrebuiltQueriesGroupedByCategory() {
        Map<String, Object> queries = service.getPrebuiltQueries();
        assertFalse(queries.isEmpty());
        assertTrue(queries.containsKey("Broker"));
        assertTrue(queries.containsKey("Consumer"));
        assertTrue(queries.containsKey("Topic"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> brokerQueries = (List<Map<String, Object>>) queries.get("Broker");
        assertFalse(brokerQueries.isEmpty());
        for (Map<String, Object> q : brokerQueries) {
            assertNotNull(q.get("id"));
            assertNotNull(q.get("title"));
            assertNotNull(q.get("promql"));
        }
    }

    // ==================== alert rules ====================

    @Test
    public void testGetAlertRulesYaml() {
        String yaml = service.getAlertRulesYaml();
        assertNotNull(yaml);
        assertTrue(yaml.contains("groups:"));
        assertEquals(20, MetricsEnhancedServiceImpl.countAlertRules(yaml));
    }

    @Test
    public void testCountAlertRules() {
        assertEquals(0, MetricsEnhancedServiceImpl.countAlertRules(null));
        assertEquals(0, MetricsEnhancedServiceImpl.countAlertRules(""));
        assertEquals(0, MetricsEnhancedServiceImpl.countAlertRules("groups:\n  rules: []"));
        assertEquals(2, MetricsEnhancedServiceImpl.countAlertRules(
            "- alert: A\n- alert: B\n"));
    }

    @Test
    public void testIsPanelPromqlValid() {
        assertFalse(MetricsEnhancedServiceImpl.isPanelPromqlValid(null));
        assertFalse(MetricsEnhancedServiceImpl.isPanelPromqlValid("   "));
        assertFalse(MetricsEnhancedServiceImpl.isPanelPromqlValid("some_random_metric"));
        assertTrue(MetricsEnhancedServiceImpl.isPanelPromqlValid("rate(rocketmq_broker_sendTPS[5m])"));
        assertTrue(MetricsEnhancedServiceImpl.isPanelPromqlValid("java_lang_memory_HeapMemoryUsage_used"));
        assertTrue(MetricsEnhancedServiceImpl.isPanelPromqlValid("up"));
    }

    // ==================== self check ====================

    @Test
    public void testSelfCheckHealthyWhenConnected() {
        when(metricsProvider.healthCheck()).thenReturn(health(true, "connected"));

        MetricsSelfCheckResult result = service.selfCheck();
        assertTrue(result.isHealthy());
        assertTrue(result.getTimestamp() > 0);
        assertEquals(result.getTotalChecks(), result.getChecks().size());
        assertEquals(result.getTotalChecks(),
            result.getPassedChecks() + result.getFailedChecks());

        CheckItem connectivity = findCheck(result, "data-source-connectivity");
        assertTrue(connectivity.isPassed());
        assertEquals("INFO", connectivity.getSeverity());
        assertTrue(result.getSummary().contains("healthy"));
    }

    @Test
    public void testSelfCheckDegradedWhenNotConfigured() {
        when(metricsProvider.healthCheck())
            .thenReturn(health(false, "data source not configured"));

        MetricsSelfCheckResult result = service.selfCheck();
        assertTrue(result.isHealthy());

        CheckItem connectivity = findCheck(result, "data-source-connectivity");
        assertTrue(connectivity.isPassed());
        assertEquals("WARN", connectivity.getSeverity());
    }

    @Test
    public void testSelfCheckErrorWhenUnreachable() {
        when(metricsProvider.healthCheck())
            .thenReturn(health(false, "connection refused"));

        MetricsSelfCheckResult result = service.selfCheck();
        assertFalse(result.isHealthy());

        CheckItem connectivity = findCheck(result, "data-source-connectivity");
        assertFalse(connectivity.isPassed());
        assertEquals("ERROR", connectivity.getSeverity());
        assertTrue(result.getSummary().contains("failing"));
    }

    @Test
    public void testSelfCheckNullStatusMessageIsError() {
        when(metricsProvider.healthCheck()).thenReturn(health(false, null));

        MetricsSelfCheckResult result = service.selfCheck();
        CheckItem connectivity = findCheck(result, "data-source-connectivity");
        assertFalse(connectivity.isPassed());
        assertEquals("ERROR", connectivity.getSeverity());
    }

    @Test
    public void testSelfCheckPanelsAndAlertRulesPass() {
        when(metricsProvider.healthCheck()).thenReturn(health(true, "ok"));

        MetricsSelfCheckResult result = service.selfCheck();
        CheckItem panels = findCheck(result, "dashboard-panels");
        assertTrue(panels.isPassed());
        assertTrue(panels.getMessage().contains("21/21"));

        CheckItem alerts = findCheck(result, "alert-rules");
        assertTrue(alerts.isPassed());
        assertTrue(alerts.getMessage().contains("20 alert rules"));

        CheckItem queries = findCheck(result, "prebuilt-queries");
        assertTrue(queries.isPassed());
    }

    private CheckItem findCheck(MetricsSelfCheckResult result, String name) {
        return result.getChecks().stream()
            .filter(c -> name.equals(c.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("check not found: " + name));
    }
}
