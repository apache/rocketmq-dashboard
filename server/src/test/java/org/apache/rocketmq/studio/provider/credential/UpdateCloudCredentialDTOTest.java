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

package org.apache.rocketmq.studio.provider.credential;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the update contract of {@link UpdateCloudCredentialDTO}: the credential being
 * rotated is identified by its id, and every other field is optional — a rotation that only
 * swaps the secret key must stay valid without re-sending the name. (The secret redaction on
 * this DTO is already pinned by {@code SensitiveRequestToStringTest}; this pins the
 * validation side.)
 */
class UpdateCloudCredentialDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(UpdateCloudCredentialDTO request) {
        Set<ConstraintViolation<UpdateCloudCredentialDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void anIdAloneIsAValidRotation() {
        UpdateCloudCredentialDTO request = new UpdateCloudCredentialDTO();
        request.setId(7L);
        request.setSecretKey("rotated-secret");

        assertThat(violationsOf(request)).isEmpty();
    }

    @Test
    void aCompleteUpdatePassesValidation() {
        UpdateCloudCredentialDTO request = new UpdateCloudCredentialDTO();
        request.setId(7L);
        request.setName("aliyun-prod");
        request.setSecretKey("rotated-secret");
        request.setRemark("after incident");

        assertThat(violationsOf(request)).isEmpty();
    }

    @Test
    void theCredentialIdIsRequired() {
        UpdateCloudCredentialDTO request = new UpdateCloudCredentialDTO();
        request.setName("aliyun-prod");

        assertThat(violationsOf(request)).containsExactly("credential id is required");
    }
}
