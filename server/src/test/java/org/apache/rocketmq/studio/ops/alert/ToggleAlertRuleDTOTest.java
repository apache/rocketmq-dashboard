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
package org.apache.rocketmq.studio.ops.alert;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the toggle contract of {@link ToggleAlertRuleDTO}: both the rule id and the enabled flag
 * are required - the operations endpoint must never silently default a rule's state.
 */
class ToggleAlertRuleDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(ToggleAlertRuleDTO request) {
        Set<ConstraintViolation<ToggleAlertRuleDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void bothToggleStatesAreValid() {
        assertThat(violationsOf(ToggleAlertRuleDTO.builder().id(7L).enabled(true).build())).isEmpty();
        assertThat(violationsOf(ToggleAlertRuleDTO.builder().id(7L).enabled(false).build())).isEmpty();
    }

    @Test
    void theRuleIdIsRequired() {
        assertThat(violationsOf(ToggleAlertRuleDTO.builder().enabled(true).build()))
                .containsExactly("id is required");
    }

    @Test
    void theEnabledFlagIsRequired() {
        assertThat(violationsOf(ToggleAlertRuleDTO.builder().id(7L).build()))
                .containsExactly("enabled is required");
    }
}
