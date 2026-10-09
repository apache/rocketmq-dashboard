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
 * Pins the update contract of {@link UpdateNameServerDTO}: the cluster and the address are
 * required, while the version is an optional diagnostic the update does not depend on.
 */
class UpdateNameServerDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(UpdateNameServerDTO request) {
        Set<ConstraintViolation<UpdateNameServerDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void aValidRequestPassesUntouched() {
        UpdateNameServerDTO request = UpdateNameServerDTO.builder()
                .clusterId("cluster-1")
                .addr("10.0.0.1:9876")
                .version("5.5.0")
                .build();
        assertThat(violationsOf(request)).isEmpty();
        assertThat(request.getClusterId()).isEqualTo("cluster-1");
        assertThat(request.getAddr()).isEqualTo("10.0.0.1:9876");
        assertThat(request.getVersion()).isEqualTo("5.5.0");
    }

    @Test
    void theVersionIsOptional() {
        UpdateNameServerDTO request = UpdateNameServerDTO.builder()
                .clusterId("cluster-1")
                .addr("10.0.0.1:9876")
                .build();
        assertThat(violationsOf(request)).isEmpty();
        assertThat(request.getVersion()).isNull();
    }

    @Test
    void theClusterAndTheAddressAreRequired() {
        UpdateNameServerDTO noCluster = UpdateNameServerDTO.builder()
                .addr("10.0.0.1:9876")
                .build();
        assertThat(violationsOf(noCluster)).containsExactly("clusterId is required");

        UpdateNameServerDTO noAddress = UpdateNameServerDTO.builder()
                .clusterId("cluster-1")
                .build();
        assertThat(violationsOf(noAddress)).containsExactly("addr is required");
    }
}
