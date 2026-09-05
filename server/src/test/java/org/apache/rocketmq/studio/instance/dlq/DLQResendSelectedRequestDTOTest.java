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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class DLQResendSelectedRequestDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void emptyRequestReportsEveryRequiredFieldTest() {
        DLQResendSelectedRequestDTO request = new DLQResendSelectedRequestDTO();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("instanceId is required", "groupName is required",
                        "At least one msgId is required");
    }

    @Test
    void blankMsgIdsAreRejectedTest() {
        DLQResendSelectedRequestDTO request = DLQResendSelectedRequestDTO.builder()
                .instanceId("instance-1")
                .groupName("cg-orders")
                .msgIds(List.of("  "))
                .build();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactly("msgId must not be blank");
    }

    @Test
    void moreThanOneHundredMsgIdsAreRejectedTest() {
        DLQResendSelectedRequestDTO request = DLQResendSelectedRequestDTO.builder()
                .instanceId("instance-1")
                .groupName("cg-orders")
                .msgIds(IntStream.rangeClosed(1, 101).mapToObj(String::valueOf)
                        .collect(Collectors.toCollection(ArrayList::new)))
                .build();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("At most 100 msgIds are allowed per resend");
    }

    @Test
    void completeRequestPassesValidationTest() {
        DLQResendSelectedRequestDTO request = DLQResendSelectedRequestDTO.builder()
                .instanceId("instance-1")
                .groupName("cg-orders")
                .msgIds(List.of("msg-1", "msg-2"))
                .targetTopic("orders")
                .build();

        assertThat(validator.validate(request)).isEmpty();
    }
}
