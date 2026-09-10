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

class ResetConsumerOffsetDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void requiredFieldsShouldNotBeBlankOrMissing() {
        ResetConsumerOffsetDTO request = ResetConsumerOffsetDTO.builder().build();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("instanceId is required", "name is required",
                        "timestamp is required", "topic is required");
    }

    @Test
    void timestampShouldBePositive() {
        ResetConsumerOffsetDTO request = ResetConsumerOffsetDTO.builder()
                .instanceId("instance-a")
                .name("cg-order")
                .timestamp(-1L)
                .topic("orders")
                .build();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("timestamp must be positive");
    }

    @Test
    void validResetRequestShouldPassValidation() {
        ResetConsumerOffsetDTO request = ResetConsumerOffsetDTO.builder()
                .instanceId("instance-a")
                .name("cg-order")
                .timestamp(1784246400000L)
                .topic("orders")
                .build();

        assertThat(validator.validate(request)).isEmpty();
    }
}
