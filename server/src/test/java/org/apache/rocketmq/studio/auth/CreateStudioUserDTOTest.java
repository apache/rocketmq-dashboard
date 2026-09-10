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
    void usernameShouldBeRequiredAndBounded() {
        CreateStudioUserDTO missing = new CreateStudioUserDTO();
        missing.setPassword("secret-123");

        CreateStudioUserDTO longName = new CreateStudioUserDTO();
        longName.setUsername("u".repeat(129));
        longName.setPassword("secret-123");

        assertThat(validator.validate(missing))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("Username is required");
        assertThat(validator.validate(longName))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("Username must not exceed 128 characters");
    }

    @Test
    void passwordShouldBeRequiredAndWithinBounds() {
        CreateStudioUserDTO missing = new CreateStudioUserDTO();
        missing.setUsername("studio-admin");

        CreateStudioUserDTO shortOne = new CreateStudioUserDTO();
        shortOne.setUsername("studio-admin");
        shortOne.setPassword("short");

        assertThat(validator.validate(missing))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("Password is required");
        assertThat(validator.validate(shortOne))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("Password must contain 8 to 256 characters");
    }

    @Test
    void validAccountShouldPassValidation() {
        CreateStudioUserDTO request = new CreateStudioUserDTO();
        request.setUsername("studio-admin");
        request.setPassword("secret-123");
        request.setAdmin(true);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void toStringShouldNeverExposeThePassword() {
        CreateStudioUserDTO request = new CreateStudioUserDTO();
        request.setUsername("studio-admin");
        request.setPassword("secret-123");

        String value = request.toString();

        assertThat(value).contains("username=studio-admin");
        assertThat(value).doesNotContain("secret-123");
    }
}
