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
import org.apache.rocketmq.dashboard.service.MetricsEnhancedService;
import org.apache.rocketmq.dashboard.service.MetricsService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

        performErrorExpect(perform);
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

        performErrorExpect(perform);
    }

    @Test
    public void testGetDashboardPanelWithNonExistentId() throws Exception {
        when(metricsEnhancedService.getDashboardPanel("non-existent-panel"))
                .thenReturn(Collections.emptyMap());

        final String url = "/api/metrics/dashboards/non-existent-panel";
        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);

        performErrorExpect(perform);
    }

    @Test
    public void testGetDashboardPanelError() throws Exception {
        when(metricsEnhancedService.getDashboardPanel("cluster-overview"))
                .thenThrow(new RuntimeException("Panel service error"));

        final String url = "/api/metrics/dashboards/cluster-overview";
        requestBuilder = MockMvcRequestBuilders.get(url);
        perform = mockMvc.perform(requestBuilder);

        performErrorExpect(perform);
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

        performErrorExpect(perform);
    }

    // ==================== exportGrafanaJson ====================

    @Test
    public void testExportGrafanaJsonNullBodyExportsAll() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> grafanaResult = new LinkedHashMap<>();
        grafanaResult.put("exportedCount", 13);
        grafanaResult.put("grafanaJson", "{\"dashboards\":[]}");

        when(metricsEnhancedService.exportGrafanaJson(anyList())).thenReturn(grafanaResult);

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
        when(metricsEnhancedService.exportGrafanaJson(anyList()))
                .thenThrow(new RuntimeException("Export failed"));

        final String url = "/api/metrics/export/grafana";
        requestBuilder = MockMvcRequestBuilders.post(url);
        requestBuilder.contentType(MediaType.APPLICATION_JSON);
        perform = mockMvc.perform(requestBuilder);

        performErrorExpect(perform);
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

        performErrorExpect(perform);
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
                .andExpect(jsonPath("$.status").value(-1))
                .andExpect(jsonPath("$.errMsg").isNotEmpty());
    }
}
