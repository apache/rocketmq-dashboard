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
 * Pins the change-password request contract of {@link ChangePasswordDTO}: both secrets are
 * required, the new one is bounded (8 to 256 characters), and neither secret ever reaches
 * {@code toString()}. The DTO travels through request logging and error reporting, so a lost
 * {@code @ToString.Exclude} would leak live credentials straight into the logs.
 */
class ChangePasswordDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(ChangePasswordDTO request) {
        Set<ConstraintViolation<ChangePasswordDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void aCompleteRequestPassesValidation() {
        ChangePasswordDTO request = new ChangePasswordDTO();
        request.setCurrentPassword("old-secret");
        request.setNewPassword("new-secret-1");

        assertThat(violationsOf(request)).isEmpty();
        assertThat(request.getCurrentPassword()).isEqualTo("old-secret");
        assertThat(request.getNewPassword()).isEqualTo("new-secret-1");
    }

    @Test
    void bothPasswordsAreRequired() {
        ChangePasswordDTO missingCurrent = new ChangePasswordDTO();
        missingCurrent.setCurrentPassword("   ");
        missingCurrent.setNewPassword("new-secret-1");
        assertThat(violationsOf(missingCurrent)).containsExactly("Current password is required");

        ChangePasswordDTO missingNew = new ChangePasswordDTO();
        missingNew.setCurrentPassword("old-secret");
        missingNew.setNewPassword("   ");
        assertThat(violationsOf(missingNew)).containsExactlyInAnyOrder(
                "New password is required",
                "New password must contain 8 to 256 characters");
    }

    @Test
    void theNewPasswordIsBoundedOnBothSides() {
        ChangePasswordDTO tooShort = new ChangePasswordDTO();
        tooShort.setCurrentPassword("old-secret");
        tooShort.setNewPassword("1234567");
        assertThat(violationsOf(tooShort)).containsExactly("New password must contain 8 to 256 characters");

        ChangePasswordDTO atLowerBound = new ChangePasswordDTO();
        atLowerBound.setCurrentPassword("old-secret");
        atLowerBound.setNewPassword("12345678");
        assertThat(violationsOf(atLowerBound)).isEmpty();

        ChangePasswordDTO atUpperBound = new ChangePasswordDTO();
        atUpperBound.setCurrentPassword("old-secret");
        atUpperBound.setNewPassword("x".repeat(256));
        assertThat(violationsOf(atUpperBound)).isEmpty();

        ChangePasswordDTO tooLong = new ChangePasswordDTO();
        tooLong.setCurrentPassword("old-secret");
        tooLong.setNewPassword("x".repeat(257));
        assertThat(violationsOf(tooLong)).containsExactly("New password must contain 8 to 256 characters");
    }

    @Test
    void toStringNeverLeaksEitherSecret() {
        ChangePasswordDTO request = new ChangePasswordDTO();
        request.setCurrentPassword("current-secret-value");
        request.setNewPassword("new-secret-value");

        assertThat(request.toString())
                .doesNotContain("current-secret-value")
                .doesNotContain("new-secret-value");
    }
}
