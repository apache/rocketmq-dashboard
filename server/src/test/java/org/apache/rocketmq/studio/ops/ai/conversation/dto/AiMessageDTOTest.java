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
 * Pins the send-message contract of {@link AiMessageDTO}: the message is required and bounded
 * to 8192 characters, the model/engine/mode overrides are optional but bounded, and the
 * enhance flag defaults to false - prompts are sent verbatim unless the caller opts in.
 */
class AiMessageDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(AiMessageDTO request) {
        Set<ConstraintViolation<AiMessageDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void aBareMessageIsValid() {
        AiMessageDTO request = AiMessageDTO.builder().message("hello").build();
        assertThat(violationsOf(request)).isEmpty();
        // prompts are sent verbatim unless the caller opts into the rewrite
        assertThat(request.isEnhance()).isFalse();
    }

    @Test
    void theMessageIsRequired() {
        assertThat(violationsOf(AiMessageDTO.builder().build()))
                .containsExactly("message is required");

        AiMessageDTO blank = AiMessageDTO.builder().message("   ").build();
        assertThat(violationsOf(blank)).containsExactly("message is required");
    }

    @Test
    void theMessageIsCappedAtEightThousandCharacters() {
        AiMessageDTO atLimit = AiMessageDTO.builder().message("x".repeat(8192)).build();
        assertThat(violationsOf(atLimit)).isEmpty();

        AiMessageDTO overLimit = AiMessageDTO.builder().message("x".repeat(8193)).build();
        assertThat(violationsOf(overLimit)).containsExactly("message must not exceed 8192 characters");
    }

    @Test
    void theOverridesAreOptionalButBounded() {
        AiMessageDTO overModel = AiMessageDTO.builder().message("hi").model("m".repeat(129)).build();
        assertThat(violationsOf(overModel)).containsExactly("model must not exceed 128 characters");

        AiMessageDTO overEngine = AiMessageDTO.builder().message("hi").engine("e".repeat(17)).build();
        assertThat(violationsOf(overEngine)).containsExactly("engine must not exceed 16 characters");

        AiMessageDTO overMode = AiMessageDTO.builder().message("hi").mode("m".repeat(17)).build();
        assertThat(violationsOf(overMode)).containsExactly("mode must not exceed 16 characters");
    }
}
