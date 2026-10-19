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

package org.apache.rocketmq.studio.auth;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the create-user request of {@link CreateStudioUserDTO}: the username is required and
 * bounded, the password is required and bounded 8..256, and the password never reaches
 * {@code toString()}. The DTO travels through request logging and error reporting, so a lost
 * {@code @ToString.Exclude} would leak a live credential straight into the logs — this DTO is
 * not covered by {@code SensitiveRequestToStringTest}, so the redaction has no net today.
 */
class CreateStudioUserDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(CreateStudioUserDTO request) {
        Set<ConstraintViolation<CreateStudioUserDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void aCompleteRequestPassesValidation() {
        CreateStudioUserDTO request = new CreateStudioUserDTO();
        request.setUsername("ops-admin");
        request.setPassword("initial-secret-1");
        request.setAdmin(true);

        assertThat(violationsOf(request)).isEmpty();
        assertThat(request.isAdmin()).isTrue();
    }

    @Test
    void theUsernameIsRequiredAndBounded() {
        CreateStudioUserDTO missing = new CreateStudioUserDTO();
        missing.setUsername("   ");
        missing.setPassword("initial-secret-1");
        assertThat(violationsOf(missing)).containsExactly("Username is required");

        CreateStudioUserDTO tooLong = new CreateStudioUserDTO();
        tooLong.setUsername("u".repeat(129));
        tooLong.setPassword("initial-secret-1");
        assertThat(violationsOf(tooLong)).containsExactly("Username must not exceed 128 characters");

        CreateStudioUserDTO atBound = new CreateStudioUserDTO();
        atBound.setUsername("u".repeat(128));
        atBound.setPassword("initial-secret-1");
        assertThat(violationsOf(atBound)).isEmpty();
    }

    @Test
    void thePasswordIsRequiredAndBoundedOnBothSides() {
        CreateStudioUserDTO tooShort = new CreateStudioUserDTO();
        tooShort.setUsername("ops-admin");
        tooShort.setPassword("1234567");
        assertThat(violationsOf(tooShort)).containsExactly("Password must contain 8 to 256 characters");

        CreateStudioUserDTO atLowerBound = new CreateStudioUserDTO();
        atLowerBound.setUsername("ops-admin");
        atLowerBound.setPassword("12345678");
        assertThat(violationsOf(atLowerBound)).isEmpty();

        CreateStudioUserDTO atUpperBound = new CreateStudioUserDTO();
        atUpperBound.setUsername("ops-admin");
        atUpperBound.setPassword("x".repeat(256));
        assertThat(violationsOf(atUpperBound)).isEmpty();

        CreateStudioUserDTO tooLong = new CreateStudioUserDTO();
        tooLong.setUsername("ops-admin");
        tooLong.setPassword("x".repeat(257));
        assertThat(violationsOf(tooLong)).containsExactly("Password must contain 8 to 256 characters");
    }

    @Test
    void toStringNeverLeaksThePassword() {
        CreateStudioUserDTO request = new CreateStudioUserDTO();
        request.setUsername("ops-admin");
        request.setPassword("initial-secret-value");

        assertThat(request.toString())
                .contains("ops-admin")
                .doesNotContain("initial-secret-value");
    }
}
