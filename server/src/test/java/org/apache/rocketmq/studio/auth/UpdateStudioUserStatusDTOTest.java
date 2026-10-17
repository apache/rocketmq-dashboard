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
 * Pins the status update contract of {@link UpdateStudioUserStatusDTO}: the enabled flag must be
 * explicitly present - an absent flag must never silently default a user's account state.
 */
class UpdateStudioUserStatusDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(UpdateStudioUserStatusDTO request) {
        Set<ConstraintViolation<UpdateStudioUserStatusDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void bothStatesAreValid() {
        UpdateStudioUserStatusDTO enabled = new UpdateStudioUserStatusDTO();
        enabled.setEnabled(true);
        assertThat(violationsOf(enabled)).isEmpty();

        UpdateStudioUserStatusDTO disabled = new UpdateStudioUserStatusDTO();
        disabled.setEnabled(false);
        assertThat(violationsOf(disabled)).isEmpty();
    }

    @Test
    void anAbsentFlagIsRejected() {
        assertThat(violationsOf(new UpdateStudioUserStatusDTO()))
                .containsExactly("Enabled is required");
    }
}
