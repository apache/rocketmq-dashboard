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

class CreateStudioUserDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void emptyRequestReportsUsernameAndPasswordTest() {
        CreateStudioUserDTO request = new CreateStudioUserDTO();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("Username is required", "Password is required");
    }

    @Test
    void shortPasswordIsRejectedTest() {
        CreateStudioUserDTO request = new CreateStudioUserDTO();
        request.setUsername("operator");
        request.setPassword("short");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactly("Password must contain 8 to 256 characters");
    }

    @Test
    void completeRequestPassesValidationTest() {
        CreateStudioUserDTO request = new CreateStudioUserDTO();
        request.setUsername("operator");
        request.setPassword("a-secure-password");
        request.setAdmin(false);

        assertThat(validator.validate(request)).isEmpty();
    }
}
