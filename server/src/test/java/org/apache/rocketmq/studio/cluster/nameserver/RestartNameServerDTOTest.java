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
 * Pins the restart contract of {@link RestartNameServerDTO}: a restart targets exactly one
 * NameServer, so both the cluster it belongs to and the address to restart are required - a
 * request that names neither machine nor cluster must fail validation instead of restarting an
 * arbitrary node.
 */
class RestartNameServerDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(RestartNameServerDTO request) {
        Set<ConstraintViolation<RestartNameServerDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void aValidRequestPassesUntouched() {
        RestartNameServerDTO request = RestartNameServerDTO.builder()
                .clusterId("cluster-1")
                .addr("10.0.0.1:9876")
                .build();
        assertThat(violationsOf(request)).isEmpty();
        assertThat(request.getClusterId()).isEqualTo("cluster-1");
        assertThat(request.getAddr()).isEqualTo("10.0.0.1:9876");
    }

    @Test
    void theClusterIsRequired() {
        RestartNameServerDTO request = RestartNameServerDTO.builder()
                .addr("10.0.0.1:9876")
                .build();
        assertThat(violationsOf(request)).containsExactly("clusterId is required");
    }

    @Test
    void theAddressIsRequired() {
        RestartNameServerDTO request = RestartNameServerDTO.builder()
                .clusterId("cluster-1")
                .addr("   ")
                .build();
        assertThat(violationsOf(request)).containsExactly("addr is required");
    }
}
