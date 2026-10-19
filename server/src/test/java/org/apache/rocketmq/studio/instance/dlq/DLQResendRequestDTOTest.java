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

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the resend contract of {@link DLQResendRequestDTO}: the instance and the group are
 * required, while the time range and the target topic are optional filters the caller may omit.
 */
class DLQResendRequestDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(DLQResendRequestDTO request) {
        Set<ConstraintViolation<DLQResendRequestDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void aMinimalResendOnlyNeedsTheInstanceAndTheGroup() {
        DLQResendRequestDTO request = DLQResendRequestDTO.builder()
                .instanceId("instance-1")
                .groupName("group-1")
                .build();
        assertThat(violationsOf(request)).isEmpty();
        assertThat(request.getStartTime()).isNull();
        assertThat(request.getEndTime()).isNull();
        assertThat(request.getTargetTopic()).isNull();
    }

    @Test
    void theInstanceAndTheGroupAreRequired() {
        DLQResendRequestDTO noInstance = DLQResendRequestDTO.builder()
                .groupName("group-1")
                .build();
        assertThat(violationsOf(noInstance)).containsExactly("instanceId is required");

        DLQResendRequestDTO blankGroup = DLQResendRequestDTO.builder()
                .instanceId("instance-1")
                .groupName("   ")
                .build();
        assertThat(violationsOf(blankGroup)).containsExactly("groupName is required");
    }

    @Test
    void theOptionalFiltersRoundTrip() {
        DLQResendRequestDTO request = DLQResendRequestDTO.builder()
                .instanceId("instance-1")
                .groupName("group-1")
                .startTime(1784112606_000L)
                .endTime(1784114406_000L)
                .targetTopic("topic-1")
                .build();
        assertThat(violationsOf(request)).isEmpty();
        assertThat(request.getStartTime()).isEqualTo(1784112606_000L);
        assertThat(request.getEndTime()).isEqualTo(1784114406_000L);
        assertThat(request.getTargetTopic()).isEqualTo("topic-1");
    }
}
