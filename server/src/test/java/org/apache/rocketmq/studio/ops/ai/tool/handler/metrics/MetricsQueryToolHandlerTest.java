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
package org.apache.rocketmq.studio.ops.ai.tool.handler.metrics;

import org.apache.rocketmq.studio.cluster.metrics.MetricDataVO;
import org.apache.rocketmq.studio.cluster.metrics.MetricQueryDTO;
import org.apache.rocketmq.studio.cluster.metrics.MetricsService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.ops.MetricsQueryInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

@ExtendWith(MockitoExtension.class)
class MetricsQueryToolHandlerTest {

    @Mock
    private MetricsService metricsService;

    @InjectMocks
    private MetricsQueryToolHandler handler;

    @Test
    void executeShouldQueryAndProjectSeries() {
        assertThat(handler.name()).isEqualTo("rmq.metrics.query");
        MetricDataVO.MetricSampleVO sample = MetricDataVO.MetricSampleVO.builder()
                .timestamp(1700000000.0)
                .value("100")
                .build();
        MetricDataVO.MetricSeriesVO series = MetricDataVO.MetricSeriesVO.builder()
                .labels(Map.of("cluster", "cluster-1", "topic", "TopicA"))
                .values(List.of(sample))
                .build();
        MetricDataVO.MetricSeriesVO otherBrokerCluster = MetricDataVO.MetricSeriesVO.builder()
                .labels(Map.of("cluster", "cluster-2", "topic", "TopicB"))
                .values(List.of(sample))
                .build();
        MetricDataVO data = MetricDataVO.builder()
                .resultType("matrix")
                .series(List.of(series, otherBrokerCluster))
                .warnings(List.of())
                .build();
        when(metricsService.queryInstance(eq("instance-a"), any(MetricQueryDTO.class)))
                .thenReturn(data);

        MetricDataVO result = handler.execute(new MetricsQueryInput(
                "cluster-1", "rocketmq_topic_tps", null, null,
                1700000000L, 1700000060L, "30s"), context("instance-a"));

        assertThat(result.getResultType()).isEqualTo("matrix");
        assertThat(result.getSeries()).extracting(MetricDataVO.MetricSeriesVO::getLabels)
                .containsExactly(series.getLabels(), otherBrokerCluster.getLabels());
        MetricDataVO.MetricSeriesVO seriesItem = result.getSeries().getFirst();
        assertThat(seriesItem.getLabels())
                .isEqualTo(Map.of("cluster", "cluster-1", "topic", "TopicA"));
        assertThat(seriesItem.getValues()).containsExactly(sample);
        assertThat(seriesItem.getValues().getFirst().getValue()).isEqualTo("100");

        ArgumentCaptor<MetricQueryDTO> captor = ArgumentCaptor.forClass(MetricQueryDTO.class);
        verify(metricsService).queryInstance(eq("instance-a"), captor.capture());
        MetricQueryDTO captured = captor.getValue();
        assertThat(captured.getMetric()).isEqualTo("rocketmq_topic_tps");
        assertThat(captured.getStart()).isEqualTo(1700000000L);
        assertThat(captured.getStep()).isEqualTo("30s");
    }
}
