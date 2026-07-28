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
package org.apache.rocketmq.dashboard.adapter;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PrometheusMetricsAdapterTest {

    private PrometheusMetricsAdapter adapter;

    @Before
    public void setUp() {
        adapter = new PrometheusMetricsAdapter();
    }

    @Test
    public void testToPrometheusFormatWithNumbers() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("brokerCount", 2);
        metrics.put("tps", 100.5);

        String output = adapter.toPrometheusFormat(metrics);
        assertTrue(output.contains("brokercount 2\n"));
        assertTrue(output.contains("tps 100.5\n"));
    }

    @Test
    public void testToPrometheusFormatSanitizesMetricNames() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("broker.count-total", 1);

        String output = adapter.toPrometheusFormat(metrics);
        assertTrue(output.contains("broker_count_total 1"));
    }

    @Test
    public void testToPrometheusFormatWithNestedMapBecomesLabels() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("broker", "broker-a");

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("brokerUp", nested);

        String output = adapter.toPrometheusFormat(metrics);
        assertTrue(output.contains("brokerup{broker=\"broker-a\"} 1"));
    }

    @Test
    public void testToPrometheusFormatWithNonNumericValue() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("status", "running");

        String output = adapter.toPrometheusFormat(metrics);
        assertTrue(output.contains("status running"));
    }

    @Test
    public void testToPrometheusFormatEmptyAndNull() {
        assertEquals("", adapter.toPrometheusFormat((Map<String, Object>) null));
        assertEquals("", adapter.toPrometheusFormat(new LinkedHashMap<>()));
    }

    @Test
    public void testToPrometheusFormatListJoinsEntries() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("a", 1);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("b", 2);

        String output = adapter.toPrometheusFormat(Arrays.asList(first, second));
        assertTrue(output.contains("a 1"));
        assertTrue(output.contains("b 2"));
    }

    @Test
    public void testToPrometheusFormatListEmptyAndNull() {
        assertEquals("", adapter.toPrometheusFormat((List<Map<String, Object>>) null));
        assertEquals("", adapter.toPrometheusFormat(Arrays.asList()));
    }

    @Test
    public void testGenerateMetricFamily() {
        String family = adapter.generateMetricFamily("rocketmq_tps", "gauge", "Messages per second");
        assertTrue(family.contains("# HELP rocketmq_tps Messages per second"));
        assertTrue(family.contains("# TYPE rocketmq_tps gauge"));
    }

    @Test
    public void testGenerateFullMetricsExport() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("tps", 10);

        String export = adapter.generateFullMetricsExport(metrics);
        assertTrue(export.startsWith("# RocketMQ Dashboard Metrics"));
        assertTrue(export.contains("# Generated at: "));
        assertTrue(export.contains("tps 10"));
    }
}
