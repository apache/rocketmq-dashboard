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
    void rangeAndStepShouldBePresent() {
        MetricQueryDTO request = MetricQueryDTO.builder().build();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("Metric query step is required", "Metric query is required");
    }

    @Test
    void profileAndSemanticMetricShouldComeTogether() {
        MetricQueryDTO orphan = MetricQueryDTO.builder()
                .profileId("rocketmq5-native")
                .start(100L)
                .end(200L)
                .step("30s")
                .build();

        assertThat(validator.validate(orphan))
                .extracting(violation -> violation.getMessage())
                .contains("Metric profile and semantic metric are required together");

        MetricQueryDTO complete = MetricQueryDTO.builder()
                .profileId("rocketmq5-native")
                .semanticMetric("consumer_lag_messages")
                .start(100L)
                .end(200L)
                .step("30s")
                .build();

        assertThat(validator.validate(complete)).isEmpty();
    }

    @Test
    void rawPromqlQueryShouldPassValidation() {
        MetricQueryDTO request = MetricQueryDTO.builder()
                .metric("sum(rate(rocketmq_messages_in_total[1m])) by (node_id)")
                .start(100L)
                .end(200L)
                .step("30s")
                .build();

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void startAndEndShouldBePositive() {
        MetricQueryDTO request = MetricQueryDTO.builder()
                .metric("up")
                .start(-1L)
                .end(0L)
                .step("30s")
                .build();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("Metric query start must be positive", "Metric query end must be positive");
    }
}
