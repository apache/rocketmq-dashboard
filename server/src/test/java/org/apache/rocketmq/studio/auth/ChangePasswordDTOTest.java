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

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChangePasswordDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void currentPasswordShouldBeRequired() {
        ChangePasswordDTO request = new ChangePasswordDTO();
        request.setNewPassword("new-secret-123");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("Current password is required");
    }

    @Test
    void newPasswordShouldBeRequiredAndWithinBounds() {
        ChangePasswordDTO blank = new ChangePasswordDTO();
        blank.setCurrentPassword("old-secret");
        blank.setNewPassword(" ");

        ChangePasswordDTO shortOne = new ChangePasswordDTO();
        shortOne.setCurrentPassword("old-secret");
        shortOne.setNewPassword("short");

        assertThat(validator.validate(blank))
                .extracting(violation -> violation.getMessage())
                .contains("New password is required");
        assertThat(validator.validate(shortOne))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("New password must contain 8 to 256 characters");
    }

    @Test
    void validChangeShouldPassValidation() {
        ChangePasswordDTO request = new ChangePasswordDTO();
        request.setCurrentPassword("old-secret");
        request.setNewPassword("new-secret-123");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void toStringShouldNeverExposePasswords() {
        ChangePasswordDTO request = new ChangePasswordDTO();
        request.setCurrentPassword("old-secret");
        request.setNewPassword("new-secret-123");

        String value = request.toString();

        assertThat(value).doesNotContain("old-secret");
        assertThat(value).doesNotContain("new-secret-123");
    }
}
