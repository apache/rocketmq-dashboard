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
package org.apache.rocketmq.studio.instance.group;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreateConsumerGroupDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void nameShouldBeRequired() {
        CreateConsumerGroupDTO request = new CreateConsumerGroupDTO();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("name is required");
    }

    @Test
    void retryAndDelayCountsShouldNotBeNegative() {
        CreateConsumerGroupDTO request = new CreateConsumerGroupDTO();
        request.setName("cg-order");
        request.setRetryMaxTimes(-1);
        request.setDelaySeconds(-2);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("retryMaxTimes must be zero or positive",
                        "delaySeconds must be zero or positive");
    }

    @Test
    void validRequestShouldPassValidation() {
        CreateConsumerGroupDTO request = new CreateConsumerGroupDTO();
        request.setName("cg-order");
        request.setRetryMaxTimes(16);
        request.setDelaySeconds(0);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void toConsumerGroupVoShouldOnlyApplyNonNullCounts() {
        CreateConsumerGroupDTO sparse = new CreateConsumerGroupDTO();
        sparse.setName("cg-order");

        ConsumerGroupVO group = sparse.toConsumerGroupVO();

        assertThat(group.getRetryMaxTimes()).isZero();
        assertThat(group.getDelaySeconds()).isZero();

        CreateConsumerGroupDTO full = new CreateConsumerGroupDTO();
        full.setName("cg-order");
        full.setRetryMaxTimes(32);
        full.setDelaySeconds(5);

        ConsumerGroupVO mapped = full.toConsumerGroupVO();
        assertThat(mapped.getRetryMaxTimes()).isEqualTo(32);
        assertThat(mapped.getDelaySeconds()).isEqualTo(5);
    }
}
