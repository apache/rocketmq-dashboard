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
package org.apache.rocketmq.studio.cluster.metrics;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MetricDataVOTest {

    @Test
    void builderDefaultsDescribeEmptyResult() {
        MetricDataVO vo = MetricDataVO.builder().build();

        assertNull(vo.getResultType());
        assertNull(vo.getSeries());
        assertNull(vo.getWarnings());
    }

    @Test
    void allArgsCarryResultWithNestedSeriesAndSamples() {
        MetricDataVO.MetricSampleVO sample = MetricDataVO.MetricSampleVO.builder()
            .timestamp(1784246400.0)
            .value("98.5")
            .build();
        MetricDataVO.MetricSeriesVO series = MetricDataVO.MetricSeriesVO.builder()
            .labels(Map.of("node", "broker-a"))
            .values(List.of(sample))
            .histograms(List.of())
            .build();

        MetricDataVO vo = MetricDataVO.builder()
            .resultType("matrix")
            .series(List.of(series))
            .warnings(List.of("partial data"))
            .build();

        assertEquals("matrix", vo.getResultType());
        assertEquals(List.of(series), vo.getSeries());
        assertEquals(List.of("partial data"), vo.getWarnings());
        assertEquals(Map.of("node", "broker-a"), series.getLabels());
        assertEquals(1784246400.0, sample.getTimestamp());
        assertEquals("98.5", sample.getValue());
    }
}
