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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the selection grammar of {@link MetricQueryDTO}: a range query is either a raw PromQL
 * expression, or a profile/semantic pair - never both, never a semantic half. The three
 * @AssertTrue rules are what the REST layer actually enforces, so a wrong one lets an ambiguous
 * or empty query through to Prometheus.
 */
class MetricQueryDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static MetricQueryDTO.MetricQueryDTOBuilder query() {
        return MetricQueryDTO.builder().start(1).end(2).step("30s");
    }

    @Test
    void aRawPromqlExpressionAloneIsValid() {
        Set<ConstraintViolation<MetricQueryDTO>> violations =
                validator.validate(query().metric("sum(rate(x[1m]))").build());

        assertThat(violations).isEmpty();
        assertThat(query().metric("sum(rate(x[1m]))").build().isMetricSelectionPresent()).isTrue();
    }

    @Test
    void aCompleteSemanticSelectionIsValid() {
        Set<ConstraintViolation<MetricQueryDTO>> violations =
                validator.validate(query().profileId("rocketmq5-native")
                        .semanticMetric("consumer_lag_messages").build());

        assertThat(violations).isEmpty();
    }

    @Test
    void noSelectionAtAllIsRejected() {
        Set<ConstraintViolation<MetricQueryDTO>> violations = validator.validate(query().build());

        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .contains("Metric query is required");
    }

    @Test
    void aHalfSemanticSelectionIsRejected() {
        MetricQueryDTO profileOnly = query().profileId("rocketmq5-native").build();
        MetricQueryDTO semanticOnly = query().semanticMetric("consumer_lag_messages").build();

        for (MetricQueryDTO half : List.of(profileOnly, semanticOnly)) {
            assertThat(half.isSemanticMetricSelectionComplete()).isFalse();
            assertThat(validator.validate(half)).extracting(ConstraintViolation::getMessage)
                    .contains("Metric profile and semantic metric are required together");
        }
        // A half selection is an INCOMPLETE selection, not a missing one: the bare semantic key
        // still counts as present, so the error must say what is missing, not that nothing was.
        assertThat(validator.validate(semanticOnly)).extracting(ConstraintViolation::getMessage)
                .doesNotContain("Metric query is required");
    }

    @Test
    void aRawExpressionCannotBeCombinedWithASemanticSelection() {
        List<MetricQueryDTO> combos = List.of(
                query().metric("up").profileId("rocketmq5-native").build(),
                query().metric("up").semanticMetric("consumer_lag_messages").build(),
                query().metric("up").profileId("rocketmq5-native")
                        .semanticMetric("consumer_lag_messages").build());

        for (MetricQueryDTO combo : combos) {
            assertThat(combo.isMetricSelectionExclusive()).isFalse();
            assertThat(validator.validate(combo)).extracting(ConstraintViolation::getMessage)
                    .contains("Metric query cannot be combined with a semantic metric selection");
        }
    }

    @Test
    void aBlankSelectionCountsAsAbsent() {
        MetricQueryDTO blank = query().metric("   ").build();

        assertThat(blank.isMetricSelectionPresent()).isFalse();
        assertThat(validator.validate(blank)).extracting(ConstraintViolation::getMessage)
                .contains("Metric query is required");
    }

    @Test
    void theStepIsRequiredAndBothBoundsMustBePositive() {
        Set<ConstraintViolation<MetricQueryDTO>> violations =
                validator.validate(MetricQueryDTO.builder().metric("up").start(0).end(-1).build());

        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .contains("Metric query step is required",
                        "Metric query start must be positive",
                        "Metric query end must be positive");
    }
}
