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
package org.apache.rocketmq.studio.instance.topic;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the TTL update contract of {@link LiteTopicTTLUpdateDTO}: the instance, the topic
 * pattern and a strictly positive new TTL are all required.
 */
class LiteTopicTTLUpdateDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(LiteTopicTTLUpdateDTO request) {
        Set<ConstraintViolation<LiteTopicTTLUpdateDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void aValidRequestPassesUntouched() {
        LiteTopicTTLUpdateDTO request = new LiteTopicTTLUpdateDTO();
        request.setInstanceId("instance-1");
        request.setTopicPattern("orders-*");
        request.setNewTTL(86_400L);
        assertThat(violationsOf(request)).isEmpty();
    }

    @Test
    void theInstanceAndTheTopicPatternAreRequired() {
        LiteTopicTTLUpdateDTO request = new LiteTopicTTLUpdateDTO();
        request.setTopicPattern("orders-*");
        request.setNewTTL(86_400L);
        assertThat(violationsOf(request)).containsExactly("instanceId is required");

        request = new LiteTopicTTLUpdateDTO();
        request.setInstanceId("instance-1");
        request.setNewTTL(86_400L);
        assertThat(violationsOf(request)).containsExactly("topicPattern is required");
    }

    @Test
    void theNewTTLMustBePresentAndStrictlyPositive() {
        LiteTopicTTLUpdateDTO missing = new LiteTopicTTLUpdateDTO();
        missing.setInstanceId("instance-1");
        missing.setTopicPattern("orders-*");
        assertThat(violationsOf(missing)).containsExactly("newTTL is required");

        LiteTopicTTLUpdateDTO zero = new LiteTopicTTLUpdateDTO();
        zero.setInstanceId("instance-1");
        zero.setTopicPattern("orders-*");
        zero.setNewTTL(0L);
        assertThat(violationsOf(zero)).containsExactly("newTTL must be positive");

        LiteTopicTTLUpdateDTO negative = new LiteTopicTTLUpdateDTO();
        negative.setInstanceId("instance-1");
        negative.setTopicPattern("orders-*");
        negative.setNewTTL(-1L);
        assertThat(violationsOf(negative)).containsExactly("newTTL must be positive");
    }
}
