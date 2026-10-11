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
 * Pins the acknowledge contract of {@link AcknowledgeSystemAlertDTO}: the alert id is required -
 * an acknowledgement that names no alert must fail validation instead of acknowledging nothing.
 */
class AcknowledgeSystemAlertDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(AcknowledgeSystemAlertDTO request) {
        Set<ConstraintViolation<AcknowledgeSystemAlertDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void aRequestNamingAnAlertIsValid() {
        AcknowledgeSystemAlertDTO request = AcknowledgeSystemAlertDTO.builder().id(42L).build();
        assertThat(violationsOf(request)).isEmpty();
        assertThat(request.getId()).isEqualTo(42L);
    }

    @Test
    void theAlertIdIsRequired() {
        assertThat(violationsOf(AcknowledgeSystemAlertDTO.builder().build()))
                .containsExactly("id is required");
    }
}
