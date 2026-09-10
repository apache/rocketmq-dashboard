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

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.apache.rocketmq.studio.cluster.metrics.MetricQueryDTO;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsDataSourceQueryRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();


    @Test
    void queryShouldBeRequired() {
        MetricsDataSourceQueryRequest request = new MetricsDataSourceQueryRequest();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("query is required");
    }

    @Test
    void nestedQueryValidationShouldPropagate() {
        MetricsDataSourceQueryRequest request = new MetricsDataSourceQueryRequest();
        request.setQuery(MetricQueryDTO.builder().build());

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("Metric query step is required", "Metric query is required");
    }

    @Test
    void validRequestShouldPassValidation() {
        MetricsDataSourceQueryRequest request = new MetricsDataSourceQueryRequest();
        request.setQuery(MetricQueryDTO.builder()
                .metric("up")
                .start(100L)
                .end(200L)
                .step("30s")
                .build());
        request.setInstanceId("instance-a");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void toStringShouldNeverExposeRequestCredentials() {
        MetricsDataSourceQueryRequest request = new MetricsDataSourceQueryRequest();
        request.setQuery(MetricQueryDTO.builder().metric("up").start(1L).end(2L).step("30s").build());
        request.setUsername("ops");
        request.setPassword("pw-secret");
        request.setBearerToken("tok-secret");

        String value = request.toString();

        assertThat(value).doesNotContain("pw-secret");
        assertThat(value).doesNotContain("tok-secret");
    }
}
