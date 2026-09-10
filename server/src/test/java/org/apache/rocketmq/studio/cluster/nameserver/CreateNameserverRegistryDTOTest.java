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

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreateNameserverRegistryDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();


    @Test
    void nameAndAddressShouldBeRequired() {
        CreateNameserverRegistryDTO request = CreateNameserverRegistryDTO.builder().build();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("name is required", "namesrvAddr is required");
    }

    @Test
    void oversizedFieldsShouldBeRejected() {
        CreateNameserverRegistryDTO request = CreateNameserverRegistryDTO.builder()
                .name("n".repeat(129))
                .namesrvAddr("a".repeat(513))
                .k8sNamespace("k".repeat(129))
                .k8sId("i".repeat(129))
                .build();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder(
                        "name must not exceed 128 characters",
                        "namesrvAddr must not exceed 512 characters",
                        "k8sNamespace must not exceed 128 characters",
                        "k8sId must not exceed 128 characters");
    }

    @Test
    void validRegistryShouldPassValidation() {
        CreateNameserverRegistryDTO request = CreateNameserverRegistryDTO.builder()
                .name("prod-registry")
                .namesrvAddr("10.0.0.1:9876;10.0.0.2:9876")
                .k8sNamespace("rocketmq")
                .build();

        assertThat(validator.validate(request)).isEmpty();
    }
}
