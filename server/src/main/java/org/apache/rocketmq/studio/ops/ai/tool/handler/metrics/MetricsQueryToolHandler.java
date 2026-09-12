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
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MetricsQueryToolHandler implements ToolHandler<MetricsQueryInput, MetricDataVO> {

    private final MetricsService metricsService;

    @Override
    public String name() {
        return "rmq.metrics.query";
    }

    @Override
    public Class<MetricsQueryInput> inputType() {
        return MetricsQueryInput.class;
    }

    @Override
    public MetricDataVO execute(
            MetricsQueryInput input, ToolExecutionContext context) {
        MetricQueryDTO query = MetricQueryDTO.builder()
                .metric(input.metric())
                .profileId(input.profileId())
                .semanticMetric(input.semanticMetric())
                .start(input.start())
                .end(input.end())
                .step(input.step())
                .build();
        MetricDataVO data = metricsService.queryInstance(context.cluster(), query);
        List<MetricDataVO.MetricSeriesVO> filteredSeries = data.getSeries() != null
                ? data.getSeries().stream()
                .map(MetricsQueryToolHandler::normalizeSeries)
                .toList()
                : List.of();
        return MetricDataVO.builder()
                .resultType(data.getResultType())
                .series(filteredSeries)
                .warnings(data.getWarnings() != null ? data.getWarnings() : List.of())
                .build();
    }

    private static MetricDataVO.MetricSeriesVO normalizeSeries(
            MetricDataVO.MetricSeriesVO series) {
        return MetricDataVO.MetricSeriesVO.builder()
                .labels(series.getLabels() != null ? series.getLabels() : Map.of())
                .values(series.getValues() != null ? series.getValues() : List.of())
                .histograms(series.getHistograms())
                .build();
    }

}
