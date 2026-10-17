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
package org.apache.rocketmq.studio.instance;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the cloud import contract of {@link ImportCloudInstancesDTO}: both the vendor and the
 * credential are required - an import that names neither must fail validation instead of
 * importing nothing or guessing a cloud.
 */
class ImportCloudInstancesDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(ImportCloudInstancesDTO request) {
        Set<ConstraintViolation<ImportCloudInstancesDTO>> violations = validator.validate(request);
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    void aRequestNamingAVendorAndCredentialIsValid() {
        ImportCloudInstancesDTO request = new ImportCloudInstancesDTO();
        request.setVendor(InstanceVendor.ALIYUN);
        request.setCredentialId(11L);
        assertThat(violationsOf(request)).isEmpty();
    }

    @Test
    void theVendorAndTheCredentialAreRequired() {
        assertThat(violationsOf(new ImportCloudInstancesDTO())).containsExactlyInAnyOrder("vendor", "credentialId");

        ImportCloudInstancesDTO noVendor = new ImportCloudInstancesDTO();
        noVendor.setCredentialId(11L);
        assertThat(violationsOf(noVendor)).containsExactly("vendor");

        ImportCloudInstancesDTO noCredential = new ImportCloudInstancesDTO();
        noCredential.setVendor(InstanceVendor.TENCENT);
        assertThat(violationsOf(noCredential)).containsExactly("credentialId");
    }
}
