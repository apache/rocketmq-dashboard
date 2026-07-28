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

import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.exception.ServiceException;
import org.apache.rocketmq.dashboard.model.MetricsHealthResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class MetricsProviderImplTest {

    private static final String BASE_URL = "http://prom.test:9090";

    private MetricsProviderImpl provider;

    @Mock
    private RMQConfigure rmqConfigure;

    @Mock
    private HttpClient httpClient;

    @Before
    public void setUp() throws Exception {
        provider = new MetricsProviderImpl();
        Field field = MetricsProviderImpl.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        field.set(provider, httpClient);
        ReflectionTestUtils.setField(provider, "rmqConfigure", rmqConfigure);
        ReflectionTestUtils.setField(provider, "datasourceUrl", BASE_URL);
        ReflectionTestUtils.setField(provider, "authType", "none");
    }

    @After
    public void tearDown() {
        System.clearProperty("rocketmq.config.metrics.datasource.url");
        System.clearProperty("rocketmq.config.datasource.url");
        System.clearProperty("rocketmq.config.metrics.datasource.auth.type");
        System.clearProperty("rocketmq.config.metrics.datasource.username");
        System.clearProperty("rocketmq.config.metrics.datasource.password");
        System.clearProperty("rocketmq.config.metrics.datasource.bearer.token");
        // executeRawGet sets the interrupt flag on IO errors; clear it to avoid test pollution
        Thread.interrupted();
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int code, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(code);
        when(response.body()).thenReturn(body);
        return response;
    }

    @SuppressWarnings("unchecked")
    private void stubResponse(int code, String body) throws Exception {
        // build the response mock BEFORE when() to avoid nested unfinished stubbing
        HttpResponse<String> stubbed = response(code, body);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(stubbed);
    }

    // ==================== refresh / config resolution ====================

    @Test
    public void testRefreshResolvesFromSystemProperties() {
        System.setProperty("rocketmq.config.metrics.datasource.url", "http://prom:9091/");
        System.setProperty("rocketmq.config.metrics.datasource.auth.type", "BASIC");
        System.setProperty("rocketmq.config.metrics.datasource.username", "user");
        System.setProperty("rocketmq.config.metrics.datasource.password", "pass");

        provider.refresh();

        assertEquals("http://prom:9091", ReflectionTestUtils.getField(provider, "datasourceUrl"));
        assertEquals("basic", ReflectionTestUtils.getField(provider, "authType"));
        assertEquals("user", ReflectionTestUtils.getField(provider, "username"));
        assertEquals("pass", ReflectionTestUtils.getField(provider, "password"));
    }

    @Test
    public void testRefreshWithoutConfigLeavesUrlNull() {
        provider.refresh();
        assertEquals(null, ReflectionTestUtils.getField(provider, "datasourceUrl"));
    }

    @Test
    public void testQueryInstantWithoutDataSourceThrows() {
        ReflectionTestUtils.setField(provider, "datasourceUrl", null);
        try {
            provider.queryInstant("up", System.currentTimeMillis(), 15);
            fail("Expected ServiceException");
        } catch (ServiceException e) {
            assertEquals(500, e.getCode());
            assertTrue(e.getMessage().contains("not configured"));
        }
    }

    // ==================== queryInstant / queryRange ====================

    @Test
    @SuppressWarnings("unchecked")
    public void testQueryInstantSuccess() throws Exception {
        stubResponse(200, "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[]}}");

        Object result = provider.queryInstant("up{job=\"rocketmq\"}", 1720000000000L, 15);
        assertTrue(result instanceof Map);
        assertEquals("success", ((Map<String, Object>) result).get("status"));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertTrue(captor.getValue().uri().toString().contains("/api/v1/query?"));
        assertFalse(captor.getValue().headers().firstValue("Authorization").isPresent());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testQueryRangeSuccess() throws Exception {
        stubResponse(200, "{\"status\":\"success\",\"data\":{\"resultType\":\"matrix\",\"result\":[]}}");

        Object result = provider.queryRange("rate(rocketmq_broker_sendTPS[5m])",
            1720000000000L, 1720000300000L, 15);
        assertTrue(result instanceof Map);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        String uri = captor.getValue().uri().toString();
        assertTrue(uri.contains("/api/v1/query_range?"));
        assertTrue(uri.contains("start=1720000000000"));
        assertTrue(uri.contains("end=1720000300000"));
    }

    @Test
    public void testQueryInstantHttpErrorThrowsServiceException() throws Exception {
        stubResponse(500, "internal error");
        try {
            provider.queryInstant("up", 0L, 15);
            fail("Expected ServiceException");
        } catch (ServiceException e) {
            assertEquals(500, e.getCode());
            assertTrue(e.getMessage().contains("HTTP 500"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testQueryInstantIoErrorThrows502() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new IOException("connection reset"));
        try {
            provider.queryInstant("up", 0L, 15);
            fail("Expected ServiceException");
        } catch (ServiceException e) {
            assertEquals(502, e.getCode());
            assertTrue(e.getMessage().contains("Failed to reach data source"));
        }
    }

    // ==================== auth headers ====================

    @Test
    @SuppressWarnings("unchecked")
    public void testBasicAuthHeaderApplied() throws Exception {
        ReflectionTestUtils.setField(provider, "authType", "basic");
        ReflectionTestUtils.setField(provider, "username", "admin");
        ReflectionTestUtils.setField(provider, "password", "secret");
        stubResponse(200, "{\"status\":\"success\"}");

        provider.queryInstant("up", 0L, 15);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        String auth = captor.getValue().headers().firstValue("Authorization").orElse("");
        assertTrue(auth.startsWith("Basic "));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBearerAuthHeaderApplied() throws Exception {
        ReflectionTestUtils.setField(provider, "authType", "bearer");
        ReflectionTestUtils.setField(provider, "bearerToken", "token-123");
        stubResponse(200, "{\"status\":\"success\"}");

        provider.queryInstant("up", 0L, 15);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("Bearer token-123",
            captor.getValue().headers().firstValue("Authorization").orElse(""));
    }

    // ==================== label values / metric families ====================

    @Test
    public void testQueryLabelValues() throws Exception {
        stubResponse(200, "{\"values\":[\"broker-a\",\"broker-b\"]}");

        List<String> values = provider.queryLabelValues("rocketmq_broker_sendTPS", "broker");
        assertEquals(2, values.size());
        assertEquals("broker-a", values.get(0));
    }

    @Test
    public void testQueryLabelValuesNonMapResponse() throws Exception {
        stubResponse(200, "[\"orphan\"]");

        List<String> values = provider.queryLabelValues("metric", "label");
        assertTrue(values.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testListMetricFamiliesFiltersAndCaches() throws Exception {
        stubResponse(200, "{\"status\":\"success\",\"data\":["
            + "{\"name\":\"rocketmq_broker_sendTPS\"},"
            + "{\"name\":\"up\"},"
            + "{\"name\":\"rocketmq_broker_sendTPS\"},"
            + "{\"name\":\"rocketmq_topic_putNums\"}]}");

        List<String> families = provider.listMetricFamilies();
        assertEquals(2, families.size());
        assertTrue(families.contains("rocketmq_broker_sendTPS"));
        assertTrue(families.contains("rocketmq_topic_putNums"));

        // second call served from cache
        List<String> cached = provider.listMetricFamilies();
        assertEquals(families, cached);
        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    public void testListMetricFamiliesNonListData() throws Exception {
        stubResponse(200, "{\"status\":\"success\",\"data\":\"oops\"}");

        List<String> families = provider.listMetricFamilies();
        assertTrue(families.isEmpty());
    }

    // ==================== health check ====================

    @Test
    public void testHealthCheckUrlNotConfigured() {
        ReflectionTestUtils.setField(provider, "datasourceUrl", null);

        MetricsHealthResult result = provider.healthCheck();
        assertFalse(result.isConnected());
        assertEquals("Data source URL not configured.", result.getStatusMessage());
    }

    @SuppressWarnings("unchecked")
    private void stubDispatchByUri(String queryBody, String labelsBody, String targetsBody) throws Exception {
        // pre-build response mocks; stubbing inside thenAnswer is unsafe
        HttpResponse<String> queryResponse = response(200, queryBody);
        HttpResponse<String> labelsResponse = response(200, labelsBody);
        HttpResponse<String> targetsResponse = response(200, targetsBody);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenAnswer(invocation -> {
                HttpRequest request = invocation.getArgument(0);
                String path = request.uri().getPath();
                if (path.endsWith("/api/v1/query")) {
                    return queryResponse;
                } else if (path.endsWith("/api/v1/labels")) {
                    return labelsResponse;
                } else {
                    return targetsResponse;
                }
            });
    }

    @Test
    public void testHealthCheckHealthy() throws Exception {
        stubDispatchByUri(
            "{\"status\":\"success\"}",
            "{\"status\":\"success\",\"data\":["
                + "{\"name\":\"rocketmq_remoting_latency\"},"
                + "{\"name\":\"rocketmq_broker_sendTPS\"},"
                + "{\"name\":\"rocketmq_proxy_qps\"},"
                + "{\"name\":\"rocketmq_client_connected\"},"
                + "{\"name\":\"rocketmq_topic_putNums\"},"
                + "{\"name\":\"rocketmq_group_diff\"}]}",
            "{\"status\":\"success\",\"data\":{\"activeTargets\":["
                + "{\"__name__\":\"rocketmq_group_consumeTotal\"}]}}");

        MetricsHealthResult result = provider.healthCheck();
        assertTrue(result.isConnected());
        assertTrue(result.getMissingMetricFamilies().isEmpty());
        assertTrue(result.getStatusMessage().startsWith("Healthy"));
        assertEquals(7, result.getAvailableMetricFamilies().size());
    }

    @Test
    public void testHealthCheckWithMissingFamilies() throws Exception {
        stubDispatchByUri(
            "{\"status\":\"success\"}",
            "{\"status\":\"success\",\"data\":[{\"name\":\"rocketmq_broker_sendTPS\"}]}",
            "{\"status\":\"success\",\"data\":{}}");

        MetricsHealthResult result = provider.healthCheck();
        assertTrue(result.isConnected());
        assertEquals(5, result.getMissingMetricFamilies().size());
        assertTrue(result.getStatusMessage().contains("missing"));
    }

    @Test
    public void testHealthCheckDataSourceError() throws Exception {
        stubResponse(200, "{\"status\":\"error\",\"errorText\":\"query failed\"}");

        MetricsHealthResult result = provider.healthCheck();
        assertFalse(result.isConnected());
        assertTrue(result.getStatusMessage().contains("Data source returned error"));
        assertTrue(result.getStatusMessage().contains("query failed"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testHealthCheckConnectionFailed() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new IOException("refused"));

        MetricsHealthResult result = provider.healthCheck();
        assertFalse(result.isConnected());
        assertTrue(result.getStatusMessage().contains("Connection failed"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testHealthCheckUnexpectedError() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenThrow(new IllegalStateException("weird"));

        MetricsHealthResult result = provider.healthCheck();
        assertFalse(result.isConnected());
        assertTrue(result.getStatusMessage().contains("Health check error"));
    }
}
