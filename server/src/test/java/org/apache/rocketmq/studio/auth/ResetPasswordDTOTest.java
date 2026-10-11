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
 * Pins the password reset contract of {@link ResetPasswordDTO}: the new password is required and
 * bounded to 8-256 characters, and the Lombok toString never exposes it.
 */
class ResetPasswordDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(ResetPasswordDTO request) {
        Set<ConstraintViolation<ResetPasswordDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void aPasswordOfEightToTwoHundredFiftySixCharactersIsValid() {
        ResetPasswordDTO request = new ResetPasswordDTO();
        request.setNewPassword("12345678");
        assertThat(violationsOf(request)).isEmpty();

        request.setNewPassword("x".repeat(256));
        assertThat(violationsOf(request)).isEmpty();
    }

    @Test
    void aMissingOrBlankPasswordIsRejected() {
        ResetPasswordDTO request = new ResetPasswordDTO();
        assertThat(violationsOf(request)).contains("New password is required");

        request.setNewPassword("   ");
        // a blank password violates both the presence and the length bound
        assertThat(violationsOf(request)).contains("New password is required");
    }

    @Test
    void theLengthBoundsAreEnforced() {
        ResetPasswordDTO request = new ResetPasswordDTO();
        request.setNewPassword("1234567");
        assertThat(violationsOf(request))
                .containsExactly("New password must contain 8 to 256 characters");

        request.setNewPassword("x".repeat(257));
        assertThat(violationsOf(request))
                .containsExactly("New password must contain 8 to 256 characters");
    }

    @Test
    void toStringNeverExposesThePassword() {
        ResetPasswordDTO request = new ResetPasswordDTO();
        request.setNewPassword("plain-secret-123");

        assertThat(request.toString()).doesNotContain("plain-secret-123");
    }
}
