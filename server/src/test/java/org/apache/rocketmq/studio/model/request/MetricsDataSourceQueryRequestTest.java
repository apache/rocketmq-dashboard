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
import java.util.Set;
import org.apache.rocketmq.studio.cluster.metrics.MetricQueryDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsDataSourceQueryRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private MetricsDataSourceQueryRequest sample() {
        MetricsDataSourceQueryRequest request = new MetricsDataSourceQueryRequest();
        request.setQuery(MetricQueryDTO.builder()
                .metric("sum(rate(rocketmq_messages_in_total[1m])) by (node_id)")
                .start(1000L)
                .end(2000L)
                .step("30s")
                .build());
        request.setInstanceId("inst-1");
        request.setUsername("reader");
        request.setPassword("sk-secret");
        request.setBearerToken("token-secret");
        return request;
    }

    @Test
    void acceptsCompleteRequest() {
        assertTrue(validator.validate(sample()).isEmpty());
    }

    @Test
    void rejectsMissingQuery() {
        MetricsDataSourceQueryRequest request = sample();
        request.setQuery(null);

        Set<ConstraintViolation<MetricsDataSourceQueryRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("query is required", violations.iterator().next().getMessage());
    }

    @Test
    void instanceIdAndCredentialsRemainOptional() {
        MetricsDataSourceQueryRequest request = sample();
        request.setInstanceId(null);
        request.setUsername(null);
        request.setPassword(null);
        request.setBearerToken(null);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void secretsAreExcludedFromToString() {
        String rendered = sample().toString();

        assertFalse(rendered.contains("sk-secret"));
        assertFalse(rendered.contains("token-secret"));
    }
}
