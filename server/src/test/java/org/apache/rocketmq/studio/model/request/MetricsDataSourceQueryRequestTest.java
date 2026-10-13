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

package org.apache.rocketmq.studio.model.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.apache.rocketmq.studio.cluster.metrics.MetricQueryDTO;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the envelope of {@link MetricsDataSourceQueryRequest}, the request that runs a PromQL
 * range query against a configured data source: the query itself is required, and the nested
 * {@link MetricQueryDTO} is validated in place — a broken query must fail the whole request
 * with the nested message, not surface later as a remote 400. (The credentials on this
 * request are redacted by {@code SensitiveRequestToStringTest}; this pins the validation
 * side.)
 */
class MetricsDataSourceQueryRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(MetricsDataSourceQueryRequest request) {
        Set<ConstraintViolation<MetricsDataSourceQueryRequest>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    private MetricsDataSourceQueryRequest withQuery(MetricQueryDTO query) {
        MetricsDataSourceQueryRequest request = new MetricsDataSourceQueryRequest();
        request.setInstanceId("instance-1");
        request.setQuery(query);
        return request;
    }

    @Test
    void aCompleteRequestPassesValidation() {
        MetricsDataSourceQueryRequest request = withQuery(MetricQueryDTO.builder()
                .metric("cpu")
                .start(1700000000L)
                .end(1700003600L)
                .step("1m")
                .build());

        assertThat(violationsOf(request)).isEmpty();
    }

    @Test
    void theQueryIsRequired() {
        MetricsDataSourceQueryRequest request = new MetricsDataSourceQueryRequest();
        request.setInstanceId("instance-1");

        assertThat(violationsOf(request)).containsExactly("query is required");
    }

    @Test
    void aBrokenNestedQueryFailsTheWholeRequest() {
        MetricsDataSourceQueryRequest request = withQuery(MetricQueryDTO.builder()
                .metric("cpu")
                .start(1700000000L)
                .end(1700003600L)
                .step("   ")
                .build());

        assertThat(violationsOf(request)).containsExactly("Metric query step is required");
    }
}
