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
package org.apache.rocketmq.studio.ops.ai.conversation.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the speed-report contract of {@link ReportRunSpeedDTO}: the tokens-per-second figure the
 * client measured is required and bounded (0 to 100000), so a bogus report cannot store an
 * absurdity.
 */
class ReportRunSpeedDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(ReportRunSpeedDTO request) {
        Set<ConstraintViolation<ReportRunSpeedDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void theSpeedIsRequired() {
        assertThat(violationsOf(ReportRunSpeedDTO.builder().build()))
                .containsExactly("tokensPerSecond is required");
    }

    @Test
    void theSpeedMustNotBeNegative() {
        ReportRunSpeedDTO request = ReportRunSpeedDTO.builder().tokensPerSecond(-0.1).build();
        assertThat(violationsOf(request)).containsExactly("tokensPerSecond must not be negative");
    }

    @Test
    void theSpeedIsCappedAtOneHundredThousand() {
        ReportRunSpeedDTO atCeiling = ReportRunSpeedDTO.builder().tokensPerSecond(100_000.0).build();
        assertThat(violationsOf(atCeiling)).isEmpty();

        ReportRunSpeedDTO overCeiling = ReportRunSpeedDTO.builder().tokensPerSecond(100_000.1).build();
        assertThat(violationsOf(overCeiling)).containsExactly("tokensPerSecond must not exceed 100000");
    }

    @Test
    void aZeroSpeedIsAValidReport() {
        ReportRunSpeedDTO request = ReportRunSpeedDTO.builder().tokensPerSecond(0.0).build();
        assertThat(violationsOf(request)).isEmpty();
        assertThat(request.getTokensPerSecond()).isZero();
    }
}
