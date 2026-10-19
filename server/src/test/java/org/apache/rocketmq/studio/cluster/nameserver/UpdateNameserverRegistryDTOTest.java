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
 * Pins the update contract of {@link UpdateNameserverRegistryDTO}: the registry entry being
 * updated is identified by its id, the name and address are required and bounded, and the
 * optional k8s fields carry their own bounds.
 */
class UpdateNameserverRegistryDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(UpdateNameserverRegistryDTO request) {
        Set<ConstraintViolation<UpdateNameserverRegistryDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void aValidEntryRoundTrips() {
        UpdateNameserverRegistryDTO request = UpdateNameserverRegistryDTO.builder()
                .id(7L)
                .name("ns-prod")
                .namesrvAddr("10.0.0.1:9876")
                .k8sNamespace("rocketmq")
                .k8sId("statefulset/rocketmq-nameserver")
                .description("static list")
                .build();
        assertThat(violationsOf(request)).isEmpty();
        assertThat(request.getId()).isEqualTo(7L);
        assertThat(request.getName()).isEqualTo("ns-prod");
    }

    @Test
    void theIdentityAndRequiredFieldsAreEnforced() {
        UpdateNameserverRegistryDTO missing = UpdateNameserverRegistryDTO.builder().build();
        assertThat(violationsOf(missing)).containsExactlyInAnyOrder("id is required", "name is required", "namesrvAddr is required");

        UpdateNameserverRegistryDTO blankName = UpdateNameserverRegistryDTO.builder().id(7L).name("   ").namesrvAddr("10.0.0.1:9876").build();
        assertThat(violationsOf(blankName)).containsExactly("name is required");
    }

    @Test
    void theOptionalFieldsAreBounded() {
        UpdateNameserverRegistryDTO overName = UpdateNameserverRegistryDTO.builder()
                .id(7L).name("n".repeat(129)).namesrvAddr("10.0.0.1:9876").build();
        assertThat(violationsOf(overName)).containsExactly("name must not exceed 128 characters");

        UpdateNameserverRegistryDTO overAddress = UpdateNameserverRegistryDTO.builder()
                .id(7L).name("ns-prod").namesrvAddr("a".repeat(513)).build();
        assertThat(violationsOf(overAddress)).containsExactly("namesrvAddr must not exceed 512 characters");

        UpdateNameserverRegistryDTO overNamespace = UpdateNameserverRegistryDTO.builder()
                .id(7L).name("ns-prod").namesrvAddr("10.0.0.1:9876").k8sNamespace("n".repeat(129)).build();
        assertThat(violationsOf(overNamespace)).containsExactly("k8sNamespace must not exceed 128 characters");
    }
}
