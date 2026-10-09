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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the resend contract of {@link DLQResendSelectedRequestDTO}: the instance and group are
 * required, at least one and at most 100 message ids are allowed, and a blank id inside the list
 * fails the whole request (a container-element constraint, not just a list-level one).
 */
class DLQResendSelectedRequestDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(DLQResendSelectedRequestDTO request) {
        Set<ConstraintViolation<DLQResendSelectedRequestDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    private DLQResendSelectedRequestDTO.DLQResendSelectedRequestDTOBuilder valid() {
        return DLQResendSelectedRequestDTO.builder()
                .instanceId("instance-1")
                .groupName("group-1")
                .msgIds(List.of("msg-1"));
    }

    @Test
    void theInstanceAndTheGroupAreRequired() {
        DLQResendSelectedRequestDTO noInstance = valid().instanceId(" ").build();
        assertThat(violationsOf(noInstance)).containsExactly("instanceId is required");

        DLQResendSelectedRequestDTO noGroup = valid().groupName("").build();
        assertThat(violationsOf(noGroup)).containsExactly("groupName is required");
    }

    @Test
    void atLeastOneMessageIdIsRequired() {
        DLQResendSelectedRequestDTO empty = valid().msgIds(List.of()).build();
        assertThat(violationsOf(empty)).containsExactly("At least one msgId is required");
    }

    @Test
    void atMostOneHundredMessageIdsAreAllowedPerResend() {
        DLQResendSelectedRequestDTO atLimit = valid().msgIds(msgIds(100)).build();
        assertThat(violationsOf(atLimit)).isEmpty();

        DLQResendSelectedRequestDTO overLimit = valid().msgIds(msgIds(101)).build();
        assertThat(violationsOf(overLimit)).containsExactly("At most 100 msgIds are allowed per resend");
    }

    @Test
    void aBlankMessageIdInsideTheListFailsTheWholeRequest() {
        List<String> msgIds = new ArrayList<>(List.of("msg-1"));
        msgIds.add("   ");
        DLQResendSelectedRequestDTO request = valid().msgIds(msgIds).build();

        assertThat(violationsOf(request)).contains("msgId must not be blank");
    }

    @Test
    void aTargetTopicIsOptional() {
        DLQResendSelectedRequestDTO request = valid().targetTopic("topic-1").build();
        assertThat(violationsOf(request)).isEmpty();
    }

    private List<String> msgIds(int count) {
        List<String> msgIds = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            msgIds.add("msg-" + i);
        }
        return msgIds;
    }
}
