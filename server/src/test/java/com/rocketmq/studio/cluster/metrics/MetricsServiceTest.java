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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
        List<long[]> values = Arrays.asList(
                new long[]{1700000000L, 45},
                new long[]{1700000060L, 52},
                new long[]{1700000120L, 48}
        );
        MetricDataVO data = MetricDataVO.builder().metric("cpu_usage").values(values).build();
        when(metricsSource.query(query)).thenReturn(data);

        MetricDataVO result = metricsService.query(query);

        assertThat(result.getMetric()).isEqualTo("cpu_usage");
        assertThat(result.getValues()).hasSize(3);
        assertThat(result.getValues().get(0)[0]).isEqualTo(1700000000L);
        assertThat(result.getValues().get(0)[1]).isEqualTo(45);
        assertThat(result.getValues().get(1)[1]).isEqualTo(52);
        assertThat(result.getValues().get(2)[1]).isEqualTo(48);
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
        MetricDataVO data = MetricDataVO.builder().metric("disk_io").values(Collections.emptyList()).build();
        when(metricsSource.query(query)).thenReturn(data);

        MetricDataVO result = metricsService.query(query);

        assertThat(result.getMetric()).isEqualTo("disk_io");
        assertThat(result.getValues()).isEmpty();
    }

    @Test
    void queryShouldPassQueryDirectlyToSource() {
        MetricQueryDTO query = MetricQueryDTO.builder()
                .metric("tps")
                .start(1700000000L)
                .end(1700086400L)
                .step("1h")
                .build();
        MetricDataVO data = MetricDataVO.builder().metric("tps").values(Collections.emptyList()).build();
        when(metricsSource.query(any(MetricQueryDTO.class))).thenReturn(data);

        metricsService.query(query);

        verify(metricsSource).query(query);
    }

    @Test
    void queryShouldHandleVariousStepSizes() {
        MetricQueryDTO query15s = MetricQueryDTO.builder().metric("cpu").start(1L).end(2L).step("15s").build();
        MetricQueryDTO query1h = MetricQueryDTO.builder().metric("cpu").start(1L).end(2L).step("1h").build();
        MetricDataVO data = MetricDataVO.builder().metric("cpu").values(Collections.emptyList()).build();
        when(metricsSource.query(any(MetricQueryDTO.class))).thenReturn(data);

        MetricDataVO result15s = metricsService.query(query15s);
        MetricDataVO result1h = metricsService.query(query1h);

        assertThat(result15s).isNotNull();
        assertThat(result1h).isNotNull();
        verify(metricsSource).query(query15s);
        verify(metricsSource).query(query1h);
    }

    @Test
    void queryShouldReturnMultipleDataPoints() {
        MetricQueryDTO query = MetricQueryDTO.builder()
                .metric("memory_usage")
                .start(1700000000L)
                .end(1700003600L)
                .step("1m")
                .build();
        List<long[]> values = Arrays.asList(
                new long[]{1700000000L, 72},
                new long[]{1700000060L, 73},
                new long[]{1700000120L, 71},
                new long[]{1700000180L, 74},
                new long[]{1700000240L, 75}
        );
        MetricDataVO data = MetricDataVO.builder().metric("memory_usage").values(values).build();
        when(metricsSource.query(query)).thenReturn(data);

        MetricDataVO result = metricsService.query(query);

        assertThat(result.getMetric()).isEqualTo("memory_usage");
        assertThat(result.getValues()).hasSize(5);
    }

    @Test
    void queryShouldPreserveMetricName() {
        String[] metrics = {"rocketmq_tps", "rocketmq_latency_p99", "broker_disk_usage", "consumer_lag"};
        for (String metricName : metrics) {
            MetricQueryDTO query = MetricQueryDTO.builder().metric(metricName).start(1L).end(2L).step("1m").build();
            MetricDataVO data = MetricDataVO.builder().metric(metricName).values(Collections.emptyList()).build();
            when(metricsSource.query(query)).thenReturn(data);

            MetricDataVO result = metricsService.query(query);

            assertThat(result.getMetric()).isEqualTo(metricName);
        }
    }
}
