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

class ResetPasswordDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void missingNewPasswordIsRejectedTest() {
        ResetPasswordDTO request = new ResetPasswordDTO();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("New password is required");
    }

    @Test
    void shortNewPasswordIsRejectedTest() {
        ResetPasswordDTO request = new ResetPasswordDTO();
        request.setNewPassword("short");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactly("New password must contain 8 to 256 characters");
    }

    @Test
    void completeRequestPassesValidationTest() {
        ResetPasswordDTO request = new ResetPasswordDTO();
        request.setNewPassword("a-secure-password");

        assertThat(validator.validate(request)).isEmpty();
    }
}
