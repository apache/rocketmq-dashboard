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
package com.rocketmq.studio.cluster.metrics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock
    private MetricsSource metricsSource;

    @InjectMocks
    private MetricsService metricsService;

    @Test
    void queryShouldReturnMetricData() {
        MetricQueryDTO query = MetricQueryDTO.builder()
                .metric("cpu_usage")
                .start(1700000000L)
                .end(1700003600L)
                .step("1m")
                .build();
        List<MetricDataVO.MetricSampleVO> values = List.of(
                sample(1700000000L, "45.5"),
                sample(1700000060L, "52"),
                sample(1700000120L, "48")
        );
        MetricDataVO data = metricData("cpu_usage", values);
        when(metricsSource.query(query)).thenReturn(data);

        MetricDataVO result = metricsService.query(query);

        assertThat(result.getSeries()).hasSize(1);
        assertThat(result.getSeries().get(0).getLabels()).containsEntry("__name__", "cpu_usage");
        assertThat(result.getSeries().get(0).getValues()).hasSize(3);
        assertThat(result.getSeries().get(0).getValues().get(0).getTimestamp()).isEqualTo(1700000000D);
        assertThat(result.getSeries().get(0).getValues().get(0).getValue()).isEqualTo("45.5");
        verify(metricsSource).query(query);
    }

    @Test
    void queryShouldReturnEmptyValuesWhenNoData() {
        MetricQueryDTO query = MetricQueryDTO.builder()
                .metric("disk_io")
                .start(1700000000L)
                .end(1700003600L)
                .step("5m")
                .build();
        MetricDataVO data = emptyMetricData();
        when(metricsSource.query(query)).thenReturn(data);

        MetricDataVO result = metricsService.query(query);

        assertThat(result.getSeries()).isEmpty();
    }

    @Test
    void queryShouldPassQueryDirectlyToSource() {
        MetricQueryDTO query = MetricQueryDTO.builder()
                .metric("tps")
                .start(1700000000L)
                .end(1700086400L)
                .step("1h")
                .build();
        MetricDataVO data = emptyMetricData();
        when(metricsSource.query(any(MetricQueryDTO.class))).thenReturn(data);

        metricsService.query(query);

        verify(metricsSource).query(query);
    }

    @Test
    void queryShouldHandleVariousStepSizes() {
        MetricQueryDTO query15s = MetricQueryDTO.builder().metric("cpu").start(1L).end(2L).step("15s").build();
        MetricQueryDTO query1h = MetricQueryDTO.builder().metric("cpu").start(1L).end(2L).step("1h").build();
        MetricQueryDTO queryCombined = MetricQueryDTO.builder().metric("cpu").start(1L).end(7_201L)
                .step("1h30m").build();
        MetricQueryDTO queryNumeric = MetricQueryDTO.builder().metric("cpu").start(1L).end(2L)
                .step("0.5").build();
        MetricDataVO data = emptyMetricData();
        when(metricsSource.query(any(MetricQueryDTO.class))).thenReturn(data);

        MetricDataVO result15s = metricsService.query(query15s);
        MetricDataVO result1h = metricsService.query(query1h);
        MetricDataVO resultCombined = metricsService.query(queryCombined);
        MetricDataVO resultNumeric = metricsService.query(queryNumeric);

        assertThat(result15s).isNotNull();
        assertThat(result1h).isNotNull();
        assertThat(resultCombined).isNotNull();
        assertThat(resultNumeric).isNotNull();
        verify(metricsSource).query(query15s);
        verify(metricsSource).query(query1h);
        verify(metricsSource).query(queryCombined);
        verify(metricsSource).query(queryNumeric);
    }

    @Test
    void queryShouldReturnMultipleDataPoints() {
        MetricQueryDTO query = MetricQueryDTO.builder()
                .metric("memory_usage")
                .start(1700000000L)
                .end(1700003600L)
                .step("1m")
                .build();
        List<MetricDataVO.MetricSampleVO> values = List.of(
                sample(1700000000L, "72"),
                sample(1700000060L, "73"),
                sample(1700000120L, "71"),
                sample(1700000180L, "74"),
                sample(1700000240L, "75")
        );
        MetricDataVO data = metricData("memory_usage", values);
        when(metricsSource.query(query)).thenReturn(data);

        MetricDataVO result = metricsService.query(query);

        assertThat(result.getSeries().get(0).getValues()).hasSize(5);
    }

    @Test
    void queryShouldPreserveMetricName() {
        String[] metrics = {"rocketmq_tps", "rocketmq_latency_p99", "broker_disk_usage", "consumer_lag"};
        for (String metricName : metrics) {
            MetricQueryDTO query = MetricQueryDTO.builder().metric(metricName).start(1L).end(2L).step("1m").build();
            MetricDataVO data = metricData(metricName, Collections.emptyList());
            when(metricsSource.query(query)).thenReturn(data);

            MetricDataVO result = metricsService.query(query);

            assertThat(result.getSeries().get(0).getLabels()).containsEntry("__name__", metricName);
        }
    }

    @Test
    void queryShouldRejectInvalidWindow() {
        MetricQueryDTO query = MetricQueryDTO.builder()
                .metric("rocketmq_messages_in_total")
                .start(1700003600L)
                .end(1700000000L)
                .step("1m")
                .build();

        assertBadRequest(query, "Metric query end must be later than start");
        verifyNoInteractions(metricsSource);
    }

    @Test
    void queryShouldRejectOversizedWindow() {
        MetricQueryDTO query = MetricQueryDTO.builder()
                .metric("rocketmq_messages_in_total")
                .start(1700000000L)
                .end(1702678401L)
                .step("1h")
                .build();

        assertBadRequest(query, "Metric query range must not exceed 31 days");
        verifyNoInteractions(metricsSource);
    }

    @Test
    void queryShouldRejectInvalidStep() {
        MetricQueryDTO query = MetricQueryDTO.builder()
                .metric("rocketmq_messages_in_total")
                .start(1700000000L)
                .end(1700003600L)
                .step("five minutes")
                .build();

        assertBadRequest(query, "Metric query step is invalid");
        verifyNoInteractions(metricsSource);
    }

    @Test
    void queryShouldRejectTooManySamples() {
        MetricQueryDTO query = MetricQueryDTO.builder()
                .metric("rocketmq_messages_in_total")
                .start(1700000000L)
                .end(1700011001L)
                .step("1s")
                .build();

        assertBadRequest(query, "Metric query returns too many samples; increase step or reduce range");
        verifyNoInteractions(metricsSource);
    }

    private MetricDataVO emptyMetricData() {
        return MetricDataVO.builder()
                .resultType("matrix")
                .series(Collections.emptyList())
                .warnings(Collections.emptyList())
                .build();
    }

    private MetricDataVO metricData(String metricName, List<MetricDataVO.MetricSampleVO> values) {
        MetricDataVO.MetricSeriesVO series = MetricDataVO.MetricSeriesVO.builder()
                .labels(Map.of("__name__", metricName))
                .values(values)
                .build();
        return MetricDataVO.builder()
                .resultType("matrix")
                .series(List.of(series))
                .warnings(Collections.emptyList())
                .build();
    }

    private MetricDataVO.MetricSampleVO sample(double timestamp, String value) {
        return MetricDataVO.MetricSampleVO.builder()
                .timestamp(timestamp)
                .value(value)
                .build();
    }

    private void assertBadRequest(MetricQueryDTO query, String message) {
        assertThatExceptionOfType(PrometheusException.class)
                .isThrownBy(() -> metricsService.query(query))
                .satisfies(exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(400);
                    assertThat(exception.getMessage()).isEqualTo(message);
                });
    }
}
