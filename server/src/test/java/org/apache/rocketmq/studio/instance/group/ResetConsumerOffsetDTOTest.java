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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the reset contract of {@link ResetConsumerOffsetDTO}: the instance, the group name, the
 * topic and a positive reset timestamp are all required.
 */
class ResetConsumerOffsetDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(ResetConsumerOffsetDTO request) {
        Set<ConstraintViolation<ResetConsumerOffsetDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    private ResetConsumerOffsetDTO.ResetConsumerOffsetDTOBuilder valid() {
        return ResetConsumerOffsetDTO.builder()
                .instanceId("instance-1")
                .name("group-1")
                .timestamp(1784112606_000L)
                .topic("topic-1");
    }

    @Test
    void aValidRequestPassesUntouched() {
        assertThat(violationsOf(valid().build())).isEmpty();
    }

    @Test
    void everyFieldOfTheResetKeyIsRequired() {
        ResetConsumerOffsetDTO request = valid().instanceId(" ").build();
        assertThat(violationsOf(request)).containsExactly("instanceId is required");

        request = valid().name(" ").build();
        assertThat(violationsOf(request)).containsExactly("name is required");

        request = valid().topic(null).build();
        assertThat(violationsOf(request)).containsExactly("topic is required");
    }

    @Test
    void theResetTimestampMustBePresentAndPositive() {
        ResetConsumerOffsetDTO missing = valid().timestamp(null).build();
        assertThat(violationsOf(missing)).containsExactly("timestamp is required");

        ResetConsumerOffsetDTO zero = valid().timestamp(0L).build();
        assertThat(violationsOf(zero)).containsExactly("timestamp must be positive");

        ResetConsumerOffsetDTO negative = valid().timestamp(-1L).build();
        assertThat(violationsOf(negative)).containsExactly("timestamp must be positive");
    }
}
