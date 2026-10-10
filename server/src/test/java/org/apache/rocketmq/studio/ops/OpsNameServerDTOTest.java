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
package org.apache.rocketmq.studio.ops;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the update contract of {@link OpsNameServerDTO}: the NameServer address list the
 * operations endpoint installs must be present - an absent or blank address must fail
 * validation instead of silently pointing every client at nothing.
 */
class OpsNameServerDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(OpsNameServerDTO request) {
        Set<ConstraintViolation<OpsNameServerDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void anAddressListIsValid() {
        OpsNameServerDTO request = new OpsNameServerDTO();
        request.setNamesrvAddr("10.0.0.1:9876;10.0.0.2:9876");
        assertThat(violationsOf(request)).isEmpty();
        assertThat(request.getNamesrvAddr()).isEqualTo("10.0.0.1:9876;10.0.0.2:9876");
    }

    @Test
    void aMissingAddressIsRejected() {
        assertThat(violationsOf(new OpsNameServerDTO()))
                .containsExactly("namesrvAddr is required");
    }

    @Test
    void aBlankAddressIsRejected() {
        OpsNameServerDTO request = new OpsNameServerDTO();
        request.setNamesrvAddr("   ");
        assertThat(violationsOf(request)).containsExactly("namesrvAddr is required");
    }
}
