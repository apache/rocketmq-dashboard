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
package org.apache.rocketmq.studio.ops.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.cluster.metrics.MetricDataVO;
import org.apache.rocketmq.studio.cluster.metrics.MetricQueryDTO;
import org.apache.rocketmq.studio.cluster.metrics.MetricsService;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsQueryToolHandlerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private MetricsService metricsService;

    @InjectMocks
    private MetricsQueryToolHandler handler;

    @Test
    @SuppressWarnings("unchecked")
    void executeShouldDelegateRawPromqlQueryAndProjectSamples() {
        when(metricsService.query(any(MetricQueryDTO.class))).thenReturn(metricData());

        Object result = handler.execute(Map.of(
                "cluster", "cluster-v5",
                "metric", "  sum(rate(rocketmq_messages_in_total[1m])) by (cluster)  ",
                "start", 1700000000L,
                "end", 1700000300L,
                "step", " 30s "));

        ArgumentCaptor<MetricQueryDTO> captor = ArgumentCaptor.forClass(MetricQueryDTO.class);
        verify(metricsService).query(captor.capture());
        assertThat(captor.getValue())
                .extracting(MetricQueryDTO::getMetric, MetricQueryDTO::getProfileId,
                        MetricQueryDTO::getSemanticMetric, MetricQueryDTO::getStart,
                        MetricQueryDTO::getEnd, MetricQueryDTO::getStep)
                .containsExactly("sum(rate(rocketmq_messages_in_total[1m])) by (cluster)",
                        null, null, 1700000000L, 1700000300L, "30s");

        Map<String, Object> output = (Map<String, Object>) result;
        assertThat(output).containsEntry("resultType", "matrix");
        assertThat(output).containsEntry("warnings", List.of("partial response"));
        List<Map<String, Object>> series = (List<Map<String, Object>>) output.get("series");
        assertThat(series).hasSize(1);
        assertThat(series.get(0)).containsEntry("labels", Map.of("cluster", "cluster-v5"));
        assertThat((List<?>) series.get(0).get("values")).hasSize(1);
        assertThat(series.get(0)).containsEntry("histograms", List.of());
    }

    @Test
    void executeShouldDelegateSemanticMetricSelection() {
        when(metricsService.query(any(MetricQueryDTO.class))).thenReturn(emptyMetricData());

        handler.execute(Map.of(
                "cluster", "cluster-v5",
                "profileId", " rocketmq5-native ",
                "semanticMetric", " consumer_lag_messages ",
                "start", 1700000000L,
                "end", 1700000300L,
                "step", "30s"));

        ArgumentCaptor<MetricQueryDTO> captor = ArgumentCaptor.forClass(MetricQueryDTO.class);
        verify(metricsService).query(captor.capture());
        assertThat(captor.getValue())
                .extracting(MetricQueryDTO::getMetric, MetricQueryDTO::getProfileId,
                        MetricQueryDTO::getSemanticMetric)
                .containsExactly(null, "rocketmq5-native", "consumer_lag_messages");
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeShouldProjectHistogramSamples() {
        MetricDataVO.MetricHistogramSampleVO histogramSample = MetricDataVO.MetricHistogramSampleVO.builder()
                .timestamp(1700000000D)
                .histogram(OBJECT_MAPPER.createObjectNode().put("count", "5"))
                .build();
        MetricDataVO.MetricSeriesVO series = MetricDataVO.MetricSeriesVO.builder()
                .labels(Map.of("cluster", "cluster-v5"))
                .histograms(List.of(histogramSample))
                .build();
        when(metricsService.query(any(MetricQueryDTO.class))).thenReturn(MetricDataVO.builder()
                .resultType("histogram")
                .series(List.of(series))
                .warnings(null)
                .build());

        Object result = handler.execute(Map.of(
                "cluster", "cluster-v5",
                "metric", "rocketmq_histogram",
                "start", 1700000000L,
                "end", 1700000300L,
                "step", "30s"));

        Map<String, Object> output = (Map<String, Object>) result;
        assertThat(output).containsEntry("warnings", List.of());
        List<Map<String, Object>> outputSeries = (List<Map<String, Object>>) output.get("series");
        assertThat(outputSeries.get(0)).containsEntry("values", List.of());
        List<Map<String, Object>> histograms = (List<Map<String, Object>>) outputSeries.get(0).get("histograms");
        assertThat(histograms).hasSize(1);
        assertThat(histograms.get(0).get("histogram").toString()).contains("\"count\":\"5\"");
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeShouldReturnEmptySeriesWhenMetricDataHasNullLists() {
        when(metricsService.query(any(MetricQueryDTO.class))).thenReturn(MetricDataVO.builder()
                .resultType(null)
                .series(null)
                .warnings(null)
                .build());

        Object result = handler.execute(Map.of(
                "cluster", "cluster-v5",
                "metric", "up",
                "start", 1700000000L,
                "end", 1700000300L,
                "step", "30s"));

        Map<String, Object> output = (Map<String, Object>) result;
        assertThat(output).containsEntry("resultType", "");
        assertThat(output).containsEntry("series", List.of());
        assertThat(output).containsEntry("warnings", List.of());
    }

    @Test
    void executeShouldRejectMissingStart() {
        assertThatThrownBy(() -> handler.execute(Map.of(
                "cluster", "cluster-v5",
                "metric", "up",
                "end", 1700000300L,
                "step", "30s")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("start is required");
    }

    @Test
    void executeShouldRejectOutOfRangeTimestamp() {
        BigInteger overflow = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);

        assertThatThrownBy(() -> handler.execute(Map.of(
                "cluster", "cluster-v5",
                "metric", "up",
                "start", overflow,
                "end", 1700000300L,
                "step", "30s")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("64-bit integer");
    }

    private static MetricDataVO metricData() {
        MetricDataVO.MetricSeriesVO series = MetricDataVO.MetricSeriesVO.builder()
                .labels(Map.of("cluster", "cluster-v5"))
                .values(List.of(MetricDataVO.MetricSampleVO.builder()
                        .timestamp(1700000000D)
                        .value("42")
                        .build()))
                .build();
        return MetricDataVO.builder()
                .resultType("matrix")
                .series(List.of(series))
                .warnings(List.of("partial response"))
                .build();
    }

    private static MetricDataVO emptyMetricData() {
        return MetricDataVO.builder()
                .resultType("matrix")
                .series(List.of())
                .warnings(List.of())
                .build();
    }
}
