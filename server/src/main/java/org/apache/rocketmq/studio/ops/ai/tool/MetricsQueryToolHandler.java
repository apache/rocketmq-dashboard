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

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.cluster.metrics.MetricDataVO;
import org.apache.rocketmq.studio.cluster.metrics.MetricQueryDTO;
import org.apache.rocketmq.studio.cluster.metrics.MetricsService;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MetricsQueryToolHandler implements ToolHandler {

    private static final String NAME = "rmq.metrics.query";

    private final MetricsService metricsService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Object execute(Map<String, Object> input) {
        MetricQueryDTO query = MetricQueryDTO.builder()
                .metric(trimToNull(input.get("metric")))
                .profileId(trimToNull(input.get("profileId")))
                .semanticMetric(trimToNull(input.get("semanticMetric")))
                .start(requiredLong(input.get("start"), "start"))
                .end(requiredLong(input.get("end"), "end"))
                .step(requiredString(input.get("step"), "step"))
                .build();
        return resultProjection(metricsService.query(query));
    }

    private static Map<String, Object> resultProjection(MetricDataVO data) {
        if (data == null) {
            throw new IllegalStateException("Metric query result is unavailable");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resultType", blankIfNull(data.getResultType()));
        result.put("series", series(data.getSeries()));
        result.put("warnings", data.getWarnings() == null ? List.of() : List.copyOf(data.getWarnings()));
        return result;
    }

    private static List<Map<String, Object>> series(List<MetricDataVO.MetricSeriesVO> series) {
        if (series == null) {
            return List.of();
        }
        return series.stream()
                .map(MetricsQueryToolHandler::seriesProjection)
                .toList();
    }

    private static Map<String, Object> seriesProjection(MetricDataVO.MetricSeriesVO series) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("labels", series.getLabels() == null ? Map.of() : Map.copyOf(series.getLabels()));
        result.put("values", samples(series.getValues()));
        result.put("histograms", histogramSamples(series.getHistograms()));
        return result;
    }

    private static List<Map<String, Object>> samples(List<MetricDataVO.MetricSampleVO> samples) {
        if (samples == null) {
            return List.of();
        }
        return samples.stream()
                .map(MetricsQueryToolHandler::sampleProjection)
                .toList();
    }

    private static Map<String, Object> sampleProjection(MetricDataVO.MetricSampleVO sample) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", sample.getTimestamp());
        result.put("value", blankIfNull(sample.getValue()));
        return result;
    }

    private static List<Map<String, Object>> histogramSamples(
            List<MetricDataVO.MetricHistogramSampleVO> samples) {
        if (samples == null) {
            return List.of();
        }
        return samples.stream()
                .map(MetricsQueryToolHandler::histogramSampleProjection)
                .toList();
    }

    private static Map<String, Object> histogramSampleProjection(
            MetricDataVO.MetricHistogramSampleVO sample) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", sample.getTimestamp());
        JsonNode histogram = sample.getHistogram();
        result.put("histogram", histogram == null ? Map.of() : histogram);
        return result;
    }

    private static String requiredString(Object value, String field) {
        String text = trimToNull(value);
        if (text == null) {
            throw new BusinessException(400, "Metric query " + field + " is required");
        }
        return text;
    }

    private static String trimToNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static long requiredLong(Object value, String field) {
        if (value == null) {
            throw new BusinessException(400, "Metric query " + field + " is required");
        }
        if (value instanceof BigInteger bigInteger) {
            try {
                return bigInteger.longValueExact();
            } catch (ArithmeticException ex) {
                throw new BusinessException(400, "Metric query " + field + " must fit in a 64-bit integer");
            }
        }
        if (value instanceof Number number) {
            if (number instanceof Double || number instanceof Float) {
                double doubleValue = number.doubleValue();
                if (!Double.isFinite(doubleValue)
                        || doubleValue > Long.MAX_VALUE
                        || doubleValue < Long.MIN_VALUE
                        || doubleValue % 1 != 0) {
                    throw new BusinessException(400, "Metric query " + field + " must be an integer");
                }
            }
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(400, "Metric query " + field + " must be an integer");
        }
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
