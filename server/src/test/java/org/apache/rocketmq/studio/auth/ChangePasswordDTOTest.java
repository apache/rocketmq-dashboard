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
    void emptyRequestReportsBothPasswordMessagesTest() {
        ChangePasswordDTO request = new ChangePasswordDTO();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("Current password is required", "New password is required");
    }

    @Test
    void shortNewPasswordIsRejectedTest() {
        ChangePasswordDTO request = new ChangePasswordDTO();
        request.setCurrentPassword("old-password");
        request.setNewPassword("short");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactly("New password must contain 8 to 256 characters");
    }

    @Test
    void completeRequestPassesValidationTest() {
        ChangePasswordDTO request = new ChangePasswordDTO();
        request.setCurrentPassword("old-password");
        request.setNewPassword("new-password-123");

        assertThat(validator.validate(request)).isEmpty();
    }
}
