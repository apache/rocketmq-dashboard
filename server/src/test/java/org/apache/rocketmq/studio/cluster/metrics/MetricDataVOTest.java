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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the metric query payload of {@link MetricDataVO}: the result type, the labelled series
 * and the warnings round-trip - the shape the metrics tab renders - and a fresh payload carries
 * no warnings rather than an empty list (the two mean different things on the wire).
 */
class MetricDataVOTest {

    @Test
    void theResultShapeRoundTrips() {
        MetricDataVO.MetricSeriesVO series = MetricDataVO.MetricSeriesVO.builder()
                .labels(java.util.Map.of("gpu", "0"))
                .values(List.of())
                .build();
        MetricDataVO data = MetricDataVO.builder()
                .resultType("matrix")
                .series(List.of(series))
                .warnings(List.of("one series was truncated"))
                .build();

        assertThat(data.getResultType()).isEqualTo("matrix");
        assertThat(data.getSeries()).hasSize(1);
        assertThat(data.getSeries().get(0).getLabels()).containsEntry("gpu", "0");
        assertThat(data.getWarnings()).containsExactly("one series was truncated");
    }

    @Test
    void aFreshPayloadCarriesNoWarnings() {
        MetricDataVO data = new MetricDataVO();
        assertThat(data.getResultType()).isNull();
        assertThat(data.getSeries()).isNull();
        assertThat(data.getWarnings()).isNull();
    }
}
