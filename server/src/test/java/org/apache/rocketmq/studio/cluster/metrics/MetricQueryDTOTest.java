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

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetricQueryDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rawMetricQueryPassesValidationTest() {
        assertThat(validator.validate(base(MetricQueryDTO.builder().metric("up").build()))).isEmpty();
    }

    @Test
    void semanticSelectionRequiresProfileAndMetricTogetherTest() {
        assertThat(validator.validate(base(MetricQueryDTO.builder().semanticMetric("consumer_lag_messages").build())))
                .extracting(violation -> violation.getMessage())
                .contains("Metric profile and semantic metric are required together");
        assertThat(validator.validate(base(MetricQueryDTO.builder().profileId("rocketmq5-native").build())))
                .extracting(violation -> violation.getMessage())
                .contains("Metric profile and semantic metric are required together");
        assertThat(validator.validate(base(MetricQueryDTO.builder()
                .profileId("rocketmq5-native").semanticMetric("consumer_lag_messages").build()))).isEmpty();
    }

    @Test
    void metricQueryWithoutAnySelectionIsRejectedTest() {
        MetricQueryDTO request = base(MetricQueryDTO.builder().build());

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("Metric query is required");
    }

    @Test
    void metricCannotBeCombinedWithSemanticSelectionTest() {
        MetricQueryDTO request = base(MetricQueryDTO.builder()
                .metric("up")
                .profileId("rocketmq5-native")
                .semanticMetric("consumer_lag_messages")
                .build());

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("Metric query cannot be combined with a semantic metric selection");
    }

    @Test
    void nonPositiveRangeAndBlankStepAreRejectedTest() {
        MetricQueryDTO request = MetricQueryDTO.builder()
                .metric("up")
                .start(0)
                .end(-1)
                .step(" ")
                .build();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("Metric query start must be positive",
                        "Metric query end must be positive", "Metric query step is required");
    }

    private static MetricQueryDTO base(MetricQueryDTO request) {
        request.setStart(1784107658L);
        request.setEnd(1784108558L);
        request.setStep("30s");
        return request;
    }
}
