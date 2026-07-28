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

import org.apache.rocketmq.dashboard.adapter.PrometheusMetricsAdapter;
import org.apache.rocketmq.dashboard.architecture.ClusterProvider;
import org.apache.rocketmq.dashboard.architecture.MetadataProvider;
import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.model.ClusterTopology;
import org.apache.rocketmq.dashboard.model.request.MetricsDataSourceRequest;
import org.apache.rocketmq.dashboard.service.ArchitectureBasedService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class MetricsServiceImplTest {

    /** A local address that instantly refuses connections; keeps HTTP paths offline. */
    private static final String REFUSED_URL = "http://127.0.0.1:1";

    @InjectMocks
    private MetricsServiceImpl metricsService;

    @Mock
    private MetadataProvider metadataProvider;

    @Mock
    private ClusterProvider clusterProvider;

    @Mock
    private PrometheusMetricsAdapter prometheusAdapter;

    @Before
    public void setUp() throws Exception {
        setField(MetricsServiceImpl.class, "metadataProvider", metadataProvider);
        setField(MetricsServiceImpl.class, "clusterProvider", clusterProvider);
        setField(MetricsServiceImpl.class, "prometheusAdapter", prometheusAdapter);
        setField(ArchitectureBasedService.class, "metadataProvider", metadataProvider);
        setField(ArchitectureBasedService.class, "clusterProvider", clusterProvider);
    }

    private void setField(Class<?> clazz, String name, Object value) throws Exception {
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        field.set(metricsService, value);
    }

    private void givenCapabilities(String... capabilities) throws Exception {
        ClusterCapability capability = new ClusterCapability();
        capability.setExtendedCapabilities(new HashSet<>(Arrays.asList(capabilities)));
        capability.setArchitectureVersion("5.0");
        when(clusterProvider.getClusterCapability()).thenReturn(capability);
    }

    private MetricsDataSourceRequest buildRequest(String name, String url) {
        MetricsDataSourceRequest request = new MetricsDataSourceRequest();
        request.setName(name);
        request.setUrl(url);
        return request;
    }

    // ==================== metric getters ====================

    @Test
    public void testGetClusterMetricsSupported() throws Exception {
        givenCapabilities("METRICS_EXPORT");
        when(metadataProvider.getClusterMetrics()).thenReturn(Map.of("tps", 100));

        Map<String, Object> result = metricsService.getClusterMetrics();
        assertEquals(100, result.get("tps"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetClusterMetricsUnsupported() throws Exception {
        givenCapabilities();
        metricsService.getClusterMetrics();
    }

    @Test
    public void testGetBrokerMetricsSupported() throws Exception {
        givenCapabilities("BROKER_METRICS");
        when(metadataProvider.getBrokerMetrics("broker-a")).thenReturn(Map.of("tps", 5));

        assertEquals(5, metricsService.getBrokerMetrics("broker-a").get("tps"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetBrokerMetricsEmptyName() {
        metricsService.getBrokerMetrics(" ");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetBrokerMetricsUnsupported() throws Exception {
        givenCapabilities();
        metricsService.getBrokerMetrics("broker-a");
    }

    @Test
    public void testGetTopicMetricsSupported() throws Exception {
        givenCapabilities("TOPIC_METRICS");
        when(metadataProvider.getTopicMetrics("topicA")).thenReturn(Map.of("inTps", 1));

        assertEquals(1, metricsService.getTopicMetrics("topicA").get("inTps"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetTopicMetricsEmptyTopic() {
        metricsService.getTopicMetrics(null);
    }

    @Test
    public void testGetConsumerGroupMetricsSupported() throws Exception {
        givenCapabilities("CONSUMER_GROUP_METRICS");
        when(metadataProvider.getConsumerGroupMetrics("group-a")).thenReturn(Map.of("lag", 7));

        assertEquals(7, metricsService.getConsumerGroupMetrics("group-a").get("lag"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetConsumerGroupMetricsEmptyGroup() {
        metricsService.getConsumerGroupMetrics("");
    }

    @Test
    public void testGetAllBrokersMetricsSupported() throws Exception {
        givenCapabilities("ALL_BROKERS_METRICS");
        when(metadataProvider.getAllBrokersMetrics())
            .thenReturn(Collections.singletonList(Map.of("broker", "a")));

        assertEquals(1, metricsService.getAllBrokersMetrics().size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetAllBrokersMetricsUnsupported() throws Exception {
        givenCapabilities();
        metricsService.getAllBrokersMetrics();
    }

    @Test
    public void testGetAllTopicsMetricsSupported() throws Exception {
        givenCapabilities("ALL_TOPICS_METRICS");
        when(metadataProvider.getAllTopicsMetrics())
            .thenReturn(Collections.singletonList(Map.of("topic", "t")));

        assertEquals(1, metricsService.getAllTopicsMetrics().size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetAllTopicsMetricsUnsupported() throws Exception {
        givenCapabilities();
        metricsService.getAllTopicsMetrics();
    }

    @Test
    public void testGetClientMetricsSupported() throws Exception {
        givenCapabilities("CLIENT_METRICS");
        when(metadataProvider.getClientMetrics()).thenReturn(Map.of("clients", 3));

        assertEquals(3, metricsService.getClientMetrics().get("clients"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetClientMetricsUnsupported() throws Exception {
        givenCapabilities();
        metricsService.getClientMetrics();
    }

    @Test
    public void testGetSystemMetricsSupported() throws Exception {
        givenCapabilities("SYSTEM_METRICS");
        when(metadataProvider.getSystemMetrics()).thenReturn(Map.of("cpu", 0.5));

        assertEquals(0.5, metricsService.getSystemMetrics().get("cpu"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetSystemMetricsUnsupported() throws Exception {
        givenCapabilities();
        metricsService.getSystemMetrics();
    }

    @Test
    public void testGetCustomMetricsSupported() throws Exception {
        givenCapabilities("CUSTOM_METRICS");
        when(metadataProvider.getCustomMetrics("jvm")).thenReturn(Map.of("heap", 1));

        assertEquals(1, metricsService.getCustomMetrics("jvm").get("heap"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetCustomMetricsEmptyType() {
        metricsService.getCustomMetrics("  ");
    }

    @Test
    public void testConfigureMetricsExportSupported() throws Exception {
        givenCapabilities("METRICS_CONFIGURATION");
        assertTrue(metricsService.configureMetricsExport("interval=10s"));
        verify(metadataProvider).configureMetricsExport("interval=10s");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testConfigureMetricsExportUnsupported() throws Exception {
        givenCapabilities();
        metricsService.configureMetricsExport("interval=10s");
    }

    @Test
    public void testGetMetricsSummary() throws Exception {
        givenCapabilities("METRICS_EXPORT", "BROKER_METRICS");
        when(clusterProvider.getClusterName()).thenReturn("DefaultCluster");
        when(clusterProvider.getClusterVersion()).thenReturn("5.3.3");

        Map<String, Object> summary = metricsService.getMetricsSummary();
        assertEquals("DefaultCluster", summary.get("cluster_name"));
        assertEquals("5.3.3", summary.get("cluster_version"));
        assertEquals(Boolean.TRUE, summary.get("metrics_supported"));
        assertEquals(Boolean.TRUE, summary.get("broker_metrics"));
        assertEquals(Boolean.FALSE, summary.get("topic_metrics"));
        assertNotNull(summary.get("timestamp"));
    }

    // ==================== data source management ====================

    @Test
    public void testCreateDataSourceValidation() {
        try {
            metricsService.createDataSource(buildRequest(null, REFUSED_URL));
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ignore
        }
        try {
            metricsService.createDataSource(buildRequest("prom", "  "));
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ignore
        }
    }

    @Test
    public void testCreateDataSourceDefaults() {
        Map<String, Object> ds = metricsService.createDataSource(buildRequest("prom", REFUSED_URL));

        assertNotNull(ds.get("id"));
        assertEquals("prom", ds.get("name"));
        assertEquals("PROMETHEUS", ds.get("type"));
        assertEquals(REFUSED_URL, ds.get("url"));
        assertEquals(5000, ds.get("connectionTimeoutMs"));
        assertEquals(30000, ds.get("readTimeoutMs"));
        assertEquals(1, metricsService.listDataSources().size());
    }

    @Test
    public void testCreateDataSourceDefaultFlagClearsOthers() {
        Map<String, Object> first = metricsService.createDataSource(buildRequest("first", REFUSED_URL));

        MetricsDataSourceRequest second = buildRequest("second", REFUSED_URL);
        second.setDefault(true);
        Map<String, Object> secondDs = metricsService.createDataSource(second);

        assertEquals(Boolean.TRUE, secondDs.get("isDefault"));
        assertEquals(Boolean.FALSE, first.get("isDefault"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUpdateDataSourceNotFound() {
        metricsService.updateDataSource("missing", buildRequest("prom", REFUSED_URL));
    }

    @Test
    public void testUpdateDataSource() {
        Map<String, Object> ds = metricsService.createDataSource(buildRequest("prom", REFUSED_URL));
        String id = (String) ds.get("id");

        MetricsDataSourceRequest update = new MetricsDataSourceRequest();
        update.setName("prom-2");
        update.setType("VICTORIA");
        update.setUrl("http://127.0.0.1:2");
        update.setUsername("admin");
        update.setDefault(true);
        update.setConnectionTimeoutMs(1234);
        update.setReadTimeoutMs(4321);
        update.setDescription("desc");
        update.setCustomHeaders("X-Custom: 1");

        Map<String, Object> updated = metricsService.updateDataSource(id, update);
        assertEquals("prom-2", updated.get("name"));
        assertEquals("VICTORIA", updated.get("type"));
        assertEquals("http://127.0.0.1:2", updated.get("url"));
        assertEquals("admin", updated.get("username"));
        assertEquals(1234, updated.get("connectionTimeoutMs"));
        assertEquals(4321, updated.get("readTimeoutMs"));
        assertEquals("desc", updated.get("description"));
        assertEquals(Boolean.TRUE, updated.get("isDefault"));
        assertNotNull(updated.get("updatedAt"));
    }

    @Test
    public void testDeleteDataSource() {
        assertFalse(metricsService.deleteDataSource("missing"));

        MetricsDataSourceRequest first = buildRequest("first", REFUSED_URL);
        first.setDefault(true);
        Map<String, Object> firstDs = metricsService.createDataSource(first);
        Map<String, Object> secondDs = metricsService.createDataSource(buildRequest("second", REFUSED_URL));

        assertTrue(metricsService.deleteDataSource((String) firstDs.get("id")));
        // remaining data source is promoted to default
        assertEquals(Boolean.TRUE, secondDs.get("isDefault"));
        assertTrue(metricsService.deleteDataSource((String) secondDs.get("id")));
        assertTrue(metricsService.listDataSources().isEmpty());
    }

    @Test
    public void testTestDataSourceNotFound() {
        Map<String, Object> result = metricsService.testDataSource("missing");
        assertEquals(Boolean.FALSE, result.get("success"));
        assertTrue(((String) result.get("message")).contains("not found"));
    }

    @Test
    public void testTestDataSourceConnectionRefused() {
        Map<String, Object> ds = metricsService.createDataSource(buildRequest("prom", REFUSED_URL));
        Map<String, Object> result = metricsService.testDataSource((String) ds.get("id"));

        assertEquals(Boolean.FALSE, result.get("success"));
        assertTrue(((String) result.get("message")).startsWith("Connection failed"));
        assertNotNull(result.get("error"));
    }

    // ==================== PromQL proxy queries ====================

    @Test(expected = UnsupportedOperationException.class)
    public void testExecutePromqlQueryWithoutDataSource() {
        Map<String, Object> params = new HashMap<>();
        params.put("query", "up");
        metricsService.executePromqlQuery(params);
    }

    @Test
    public void testExecutePromqlQueryConnectionError() {
        metricsService.createDataSource(buildRequest("prom", REFUSED_URL));

        Map<String, Object> params = new HashMap<>();
        params.put("query", "up");
        params.put("time", "1234567890");

        Map<String, Object> result = metricsService.executePromqlQuery(params);
        assertEquals("error", result.get("status"));
        assertNotNull(result.get("error"));
    }

    @Test
    public void testExecutePromqlQueryWithExplicitDatasourceId() {
        Map<String, Object> ds = metricsService.createDataSource(buildRequest("prom", REFUSED_URL + "/"));

        Map<String, Object> params = new HashMap<>();
        params.put("datasourceId", ds.get("id"));
        params.put("query", "rocketmq_producer_tps");

        Map<String, Object> result = metricsService.executePromqlQuery(params);
        assertEquals("error", result.get("status"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testExecutePromqlRangeQueryWithoutDataSource() {
        Map<String, Object> params = new HashMap<>();
        params.put("query", "up");
        metricsService.executePromqlRangeQuery(params);
    }

    @Test
    public void testExecutePromqlRangeQueryConnectionError() {
        MetricsDataSourceRequest request = buildRequest("prom", REFUSED_URL);
        request.setUsername("admin");
        metricsService.createDataSource(request);

        Map<String, Object> params = new HashMap<>();
        params.put("query", "up");
        params.put("start", "0");
        params.put("end", "100");
        params.put("step", "15");

        Map<String, Object> result = metricsService.executePromqlRangeQuery(params);
        assertEquals("error", result.get("status"));
    }

    // ==================== federation / exposition ====================

    @Test
    public void testGetClusterMetricsExpositionFallsBackToAdapter() throws Exception {
        givenCapabilities("METRICS_EXPORT");
        when(clusterProvider.getClusterTopology()).thenReturn(null);
        when(metadataProvider.getClusterMetrics()).thenReturn(Map.of("tps", 1));
        when(prometheusAdapter.generateFullMetricsExport(any())).thenReturn("rocketmq_tps 1\n");

        String exposition = metricsService.getClusterMetricsExposition();
        assertEquals("rocketmq_tps 1\n", exposition);
    }

    @Test
    public void testGetClusterMetricsExpositionScrapeFailureFallsBack() throws Exception {
        givenCapabilities("METRICS_EXPORT");
        ClusterTopology topology = new ClusterTopology();
        topology.addNode("broker-a", 0L, "127.0.0.1:10911", "BROKER");
        topology.addNode("broker-a-blank", 1L, "  ", "BROKER");
        when(clusterProvider.getClusterTopology()).thenReturn(topology);
        when(metadataProvider.getClusterMetrics()).thenReturn(Map.of("tps", 1));
        when(prometheusAdapter.generateFullMetricsExport(any())).thenReturn("rocketmq_tps 1\n");

        // scrape of 127.0.0.1:5557 fails -> falls back to generated exposition
        String exposition = metricsService.getClusterMetricsExposition();
        assertEquals("rocketmq_tps 1\n", exposition);
    }

    @Test
    public void testFederateAppliesSelectors() throws Exception {
        givenCapabilities("METRICS_EXPORT");
        when(clusterProvider.getClusterTopology()).thenReturn(null);
        when(metadataProvider.getClusterMetrics()).thenReturn(Map.of());
        when(prometheusAdapter.generateFullMetricsExport(any()))
            .thenReturn("# HELP rocketmq_tps tps\nrocketmq_tps 1\njava_heap_used 2\n");

        String filtered = metricsService.federate(Collections.singletonList("rocketmq_*"));
        assertTrue(filtered.contains("rocketmq_tps 1"));
        assertFalse(filtered.contains("java_heap_used"));
        assertTrue(filtered.contains("# HELP"));
    }

    // ==================== static filterPrometheusText ====================

    @Test
    public void testFilterPrometheusTextNullOrEmpty() {
        assertNull(MetricsServiceImpl.filterPrometheusText(null, Collections.singletonList("a")));
        assertEquals("", MetricsServiceImpl.filterPrometheusText("", Collections.singletonList("a")));
    }

    @Test
    public void testFilterPrometheusTextNoSelectors() {
        String exposition = "rocketmq_tps 1\n";
        assertEquals(exposition, MetricsServiceImpl.filterPrometheusText(exposition, null));
        assertEquals(exposition, MetricsServiceImpl.filterPrometheusText(exposition, Collections.emptyList()));
        assertEquals(exposition, MetricsServiceImpl.filterPrometheusText(exposition, Arrays.asList("  ", null)));
    }

    @Test
    public void testFilterPrometheusTextWildcard() {
        String exposition = "# TYPE rocketmq_tps counter\n"
            + "rocketmq_tps{broker=\"a\"} 1\n"
            + "\n"
            + "jvm_threads 5\n";

        String filtered = MetricsServiceImpl.filterPrometheusText(exposition, Collections.singletonList("rocketmq_*"));
        assertTrue(filtered.contains("rocketmq_tps{broker=\"a\"} 1"));
        assertTrue(filtered.contains("# TYPE"));
        assertFalse(filtered.contains("jvm_threads"));
    }

    @Test
    public void testFilterPrometheusTextExactName() {
        String exposition = "rocketmq_tps 1\nrocketmq_lag 2\n";
        String filtered = MetricsServiceImpl.filterPrometheusText(exposition, Collections.singletonList("rocketmq_lag"));
        assertTrue(filtered.contains("rocketmq_lag 2"));
        assertFalse(filtered.contains("rocketmq_tps 1"));
    }
}
