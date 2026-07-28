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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.rocketmq.dashboard.exception.ServiceException;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PrometheusMetricsQueryClientTest {

    private PrometheusMetricsQueryClient client;

    @Before
    public void setUp() {
        client = new PrometheusMetricsQueryClient();
    }

    // ---- datasource resolution -----------------------------------------------

    @Test
    public void testQueryWithoutDatasourceThrows() {
        try {
            client.queryInstant("up", null);
            fail("Expected ServiceException");
        } catch (ServiceException e) {
            assertTrue(e.getMessage().contains("No Prometheus datasource configured"));
        }
    }

    @Test
    public void testIsDatasourceAvailableFalseWhenUnconfigured() {
        assertFalse(client.isDatasourceAvailable(null));
    }

    // ---- Prometheus JSON response parsing --------------------------------------

    private Map<String, Object> vectorResponse(List<Map<String, Object>> result) {
        Map<String, Object> data = new HashMap<>();
        data.put("resultType", "vector");
        data.put("result", result);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("data", data);
        return response;
    }

    @Test
    public void testParseVectorResult() {
        Map<String, Object> sample = new HashMap<>();
        sample.put("metric", Collections.singletonMap("__name__", "up"));
        sample.put("value", Arrays.asList(1700000000, "1"));

        List<Map<String, Object>> result = client.parseVectorResult(
                vectorResponse(Collections.singletonList(sample)));
        assertEquals(1, result.size());
        assertEquals(sample, result.get(0));
    }

    @Test
    public void testParseResultRejectsNullResponse() {
        assertTrue(client.parseVectorResult(null).isEmpty());
    }

    @Test
    public void testParseResultRejectsErrorStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("error", "query parse error");
        assertTrue(client.parseVectorResult(response).isEmpty());
    }

    @Test
    public void testParseResultHandlesMissingData() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        assertTrue(client.parseVectorResult(response).isEmpty());
    }

    @Test
    public void testParseMatrixResultToleratesTypeMismatch() {
        // vector payload parsed with matrix expectation still returns the result list
        Map<String, Object> sample = new HashMap<>();
        sample.put("metric", Collections.emptyMap());
        sample.put("values", Collections.emptyList());
        List<Map<String, Object>> result = client.parseMatrixResult(
                vectorResponse(Collections.singletonList(sample)));
        assertEquals(1, result.size());
    }

    // ---- scalar value extraction ------------------------------------------------

    @Test
    public void testExtractScalarValues() {
        Map<String, Object> sample = new HashMap<>();
        Map<String, String> labels = new HashMap<>();
        labels.put("__name__", "rocketmq_broker_tps");
        labels.put("broker", "broker-a");
        sample.put("metric", labels);
        sample.put("value", Arrays.asList(1700000000, "42.5"));

        Map<String, Double> values = client.extractScalarValues(Collections.singletonList(sample));
        assertEquals(1, values.size());
        Map.Entry<String, Double> entry = values.entrySet().iterator().next();
        assertTrue(entry.getKey().contains("rocketmq_broker_tps"));
        assertTrue(entry.getKey().contains("broker-a"));
        assertEquals(42.5, entry.getValue(), 0.0001);
    }

    @Test
    public void testExtractScalarValuesUnparseableValueDefaultsToZero() {
        Map<String, Object> sample = new HashMap<>();
        sample.put("metric", Collections.singletonMap("__name__", "up"));
        sample.put("value", Arrays.asList(1700000000, "not-a-number"));

        Map<String, Double> values = client.extractScalarValues(Collections.singletonList(sample));
        assertEquals(Double.valueOf(0.0), values.get("up"));
    }

    @Test
    public void testExtractScalarValuesEmptyMetricLabelsIsUnknown() {
        Map<String, Object> sample = new HashMap<>();
        sample.put("metric", Collections.emptyMap());
        sample.put("value", Arrays.asList(1700000000, "1"));

        Map<String, Double> values = client.extractScalarValues(Collections.singletonList(sample));
        assertTrue(values.containsKey("unknown"));
    }

    @Test
    public void testExtractScalarValuesNullInput() {
        assertTrue(client.extractScalarValues(null).isEmpty());
    }

    // ---- OpenMetrics text parsing ----------------------------------------------

    @Test
    public void testParseOpenMetricsTextBasic() {
        String text = "# HELP up Target up status\n"
                + "# TYPE up gauge\n"
                + "up{job=\"rocketmq\",instance=\"127.0.0.1:9090\"} 1\n"
                + "process_open_fds 123 1700000000\n";

        List<Map<String, Object>> metrics = client.parseOpenMetricsText(text);
        assertEquals(2, metrics.size());

        Map<String, Object> first = metrics.get(0);
        assertEquals("up", first.get("name"));
        @SuppressWarnings("unchecked")
        Map<String, String> labels = (Map<String, String>) first.get("labels");
        assertEquals("rocketmq", labels.get("job"));
        assertEquals("127.0.0.1:9090", labels.get("instance"));
        assertEquals(1.0, (Double) first.get("value"), 0.0001);

        Map<String, Object> second = metrics.get(1);
        assertEquals("process_open_fds", second.get("name"));
        assertEquals(123.0, (Double) second.get("value"), 0.0001);
        assertEquals(Long.valueOf(1700000000L), second.get("timestamp"));
    }

    @Test
    public void testParseOpenMetricsTextSpecialValues() {
        String text = "a 1\nb +Inf\nc -Inf\nd NaN\n";
        List<Map<String, Object>> metrics = client.parseOpenMetricsText(text);
        assertEquals(4, metrics.size());
        assertEquals(Double.POSITIVE_INFINITY, (Double) metrics.get(1).get("value"), 0.0);
        assertEquals(Double.NEGATIVE_INFINITY, (Double) metrics.get(2).get("value"), 0.0);
        assertTrue(((Double) metrics.get(3).get("value")).isNaN());
    }

    @Test
    public void testParseOpenMetricsTextEscapedLabelValue() {
        String text = "metric{path=\"C:\\\\dir\"} 5\n";
        List<Map<String, Object>> metrics = client.parseOpenMetricsText(text);
        assertEquals(1, metrics.size());
        @SuppressWarnings("unchecked")
        Map<String, String> labels = (Map<String, String>) metrics.get(0).get("labels");
        assertEquals("C:\\dir", labels.get("path"));
    }

    @Test
    public void testParseOpenMetricsTextSkipsInvalidLines() {
        String text = "# comment only\nnot_a_valid_line\nvalid 1\n";
        List<Map<String, Object>> metrics = client.parseOpenMetricsText(text);
        assertEquals(1, metrics.size());
        assertEquals("valid", metrics.get(0).get("name"));
    }

    @Test
    public void testParseOpenMetricsTextNullAndEmpty() {
        assertTrue(client.parseOpenMetricsText(null).isEmpty());
        assertTrue(client.parseOpenMetricsText("").isEmpty());
    }

    // ---- query templates --------------------------------------------------------

    @Test
    public void testGetQueryTemplatesCoversAllCategories() {
        Map<String, Map<String, String>> templates = client.getQueryTemplates();
        assertTrue(templates.containsKey("cluster"));
        assertTrue(templates.containsKey("broker"));
        assertTrue(templates.containsKey("topic"));
        assertTrue(templates.containsKey("consumer"));
        assertTrue(templates.containsKey("system"));
        assertFalse(templates.get("broker").isEmpty());
    }

    // ---- datasource registration --------------------------------------------------

    @Test
    public void testRegisterAndRemoveDatasource() {
        client.registerDatasource("ds1", "http://prometheus:9090/", false);
        // The first registered datasource becomes the default even when isDefault=false,
        // so queries no longer fail with "not configured" (they fail on the HTTP call instead).
        try {
            client.queryInstant("up", "unknown-ds");
            fail("Expected ServiceException from unreachable datasource");
        } catch (ServiceException e) {
            assertFalse(e.getMessage().contains("No Prometheus datasource configured"));
        } finally {
            // executeQuery interrupts the thread on IO failure; clear the flag so
            // later tests on this worker thread are unaffected
            Thread.interrupted();
        }

        client.removeDatasource("ds1");
    }
}
