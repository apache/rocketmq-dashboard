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

package org.apache.rocketmq.dashboard.controller;

import com.alibaba.fastjson.JSON;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.rocketmq.dashboard.adapter.PrometheusMetricsAdapter;
import org.apache.rocketmq.dashboard.model.MetricsSelfCheckResult;
import org.apache.rocketmq.dashboard.model.request.MetricsDataSourceRequest;
import org.apache.rocketmq.dashboard.service.MetricsEnhancedService;
import org.apache.rocketmq.dashboard.service.MetricsService;
import org.apache.rocketmq.dashboard.support.JsonResult;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class MetricsControllerTest extends BaseControllerTest {

    @InjectMocks
    private MetricsController metricsController;

    @Mock
    private MetricsService metricsService;

    @Mock
    private MetricsEnhancedService metricsEnhancedService;

    @Mock
    private PrometheusMetricsAdapter prometheusAdapter;

    @Before
    public void init() {
        super.mockRmqConfigure();
    }

    @Override
    protected Object getTestController() {
        return metricsController;
    }

    /**
     * MetricsController catches exceptions internally and returns JsonResult with
     * status 1 (business error), instead of propagating to GlobalExceptionHandler
     * (which would produce status -1).
     */
    private org.springframework.test.web.servlet.ResultActions performBusinessErrorExpect(
            org.springframework.test.web.servlet.ResultActions perform) throws Exception {
        return perform.andExpect(status().isOk())
                .andExpect(jsonPath("$").exists())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.errMsg").isNotEmpty());
    }

    // ==================== listDashboards ====================

    @Test
    public void testListDashboards() throws Exception {
        List<Map<String, Object>> dashboards = new ArrayList<>();

        Map<String, Object> panel1 = new LinkedHashMap<>();
        panel1.put("id", "cluster-overview");
        panel1.put("title", "Cluster Overview");
        panel1.put("category", "Overview");
        dashboards.add(panel1);

        Map<String, Object> panel2 = new LinkedHashMap<>();
        panel2.put("id", "broker-stats");
        panel2.put("title", "Broker Statistics");
        panel2.put("category", "Broker");
        dashboards.add(panel2);

        when(metricsEnhancedService.listDashboards()).thenReturn(dashboards);

        final String url = "/api/metrics/dashboards";
        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);

        performOkExpect(perform)
                .andExpect(jsonPath("$.data[0].id").value("cluster-overview"))
                .andExpect(jsonPath("$.data[0].title").value("Cluster Overview"))
                .andExpect(jsonPath("$.data[1].id").value("broker-stats"))
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    public void testListDashboardsEmpty() throws Exception {
        when(metricsEnhancedService.listDashboards()).thenReturn(Collections.emptyList());

        final String url = "/api/metrics/dashboards";
        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);

        performOkExpect(perform)
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    public void testListDashboardsError() throws Exception {
        when(metricsEnhancedService.listDashboards())
                .thenThrow(new RuntimeException("Service unavailable"));

        final String url = "/api/metrics/dashboards";
        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);

        performBusinessErrorExpect(perform);
    }

    // ==================== getDashboardPanel ====================

    @Test
    public void testGetDashboardPanelWithValidId() throws Exception {
        Map<String, Object> panel = new LinkedHashMap<>();
        panel.put("id", "cluster-overview");
        panel.put("title", "Cluster Overview");
        panel.put("description", "Overall RocketMQ cluster health");
        panel.put("category", "Overview");
        panel.put("promql", "sum(rocketmq_broker_bornTotal)");

        when(metricsEnhancedService.getDashboardPanel("cluster-overview")).thenReturn(panel);

        final String url = "/api/metrics/dashboards/cluster-overview";
        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);

        performOkExpect(perform)
                .andExpect(jsonPath("$.data.id").value("cluster-overview"))
                .andExpect(jsonPath("$.data.title").value("Cluster Overview"))
                .andExpect(jsonPath("$.data.promql").value("sum(rocketmq_broker_bornTotal)"));
    }

    @Test
    public void testGetDashboardPanelWithEmptyId() throws Exception {
        final String url = "/api/metrics/dashboards/ ";
        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);

        performBusinessErrorExpect(perform);
    }

    @Test
    public void testGetDashboardPanelWithNonExistentId() throws Exception {
        when(metricsEnhancedService.getDashboardPanel("non-existent-panel"))
                .thenReturn(Collections.emptyMap());

        final String url = "/api/metrics/dashboards/non-existent-panel";
        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);

        performBusinessErrorExpect(perform);
    }

    @Test
    public void testGetDashboardPanelError() throws Exception {
        when(metricsEnhancedService.getDashboardPanel("cluster-overview"))
                .thenThrow(new RuntimeException("Panel service error"));

        final String url = "/api/metrics/dashboards/cluster-overview";
        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);

        performBusinessErrorExpect(perform);
    }

    // ==================== getAlertRules (default yaml format) ====================

    @Test
    public void testGetAlertRulesDefaultFormat() throws Exception {
        String yamlContent = "groups:\n  - name: rocketmq-broker\n    rules:\n      - alert: TestAlert";
        when(metricsEnhancedService.getAlertRulesYaml()).thenReturn(yamlContent);

        final String url = "/api/metrics/alerts";
        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);

        performOkExpect(perform)
                .andExpect(jsonPath("$.data.format").value("yaml"))
                .andExpect(jsonPath("$.data.rules").value(yamlContent));
    }

    @Test
    public void testGetAlertRulesDefaultFormatNoParam() throws Exception {
        String yamlContent = "groups:\n  - name: rocketmq-broker\n    rules:\n      - alert: BrokerDown";
        when(metricsEnhancedService.getAlertRulesYaml()).thenReturn(yamlContent);

        final String url = "/api/metrics/alerts";
        requestBuilder = MockMvcRequestBuilders.get(url);
        // No format parameter means default "yaml"
        perform = mockMvc.perform(requestBuilder);

        performOkExpect(perform)
                .andExpect(jsonPath("$.data.format").value("yaml"))
                .andExpect(jsonPath("$.data.rules").isString());
    }

    // ==================== getAlertRules (json format) ====================

    @Test
    public void testGetAlertRulesJsonFormat() throws Exception {
        String yamlContent = "groups:\n  - name: rocketmq-broker\n    rules:\n      - alert: TestAlert";
        when(metricsEnhancedService.getAlertRulesYaml()).thenReturn(yamlContent);

        final String url = "/api/metrics/alerts";
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("format", "json");
        perform = mockMvc.perform(requestBuilder);

        performOkExpect(perform)
                .andExpect(jsonPath("$.data.format").value("yaml"))
                .andExpect(jsonPath("$.data.rules").value(yamlContent))
                .andExpect(jsonPath("$.data.message").isString());
    }

    @Test
    public void testGetAlertRulesJsonFormatCaseInsensitive() throws Exception {
        String yamlContent = "groups:\n  - name: rocketmq-broker\n    rules:\n      - alert: TestAlert";
        when(metricsEnhancedService.getAlertRulesYaml()).thenReturn(yamlContent);

        final String url = "/api/metrics/alerts";
        requestBuilder = MockMvcRequestBuilders.get(url);
        requestBuilder.param("format", "JSON");
        perform = mockMvc.perform(requestBuilder);

        performOkExpect(perform)
                .andExpect(jsonPath("$.data.format").value("yaml"))
                .andExpect(jsonPath("$.data.message").isString());
    }

    @Test
    public void testGetAlertRulesError() throws Exception {
        when(metricsEnhancedService.getAlertRulesYaml())
                .thenThrow(new RuntimeException("Alert service error"));

        final String url = "/api/metrics/alerts";
        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);

        performBusinessErrorExpect(perform);
    }

    // ==================== exportGrafanaJson ====================

    @Test
    public void testExportGrafanaJsonNullBodyExportsAll() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> grafanaResult = new LinkedHashMap<>();
        grafanaResult.put("exportedCount", 13);
        grafanaResult.put("grafanaJson", "{\"dashboards\":[]}");

        // With no request body the controller passes null dashboardIds, so use a
        // null-friendly matcher (anyList() does not match null).
        when(metricsEnhancedService.exportGrafanaJson(org.mockito.ArgumentMatchers.<List<String>>any()))
                .thenReturn(grafanaResult);

        final String url = "/api/metrics/export/grafana";
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.contentType(MediaType.APPLICATION_JSON);
        // No body - should pass null dashboardIds to service
        perform = mockMvc.perform(requestBuilder);

        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grafanaVersion").value("8.0+"))
                .andExpect(jsonPath("$.data.dashboards.exportedCount").value(13))
                .andExpect(jsonPath("$.data.message").isString());
    }

    @Test
    public void testExportGrafanaJsonWithSpecificDashboardIds() throws Exception {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("dashboardIds", Arrays.asList("cluster-overview", "broker-stats", "consumer-lag"));

        @SuppressWarnings("unchecked")
        Map<String, Object> grafanaResult = new LinkedHashMap<>();
        grafanaResult.put("exportedCount", 3);
        grafanaResult.put("grafanaJson", "{\"dashboards\":[]}");

        when(metricsEnhancedService.exportGrafanaJson(anyList())).thenReturn(grafanaResult);

        final String url = "/api/metrics/export/grafana";
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.contentType(MediaType.APPLICATION_JSON);
        requestBuilder.content(JSON.toJSONString(requestBody));
        perform = mockMvc.perform(requestBuilder);

        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grafanaVersion").value("8.0+"))
                .andExpect(jsonPath("$.data.dashboards.exportedCount").value(3))
                .andExpect(jsonPath("$.data.message").isString());
    }

    @Test
    public void testExportGrafanaJsonWithEmptyDashboardIdsList() throws Exception {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("dashboardIds", Collections.emptyList());

        @SuppressWarnings("unchecked")
        Map<String, Object> grafanaResult = new LinkedHashMap<>();
        grafanaResult.put("exportedCount", 0);
        grafanaResult.put("grafanaJson", "{\"dashboards\":[]}");

        when(metricsEnhancedService.exportGrafanaJson(anyList())).thenReturn(grafanaResult);

        final String url = "/api/metrics/export/grafana";
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.contentType(MediaType.APPLICATION_JSON);
        requestBuilder.content(JSON.toJSONString(requestBody));
        perform = mockMvc.perform(requestBuilder);

        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grafanaVersion").value("8.0+"))
                .andExpect(jsonPath("$.data.dashboards.exportedCount").value(0));
    }

    @Test
    public void testExportGrafanaJsonError() throws Exception {
        // Null-friendly matcher: matches the null dashboardIds passed when the request has no body
        when(metricsEnhancedService.exportGrafanaJson(org.mockito.ArgumentMatchers.<List<String>>any()))
                .thenThrow(new RuntimeException("Export failed"));

        final String url = "/api/metrics/export/grafana";
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.contentType(MediaType.APPLICATION_JSON);
        perform = mockMvc.perform(requestBuilder);

        performBusinessErrorExpect(perform);
    }

    // ==================== getPrebuiltQueries ====================

    @Test
    public void testGetPrebuiltQueries() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> queries = new LinkedHashMap<>();

        List<Map<String, Object>> overviewQueries = new ArrayList<>();
        Map<String, Object> query1 = new LinkedHashMap<>();
        query1.put("id", "cluster-overview");
        query1.put("title", "Cluster Overview");
        query1.put("promql", "sum(rocketmq_broker_bornTotal)");
        query1.put("description", "Overall RocketMQ cluster health");
        overviewQueries.add(query1);
        queries.put("Overview", overviewQueries);

        List<Map<String, Object>> brokerQueries = new ArrayList<>();
        Map<String, Object> query2 = new LinkedHashMap<>();
        query2.put("id", "broker-stats");
        query2.put("title", "Broker Statistics");
        query2.put("promql", "rate(rocketmq_broker_sendTPS[5m])");
        query2.put("description", "Real-time send TPS per broker");
        brokerQueries.add(query2);
        queries.put("Broker", brokerQueries);

        when(metricsEnhancedService.getPrebuiltQueries()).thenReturn(queries);

        final String url = "/api/metrics/queries";
        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);

        performOkExpect(perform)
                .andExpect(jsonPath("$.data.Overview[0].id").value("cluster-overview"))
                .andExpect(jsonPath("$.data.Overview[0].promql").value("sum(rocketmq_broker_bornTotal)"))
                .andExpect(jsonPath("$.data.Broker[0].id").value("broker-stats"));
    }

    @Test
    public void testGetPrebuiltQueriesEmpty() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> emptyQueries = new LinkedHashMap<>();
        when(metricsEnhancedService.getPrebuiltQueries()).thenReturn(emptyQueries);

        final String url = "/api/metrics/queries";
        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);

        performOkExpect(perform);
    }

    @Test
    public void testGetPrebuiltQueriesError() throws Exception {
        when(metricsEnhancedService.getPrebuiltQueries())
                .thenThrow(new RuntimeException("Query service unavailable"));

        final String url = "/api/metrics/queries";
        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);

        performBusinessErrorExpect(perform);
    }

    // ==================== Error handling: RuntimeException ====================

    @Test
    public void testListDashboardsRuntimeExceptionReturnsErrorJsonResult() throws Exception {
        when(metricsEnhancedService.listDashboards())
                .thenThrow(new RuntimeException("Unexpected error"));

        final String url = "/api/metrics/dashboards";
        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);

        perform.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1))
                .andExpect(jsonPath("$.errMsg").isNotEmpty());
    }

    // ==================================================================
    // Direct-invocation tests for the remaining endpoints (Prometheus
    // text exposition, PromQL proxy, data source CRUD, self-check, ...)
    // ==================================================================

    @SuppressWarnings("unchecked")
    private <T> JsonResult<T> asResult(Object obj) {
        return (JsonResult<T>) obj;
    }

    // ==================== Prometheus text endpoints ====================

    @Test
    public void testExportAllMetrics() {
        when(metricsService.getClusterMetricsExposition()).thenReturn("# HELP up\nup 1");
        assertEquals("# HELP up\nup 1", metricsController.exportAllMetrics());

        when(metricsService.getClusterMetricsExposition()).thenThrow(new RuntimeException("boom"));
        assertTrue(metricsController.exportAllMetrics().startsWith("# Error collecting metrics"));
    }

    @Test
    public void testExportClusterMetrics() {
        when(metricsService.getClusterMetricsExposition()).thenReturn("cluster 1");
        assertEquals("cluster 1", metricsController.exportClusterMetrics());

        when(metricsService.getClusterMetricsExposition()).thenThrow(new RuntimeException("boom"));
        assertTrue(metricsController.exportClusterMetrics().startsWith("# Error"));
    }

    @Test
    public void testExportBrokerMetrics() {
        Map<String, Object> metrics = Collections.singletonMap("tps", 1);
        when(metricsService.getBrokerMetrics("broker-a")).thenReturn(metrics);
        when(prometheusAdapter.toPrometheusFormat(metrics)).thenReturn("broker 1");
        assertEquals("broker 1", metricsController.exportBrokerMetrics("broker-a"));

        when(metricsService.getBrokerMetrics("broker-b")).thenThrow(new RuntimeException("boom"));
        assertTrue(metricsController.exportBrokerMetrics("broker-b").startsWith("# Error"));
    }

    @Test
    public void testExportTopicMetrics() {
        Map<String, Object> metrics = Collections.singletonMap("inTps", 2);
        when(metricsService.getTopicMetrics("topic-a")).thenReturn(metrics);
        when(prometheusAdapter.toPrometheusFormat(metrics)).thenReturn("topic 2");
        assertEquals("topic 2", metricsController.exportTopicMetrics("topic-a"));

        when(metricsService.getTopicMetrics("topic-b")).thenThrow(new RuntimeException("boom"));
        assertTrue(metricsController.exportTopicMetrics("topic-b").startsWith("# Error"));
    }

    @Test
    public void testExportConsumerGroupMetrics() {
        Map<String, Object> metrics = Collections.singletonMap("lag", 3);
        when(metricsService.getConsumerGroupMetrics("group-a")).thenReturn(metrics);
        when(prometheusAdapter.toPrometheusFormat(metrics)).thenReturn("group 3");
        assertEquals("group 3", metricsController.exportConsumerGroupMetrics("group-a"));

        when(metricsService.getConsumerGroupMetrics("group-b")).thenThrow(new RuntimeException("boom"));
        assertTrue(metricsController.exportConsumerGroupMetrics("group-b").startsWith("# Error"));
    }

    @Test
    public void testExportClientMetrics() {
        Map<String, Object> metrics = Collections.singletonMap("clients", 4);
        when(metricsService.getClientMetrics()).thenReturn(metrics);
        when(prometheusAdapter.toPrometheusFormat(metrics)).thenReturn("client 4");
        assertEquals("client 4", metricsController.exportClientMetrics());

        when(metricsService.getClientMetrics()).thenThrow(new RuntimeException("boom"));
        assertTrue(metricsController.exportClientMetrics().startsWith("# Error"));
    }

    @Test
    public void testExportSystemMetrics() {
        Map<String, Object> metrics = Collections.singletonMap("cpu", 5);
        when(metricsService.getSystemMetrics()).thenReturn(metrics);
        when(prometheusAdapter.toPrometheusFormat(metrics)).thenReturn("system 5");
        assertEquals("system 5", metricsController.exportSystemMetrics());

        when(metricsService.getSystemMetrics()).thenThrow(new RuntimeException("boom"));
        assertTrue(metricsController.exportSystemMetrics().startsWith("# Error"));
    }

    @Test
    public void testExportCustomMetrics() {
        Map<String, Object> metrics = Collections.singletonMap("custom", 6);
        when(metricsService.getCustomMetrics("jvm")).thenReturn(metrics);
        when(prometheusAdapter.toPrometheusFormat(metrics)).thenReturn("custom 6");
        assertEquals("custom 6", metricsController.exportCustomMetrics("jvm"));

        when(metricsService.getCustomMetrics("disk")).thenThrow(new RuntimeException("boom"));
        assertTrue(metricsController.exportCustomMetrics("disk").startsWith("# Error"));
    }

    @Test
    public void testGetMetricsSummary() {
        Map<String, Object> summary = Collections.singletonMap("brokerCount", 2);
        when(metricsService.getMetricsSummary()).thenReturn(summary);
        assertEquals(summary, metricsController.getMetricsSummary());
    }

    // ==================== promqlQuery ====================

    @Test
    public void testPromqlQueryBlank() {
        JsonResult<Object> result = asResult(metricsController.promqlQuery(" ", null, null));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testPromqlQuerySuccess() {
        Map<String, Object> data = Collections.singletonMap("resultType", "vector");
        when(metricsService.executePromqlQuery(anyMap())).thenReturn(data);

        JsonResult<Map<String, Object>> result =
            asResult(metricsController.promqlQuery("up", "1700000000", "ds-1"));
        assertEquals(0, result.getStatus());
        assertEquals(data, result.getData());
    }

    @Test
    public void testPromqlQueryUnsupported() {
        when(metricsService.executePromqlQuery(anyMap()))
            .thenThrow(new UnsupportedOperationException("no datasource"));

        JsonResult<Map<String, Object>> result =
            asResult(metricsController.promqlQuery("up", null, null));
        assertEquals(2, result.getStatus());
        assertEquals(Boolean.FALSE, result.getData().get("supported"));
    }

    @Test
    public void testPromqlQueryError() {
        when(metricsService.executePromqlQuery(anyMap()))
            .thenThrow(new RuntimeException("prometheus down"));

        JsonResult<Object> result = asResult(metricsController.promqlQuery("up", null, null));
        assertEquals(1, result.getStatus());
        assertTrue(result.getErrMsg().contains("prometheus down"));
    }

    // ==================== promqlRangeQuery ====================

    @Test
    public void testPromqlRangeQueryParamValidation() {
        assertEquals(1, asResult(metricsController.promqlRangeQuery(" ", "1", "2", "15s", null)).getStatus());
        assertEquals(1, asResult(metricsController.promqlRangeQuery("up", " ", "2", "15s", null)).getStatus());
        assertEquals(1, asResult(metricsController.promqlRangeQuery("up", "1", " ", "15s", null)).getStatus());
        assertEquals(1, asResult(metricsController.promqlRangeQuery("up", "1", "2", " ", null)).getStatus());
    }

    @Test
    public void testPromqlRangeQuerySuccess() {
        Map<String, Object> data = Collections.singletonMap("resultType", "matrix");
        when(metricsService.executePromqlRangeQuery(anyMap())).thenReturn(data);

        JsonResult<Map<String, Object>> result =
            asResult(metricsController.promqlRangeQuery("up", "1", "2", "15s", "ds-1"));
        assertEquals(0, result.getStatus());
        assertEquals(data, result.getData());
    }

    @Test
    public void testPromqlRangeQueryUnsupported() {
        when(metricsService.executePromqlRangeQuery(anyMap()))
            .thenThrow(new UnsupportedOperationException("no datasource"));

        JsonResult<Map<String, Object>> result =
            asResult(metricsController.promqlRangeQuery("up", "1", "2", "15s", null));
        assertEquals(2, result.getStatus());
        assertEquals(Boolean.FALSE, result.getData().get("supported"));
    }

    @Test
    public void testPromqlRangeQueryError() {
        when(metricsService.executePromqlRangeQuery(anyMap()))
            .thenThrow(new RuntimeException("range failed"));

        JsonResult<Object> result =
            asResult(metricsController.promqlRangeQuery("up", "1", "2", "15s", null));
        assertEquals(1, result.getStatus());
    }

    // ==================== data source CRUD ====================

    @Test
    public void testListDataSources() {
        when(metricsService.listDataSources())
            .thenReturn(Collections.singletonList(Collections.singletonMap("id", "ds-1")));
        JsonResult<List<Map<String, Object>>> result = asResult(metricsController.listDataSources());
        assertEquals(0, result.getStatus());
        assertEquals(1, result.getData().size());

        when(metricsService.listDataSources()).thenThrow(new RuntimeException("boom"));
        assertEquals(1, asResult(metricsController.listDataSources()).getStatus());
    }

    @Test
    public void testCreateDataSourceValidation() {
        assertEquals(1, asResult(metricsController.createDataSource(null)).getStatus());

        MetricsDataSourceRequest noName = new MetricsDataSourceRequest();
        noName.setUrl("http://prom:9090");
        assertEquals(1, asResult(metricsController.createDataSource(noName)).getStatus());

        MetricsDataSourceRequest noUrl = new MetricsDataSourceRequest();
        noUrl.setName("prom");
        assertEquals(1, asResult(metricsController.createDataSource(noUrl)).getStatus());
    }

    @Test
    public void testCreateDataSourceSuccess() {
        MetricsDataSourceRequest request = new MetricsDataSourceRequest();
        request.setName("prom");
        request.setUrl("http://prom:9090");
        Map<String, Object> created = Collections.singletonMap("id", "ds-1");
        when(metricsService.createDataSource(request)).thenReturn(created);

        JsonResult<Map<String, Object>> result = asResult(metricsController.createDataSource(request));
        assertEquals(0, result.getStatus());
        assertEquals(created, result.getData());
    }

    @Test
    public void testCreateDataSourceErrors() {
        MetricsDataSourceRequest request = new MetricsDataSourceRequest();
        request.setName("prom");
        request.setUrl("http://prom:9090");

        // chain the stubbed throwables: re-stubbing the same call with when()
        // would trigger the previously stubbed exception
        when(metricsService.createDataSource(request))
            .thenThrow(new IllegalArgumentException("duplicate name"))
            .thenThrow(new RuntimeException("boom"));

        JsonResult<Object> iae = asResult(metricsController.createDataSource(request));
        assertEquals(1, iae.getStatus());
        assertEquals("duplicate name", iae.getErrMsg());

        assertEquals(1, asResult(metricsController.createDataSource(request)).getStatus());
    }

    @Test
    public void testUpdateDataSource() {
        MetricsDataSourceRequest request = new MetricsDataSourceRequest();
        request.setName("prom");

        assertEquals(1, asResult(metricsController.updateDataSource(" ", request)).getStatus());
        assertEquals(1, asResult(metricsController.updateDataSource("ds-1", null)).getStatus());

        Map<String, Object> updated = Collections.singletonMap("id", "ds-1");
        when(metricsService.updateDataSource("ds-1", request)).thenReturn(updated);
        JsonResult<Map<String, Object>> ok = asResult(metricsController.updateDataSource("ds-1", request));
        assertEquals(0, ok.getStatus());
        assertEquals(updated, ok.getData());

        when(metricsService.updateDataSource("ds-2", request))
            .thenThrow(new IllegalArgumentException("not found"));
        assertEquals(1, asResult(metricsController.updateDataSource("ds-2", request)).getStatus());

        when(metricsService.updateDataSource("ds-3", request)).thenThrow(new RuntimeException("boom"));
        assertEquals(1, asResult(metricsController.updateDataSource("ds-3", request)).getStatus());
    }

    @Test
    public void testDeleteDataSource() {
        assertEquals(1, asResult(metricsController.deleteDataSource(" ")).getStatus());

        when(metricsService.deleteDataSource("ds-1")).thenReturn(true);
        JsonResult<Map<String, Object>> deleted = asResult(metricsController.deleteDataSource("ds-1"));
        assertEquals(0, deleted.getStatus());
        assertEquals(Boolean.TRUE, deleted.getData().get("success"));
        assertEquals("Data source deleted successfully", deleted.getData().get("message"));

        when(metricsService.deleteDataSource("ds-2")).thenReturn(false);
        JsonResult<Map<String, Object>> missed = asResult(metricsController.deleteDataSource("ds-2"));
        assertEquals(Boolean.FALSE, missed.getData().get("success"));

        when(metricsService.deleteDataSource("ds-3")).thenThrow(new RuntimeException("boom"));
        assertEquals(1, asResult(metricsController.deleteDataSource("ds-3")).getStatus());
    }

    @Test
    public void testTestDataSource() {
        assertEquals(1, asResult(metricsController.testDataSource(" ")).getStatus());

        Map<String, Object> testResult = new LinkedHashMap<>();
        testResult.put("connected", true);
        when(metricsService.testDataSource("ds-1")).thenReturn(testResult);
        JsonResult<Map<String, Object>> ok = asResult(metricsController.testDataSource("ds-1"));
        assertEquals(0, ok.getStatus());
        assertNotNull(ok.getData().get("supportedProviderTypes"));

        when(metricsService.testDataSource("ds-2")).thenThrow(new RuntimeException("boom"));
        assertEquals(1, asResult(metricsController.testDataSource("ds-2")).getStatus());
    }

    // ==================== alerts.yaml / grafana GET / panels alias ====================

    @Test
    public void testExportAlertRulesYaml() {
        when(metricsEnhancedService.getAlertRulesYaml()).thenReturn("groups: []");
        assertEquals("groups: []", metricsController.exportAlertRulesYaml());

        when(metricsEnhancedService.getAlertRulesYaml()).thenThrow(new RuntimeException("boom"));
        assertTrue(metricsController.exportAlertRulesYaml().startsWith("# Error exporting alert rules"));
    }

    @Test
    public void testExportGrafanaJsonGetAll() {
        Map<String, Object> grafanaJson = Collections.singletonMap("exportedCount", 13);
        when(metricsEnhancedService.exportGrafanaJson(org.mockito.ArgumentMatchers.<List<String>>any()))
            .thenReturn(grafanaJson);

        JsonResult<Map<String, Object>> result = asResult(metricsController.exportGrafanaJsonGet(null));
        assertEquals(0, result.getStatus());
        assertEquals(grafanaJson, result.getData().get("dashboards"));
    }

    @Test
    public void testExportGrafanaJsonGetWithIds() {
        Map<String, Object> grafanaJson = Collections.singletonMap("exportedCount", 2);
        when(metricsEnhancedService.exportGrafanaJson(eq(Arrays.asList("a", "b"))))
            .thenReturn(grafanaJson);

        JsonResult<Map<String, Object>> result =
            asResult(metricsController.exportGrafanaJsonGet(" a , b ,"));
        assertEquals(0, result.getStatus());
        assertEquals(grafanaJson, result.getData().get("dashboards"));
    }

    @Test
    public void testExportGrafanaJsonGetError() {
        when(metricsEnhancedService.exportGrafanaJson(org.mockito.ArgumentMatchers.<List<String>>any()))
            .thenThrow(new RuntimeException("boom"));
        assertEquals(1, asResult(metricsController.exportGrafanaJsonGet(null)).getStatus());
    }

    @Test
    public void testListPanelsAlias() {
        when(metricsEnhancedService.listDashboards())
            .thenReturn(Collections.singletonList(Collections.singletonMap("id", "p1")));
        JsonResult<List<Map<String, Object>>> result = asResult(metricsController.listPanelsAlias());
        assertEquals(0, result.getStatus());
        assertEquals(1, result.getData().size());

        when(metricsEnhancedService.listDashboards()).thenThrow(new RuntimeException("boom"));
        assertEquals(1, asResult(metricsController.listPanelsAlias()).getStatus());
    }

    // ==================== selfCheck / federate ====================

    @Test
    public void testSelfCheck() {
        MetricsSelfCheckResult selfCheckResult = mock(MetricsSelfCheckResult.class);
        when(metricsEnhancedService.selfCheck()).thenReturn(selfCheckResult);
        JsonResult<MetricsSelfCheckResult> ok = asResult(metricsController.selfCheck());
        assertEquals(0, ok.getStatus());
        assertEquals(selfCheckResult, ok.getData());

        when(metricsEnhancedService.selfCheck()).thenThrow(new RuntimeException("boom"));
        assertEquals(1, asResult(metricsController.selfCheck()).getStatus());
    }

    @Test
    public void testFederate() {
        when(metricsService.federate(Arrays.asList("rocketmq_broker_*"))).thenReturn("broker 1");
        assertEquals("broker 1", metricsController.federate(Arrays.asList("rocketmq_broker_*")));

        when(metricsService.federate(null)).thenThrow(new RuntimeException("boom"));
        assertTrue(metricsController.federate(null).startsWith("# Error producing federation export"));
    }
}
