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
 * Pins the conversation-create contract of {@link AiConversationCreateDTO}: the body of
 * {@code POST /api/ai/conversations} - a frozen cross-language contract with the web client,
 * so the field bounds are the wire compatibility guarantee. Every field is optional (an
 * unbound chat in the default mode is valid), and each carries its documented size bound.
 */
class AiConversationCreateDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(AiConversationCreateDTO request) {
        Set<ConstraintViolation<AiConversationCreateDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void anUnboundChatInTheDefaultModeIsValid() {
        assertThat(violationsOf(AiConversationCreateDTO.builder().build())).isEmpty();
    }

    @Test
    void theInstanceBindingIsOptionalButBounded() {
        AiConversationCreateDTO overLimit = AiConversationCreateDTO.builder()
                .instanceId("i".repeat(129))
                .build();
        assertThat(violationsOf(overLimit)).containsExactly("instanceId must not exceed 128 characters");
    }

    @Test
    void theModeIsOptionalButBounded() {
        AiConversationCreateDTO overLimit = AiConversationCreateDTO.builder()
                .mode("m".repeat(17))
                .build();
        assertThat(violationsOf(overLimit)).containsExactly("mode must not exceed 16 characters");
    }
}
