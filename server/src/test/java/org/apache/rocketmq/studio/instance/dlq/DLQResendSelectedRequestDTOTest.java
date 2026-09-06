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
package org.apache.rocketmq.studio.instance.dlq;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DLQResendSelectedRequestDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();


    @Test
    void requiredFieldsShouldBePresent() {
        DLQResendSelectedRequestDTO request = DLQResendSelectedRequestDTO.builder().build();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("instanceId is required", "groupName is required",
                        "At least one msgId is required");
    }

    @Test
    void msgIdsShouldBeBoundedAndNonBlank() {
        java.util.List<String> many = new java.util.ArrayList<>();
        for (int i = 0; i < 101; i++) {
            many.add("msg-" + i);
        }
        DLQResendSelectedRequestDTO oversized = DLQResendSelectedRequestDTO.builder()
                .instanceId("instance-a")
                .groupName("cg-order")
                .msgIds(many)
                .build();

        DLQResendSelectedRequestDTO blank = DLQResendSelectedRequestDTO.builder()
                .instanceId("instance-a")
                .groupName("cg-order")
                .msgIds(java.util.List.of("msg-1", " "))
                .build();

        assertThat(validator.validate(oversized))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("At most 100 msgIds are allowed per resend");
        assertThat(validator.validate(blank))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("msgId must not be blank");
    }

    @Test
    void validSelectedResendShouldPassValidation() {
        DLQResendSelectedRequestDTO request = DLQResendSelectedRequestDTO.builder()
                .instanceId("instance-a")
                .groupName("cg-order")
                .msgIds(java.util.List.of("msg-1", "msg-2"))
                .targetTopic("orders-retry")
                .build();

        assertThat(validator.validate(request)).isEmpty();
    }
}
