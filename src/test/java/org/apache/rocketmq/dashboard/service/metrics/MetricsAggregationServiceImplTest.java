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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.rocketmq.dashboard.service.MetricsService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class MetricsAggregationServiceImplTest {

    @InjectMocks
    private MetricsAggregationServiceImpl service;

    @Mock
    private PrometheusMetricsQueryClient prometheusClient;

    @Mock
    private MetricsService metricsService;

    private Map<String, Object> prometheusResponse(Map<String, Object> metrics) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("source", "prometheus");
        response.put("metrics", metrics);
        return response;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetDashboardMetricsMergesNativeAndPrometheus() {
        Map<String, Object> promMetrics = new LinkedHashMap<>();
        promMetrics.put("tps", 100.0);
        promMetrics.put("brokerCount", 99); // must lose to native value
        promMetrics.put("emptyMetric", null); // null values are skipped
        when(prometheusClient.clusterMetrics(null)).thenReturn(prometheusResponse(promMetrics));

        Map<String, Object> nativeMetrics = new LinkedHashMap<>();
        nativeMetrics.put("brokerCount", 2);
        when(metricsService.getClusterMetrics()).thenReturn(nativeMetrics);

        Map<String, Object> summary = new HashMap<>();
        summary.put("metrics_supported", true);
        when(metricsService.getMetricsSummary()).thenReturn(summary);
        when(prometheusClient.isDatasourceAvailable(null)).thenReturn(true);

        Map<String, Object> dashboard = service.getDashboardMetrics();

        assertNotNull(dashboard.get("timestamp"));
        Map<String, Object> merged = (Map<String, Object>) dashboard.get("metrics");
        // Native data wins over Prometheus for duplicate keys
        assertEquals(2, merged.get("brokerCount"));
        assertEquals(100.0, merged.get("tps"));
        assertTrue(!merged.containsKey("emptyMetric"));

        Map<String, Object> health = (Map<String, Object>) dashboard.get("dataSourcesHealth");
        Map<String, Object> prometheusHealth = (Map<String, Object>) health.get("prometheus");
        assertEquals("connected", prometheusHealth.get("status"));
        Map<String, Object> nativeHealth = (Map<String, Object>) health.get("native");
        assertEquals("connected", nativeHealth.get("status"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetDashboardMetricsDegradesWhenPrometheusFails() {
        when(prometheusClient.clusterMetrics(null)).thenThrow(new RuntimeException("connection refused"));
        when(prometheusClient.isDatasourceAvailable(null)).thenReturn(false);

        Map<String, Object> nativeMetrics = new LinkedHashMap<>();
        nativeMetrics.put("brokerCount", 2);
        when(metricsService.getClusterMetrics()).thenReturn(nativeMetrics);
        when(metricsService.getMetricsSummary()).thenReturn(new HashMap<>());

        Map<String, Object> dashboard = service.getDashboardMetrics();

        Map<String, Object> prometheusCluster = (Map<String, Object>) dashboard.get("prometheusClusterMetrics");
        assertEquals(Boolean.FALSE, prometheusCluster.get("available"));
        assertEquals("prometheus", prometheusCluster.get("source"));

        Map<String, Object> merged = (Map<String, Object>) dashboard.get("metrics");
        assertEquals(2, merged.get("brokerCount"));

        Map<String, Object> health = (Map<String, Object>) dashboard.get("dataSourcesHealth");
        Map<String, Object> prometheusHealth = (Map<String, Object>) health.get("prometheus");
        assertEquals("unavailable", prometheusHealth.get("status"));
        Map<String, Object> nativeHealth = (Map<String, Object>) health.get("native");
        assertEquals("unsupported", nativeHealth.get("status"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetAggregatedBrokerMetrics() {
        Map<String, Object> promMetrics = new LinkedHashMap<>();
        promMetrics.put("putTps", 10.0);
        when(prometheusClient.brokerMetrics("broker-a", null)).thenReturn(prometheusResponse(promMetrics));

        Map<String, Object> nativeMetrics = new LinkedHashMap<>();
        nativeMetrics.put("version", "5.0");
        when(metricsService.getBrokerMetrics("broker-a")).thenReturn(nativeMetrics);

        Map<String, Object> result = service.getAggregatedBrokerMetrics("broker-a");

        assertEquals("broker-a", result.get("brokerName"));
        Map<String, Object> merged = (Map<String, Object>) result.get("metrics");
        assertEquals(10.0, merged.get("putTps"));
        assertEquals("5.0", merged.get("version"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetAggregatedTopicMetricsWithNativeFailure() {
        when(prometheusClient.topicMetrics("topicA", null))
                .thenReturn(prometheusResponse(new LinkedHashMap<>()));
        when(metricsService.getTopicMetrics("topicA")).thenThrow(new RuntimeException("unsupported"));

        Map<String, Object> result = service.getAggregatedTopicMetrics("topicA");

        assertEquals("topicA", result.get("topicName"));
        Map<String, Object> nativeMetrics = (Map<String, Object>) result.get("nativeMetrics");
        assertEquals(Boolean.FALSE, nativeMetrics.get("available"));
        assertEquals("native", nativeMetrics.get("source"));
    }

    @Test
    public void testGetAggregatedConsumerGroupMetrics() {
        when(prometheusClient.consumerGroupMetrics("groupA", null))
                .thenReturn(prometheusResponse(new LinkedHashMap<>()));
        when(metricsService.getConsumerGroupMetrics("groupA")).thenReturn(new LinkedHashMap<>());

        Map<String, Object> result = service.getAggregatedConsumerGroupMetrics("groupA");
        assertEquals("groupA", result.get("groupName"));
        assertNotNull(result.get("metrics"));
    }

    @Test
    public void testGetAggregatedSystemMetrics() {
        when(prometheusClient.systemMetrics(null)).thenReturn(prometheusResponse(new LinkedHashMap<>()));
        when(metricsService.getSystemMetrics()).thenReturn(new LinkedHashMap<>());

        Map<String, Object> result = service.getAggregatedSystemMetrics();
        assertNotNull(result.get("timestamp"));
        assertNotNull(result.get("metrics"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetDataSourcesHealthReportsNativeError() {
        when(prometheusClient.isDatasourceAvailable(null)).thenReturn(false);
        when(metricsService.getMetricsSummary()).thenThrow(new RuntimeException("admin api broken"));

        Map<String, Object> health = service.getDataSourcesHealth();

        Map<String, Object> nativeHealth = (Map<String, Object>) health.get("native");
        assertEquals("error", nativeHealth.get("status"));
        assertEquals("admin api broken", nativeHealth.get("message"));
    }

    @Test
    public void testExecutePromQLDelegates() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        when(prometheusClient.queryInstant("up", "ds1")).thenReturn(response);

        assertEquals(response, service.executePromQL("up", "ds1"));
        verify(prometheusClient).queryInstant("up", "ds1");
    }

    @Test
    public void testExecutePromQLRangeDelegates() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        when(prometheusClient.queryRange(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(response);

        assertEquals(response, service.executePromQLRange("up", "0", "100", "15s", "ds1"));
        verify(prometheusClient).queryRange("up", "0", "100", "15s", "ds1");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testPrometheusResponseWithoutMetricsSectionIgnored() {
        // Response lacking a "metrics" map contributes nothing to the merge
        Map<String, Object> bareResponse = new LinkedHashMap<>();
        bareResponse.put("source", "prometheus");
        when(prometheusClient.systemMetrics(null)).thenReturn(bareResponse);
        when(metricsService.getSystemMetrics()).thenReturn(new LinkedHashMap<>());

        Map<String, Object> result = service.getAggregatedSystemMetrics();
        Map<String, Object> merged = (Map<String, Object>) result.get("metrics");
        assertTrue(merged.isEmpty());
    }
}
