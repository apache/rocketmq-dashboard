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

import org.apache.rocketmq.dashboard.service.metrics.MetricsAggregationService;
import org.apache.rocketmq.dashboard.service.metrics.PrometheusMetricsQueryClient;
import org.apache.rocketmq.dashboard.support.JsonResult;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class MetricsDataControllerTest {

    @InjectMocks
    private MetricsDataController metricsDataController;

    @Mock
    private MetricsAggregationService metricsAggregationService;

    @Mock
    private PrometheusMetricsQueryClient prometheusClient;

    @SuppressWarnings("unchecked")
    private <T> JsonResult<T> asResult(Object obj) {
        return (JsonResult<T>) obj;
    }

    // ==================== prometheusQuery ====================

    @Test
    public void testPrometheusQueryBlankQuery() {
        JsonResult<Object> result = asResult(metricsDataController.prometheusQuery(" ", null, null));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testPrometheusQueryWithoutTime() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "success");
        when(prometheusClient.queryInstant("up", null)).thenReturn(data);

        JsonResult<Map<String, Object>> result =
            asResult(metricsDataController.prometheusQuery("up", null, null));
        assertEquals(0, result.getStatus());
        assertEquals("success", result.getData().get("status"));
    }

    @Test
    public void testPrometheusQueryWithTime() {
        Map<String, Object> data = new HashMap<>();
        when(prometheusClient.queryInstant("up", "1650000000", "ds1")).thenReturn(data);

        JsonResult<Object> result =
            asResult(metricsDataController.prometheusQuery("up", "1650000000", "ds1"));
        assertEquals(0, result.getStatus());
    }

    @Test
    public void testPrometheusQueryError() {
        when(prometheusClient.queryInstant(anyString(), anyString()))
            .thenThrow(new RuntimeException("connection refused"));

        JsonResult<Object> result = asResult(metricsDataController.prometheusQuery("up", null, "ds1"));
        assertEquals(1, result.getStatus());
        assertTrue(result.getErrMsg().contains("connection refused"));
    }

    // ==================== prometheusRangeQuery ====================

    @Test
    public void testPrometheusRangeQueryBlankQuery() {
        JsonResult<Object> result =
            asResult(metricsDataController.prometheusRangeQuery("", "1", "2", "15s", null));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testPrometheusRangeQueryBlankStart() {
        JsonResult<Object> result =
            asResult(metricsDataController.prometheusRangeQuery("up", " ", "2", "15s", null));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testPrometheusRangeQueryBlankEnd() {
        JsonResult<Object> result =
            asResult(metricsDataController.prometheusRangeQuery("up", "1", "", "15s", null));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testPrometheusRangeQueryBlankStep() {
        JsonResult<Object> result =
            asResult(metricsDataController.prometheusRangeQuery("up", "1", "2", " ", null));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testPrometheusRangeQuerySuccess() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resultType", "matrix");
        when(prometheusClient.queryRange("up", "1", "2", "15s", null)).thenReturn(data);

        JsonResult<Map<String, Object>> result =
            asResult(metricsDataController.prometheusRangeQuery("up", "1", "2", "15s", null));
        assertEquals(0, result.getStatus());
        assertEquals("matrix", result.getData().get("resultType"));
    }

    @Test
    public void testPrometheusRangeQueryError() {
        when(prometheusClient.queryRange("up", "1", "2", "15s", null))
            .thenThrow(new RuntimeException("boom"));

        JsonResult<Object> result =
            asResult(metricsDataController.prometheusRangeQuery("up", "1", "2", "15s", null));
        assertEquals(1, result.getStatus());
    }

    // ==================== getDashboardMetrics ====================

    @Test
    public void testGetDashboardMetrics() {
        when(metricsAggregationService.getDashboardMetrics()).thenReturn(Collections.emptyMap());

        JsonResult<Object> result = asResult(metricsDataController.getDashboardMetrics());
        assertEquals(0, result.getStatus());
        assertNotNull(result.getData());
    }

    @Test
    public void testGetDashboardMetricsError() {
        when(metricsAggregationService.getDashboardMetrics()).thenThrow(new RuntimeException("err"));

        JsonResult<Object> result = asResult(metricsDataController.getDashboardMetrics());
        assertEquals(1, result.getStatus());
    }

    // ==================== getBrokerMetrics ====================

    @Test
    public void testGetBrokerMetricsBlankName() {
        JsonResult<Object> result = asResult(metricsDataController.getBrokerMetrics(" "));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testGetBrokerMetrics() {
        when(metricsAggregationService.getAggregatedBrokerMetrics("broker-a"))
            .thenReturn(Collections.emptyMap());

        JsonResult<Object> result = asResult(metricsDataController.getBrokerMetrics(" broker-a "));
        assertEquals(0, result.getStatus());
    }

    @Test
    public void testGetBrokerMetricsError() {
        when(metricsAggregationService.getAggregatedBrokerMetrics("broker-a"))
            .thenThrow(new RuntimeException("err"));

        JsonResult<Object> result = asResult(metricsDataController.getBrokerMetrics("broker-a"));
        assertEquals(1, result.getStatus());
    }

    // ==================== getTopicMetrics ====================

    @Test
    public void testGetTopicMetricsBlankName() {
        JsonResult<Object> result = asResult(metricsDataController.getTopicMetrics(""));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testGetTopicMetrics() {
        when(metricsAggregationService.getAggregatedTopicMetrics("topicA"))
            .thenReturn(Collections.emptyMap());

        JsonResult<Object> result = asResult(metricsDataController.getTopicMetrics("topicA"));
        assertEquals(0, result.getStatus());
    }

    @Test
    public void testGetTopicMetricsError() {
        when(metricsAggregationService.getAggregatedTopicMetrics("topicA"))
            .thenThrow(new RuntimeException("err"));

        JsonResult<Object> result = asResult(metricsDataController.getTopicMetrics("topicA"));
        assertEquals(1, result.getStatus());
    }

    // ==================== getConsumerGroupMetrics ====================

    @Test
    public void testGetConsumerGroupMetricsBlankGroup() {
        JsonResult<Object> result = asResult(metricsDataController.getConsumerGroupMetrics(" "));
        assertEquals(1, result.getStatus());
    }

    @Test
    public void testGetConsumerGroupMetrics() {
        when(metricsAggregationService.getAggregatedConsumerGroupMetrics("group1"))
            .thenReturn(Collections.emptyMap());

        JsonResult<Object> result = asResult(metricsDataController.getConsumerGroupMetrics("group1"));
        assertEquals(0, result.getStatus());
    }

    @Test
    public void testGetConsumerGroupMetricsError() {
        when(metricsAggregationService.getAggregatedConsumerGroupMetrics("group1"))
            .thenThrow(new RuntimeException("err"));

        JsonResult<Object> result = asResult(metricsDataController.getConsumerGroupMetrics("group1"));
        assertEquals(1, result.getStatus());
    }

    // ==================== getSystemMetrics ====================

    @Test
    public void testGetSystemMetrics() {
        when(metricsAggregationService.getAggregatedSystemMetrics()).thenReturn(Collections.emptyMap());

        JsonResult<Object> result = asResult(metricsDataController.getSystemMetrics());
        assertEquals(0, result.getStatus());
    }

    @Test
    public void testGetSystemMetricsError() {
        when(metricsAggregationService.getAggregatedSystemMetrics())
            .thenThrow(new RuntimeException("err"));

        JsonResult<Object> result = asResult(metricsDataController.getSystemMetrics());
        assertEquals(1, result.getStatus());
    }

    // ==================== getDataSourcesHealth ====================

    @Test
    public void testGetDataSourcesHealth() {
        when(metricsAggregationService.getDataSourcesHealth()).thenReturn(Collections.emptyMap());

        JsonResult<Object> result = asResult(metricsDataController.getDataSourcesHealth());
        assertEquals(0, result.getStatus());
    }

    @Test
    public void testGetDataSourcesHealthError() {
        when(metricsAggregationService.getDataSourcesHealth()).thenThrow(new RuntimeException("err"));

        JsonResult<Object> result = asResult(metricsDataController.getDataSourcesHealth());
        assertEquals(1, result.getStatus());
    }

    // ==================== getQueryTemplates ====================

    @Test
    public void testGetQueryTemplates() {
        Map<String, Map<String, String>> templates = new LinkedHashMap<>();
        templates.put("cluster", Collections.singletonMap("tps", "sum(rate(x[1m]))"));
        when(prometheusClient.getQueryTemplates()).thenReturn(templates);

        JsonResult<Map<String, Object>> result = asResult(metricsDataController.getQueryTemplates());
        assertEquals(0, result.getStatus());
        assertNotNull(result.getData().get("templates"));
        assertNotNull(result.getData().get("description"));
    }

    @Test
    public void testGetQueryTemplatesError() {
        when(prometheusClient.getQueryTemplates()).thenThrow(new RuntimeException("err"));

        JsonResult<Object> result = asResult(metricsDataController.getQueryTemplates());
        assertEquals(1, result.getStatus());
    }
}
