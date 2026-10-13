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
package org.apache.rocketmq.studio.cluster.nameserver;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the create contract of {@link CreateNameserverRegistryDTO}: the name and address are
 * required and bounded, the k8s discovery fields are optional but bounded.
 */
class CreateNameserverRegistryDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(CreateNameserverRegistryDTO request) {
        Set<ConstraintViolation<CreateNameserverRegistryDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void aMinimalEntryIsValid() {
        CreateNameserverRegistryDTO request = CreateNameserverRegistryDTO.builder()
                .name("ns-prod")
                .namesrvAddr("10.0.0.1:9876")
                .build();
        assertThat(violationsOf(request)).isEmpty();
    }

    @Test
    void theRequiredFieldsAreEnforced() {
        assertThat(violationsOf(CreateNameserverRegistryDTO.builder().build()))
                .containsExactlyInAnyOrder("name is required", "namesrvAddr is required");

        CreateNameserverRegistryDTO blankAddress = CreateNameserverRegistryDTO.builder()
                .name("ns-prod").namesrvAddr("   ").build();
        assertThat(violationsOf(blankAddress)).containsExactly("namesrvAddr is required");
    }

    @Test
    void theOptionalFieldsAreBounded() {
        CreateNameserverRegistryDTO overK8sId = CreateNameserverRegistryDTO.builder()
                .name("ns-k8s").namesrvAddr("10.0.0.1:9876").k8sId("k".repeat(129)).build();
        assertThat(violationsOf(overK8sId)).containsExactly("k8sId must not exceed 128 characters");

        CreateNameserverRegistryDTO overName = CreateNameserverRegistryDTO.builder()
                .name("n".repeat(129)).namesrvAddr("10.0.0.1:9876").build();
        assertThat(violationsOf(overName)).containsExactly("name must not exceed 128 characters");
    }
}
