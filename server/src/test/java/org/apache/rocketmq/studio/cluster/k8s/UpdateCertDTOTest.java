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

package org.apache.rocketmq.studio.cluster.k8s;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the update contract of {@link UpdateCertDTO}: the certificate being updated is
 * identified by its id, and every other field is optional — but a supplied {@code type}
 * must still be one of the three supported kinds (TLS, mTLS, ServiceAccount). A PATCH that
 * changes only the SAN list must stay valid without re-sending the whole certificate.
 */
class UpdateCertDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(UpdateCertDTO request) {
        Set<ConstraintViolation<UpdateCertDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void anIdAloneIsAValidPartialUpdate() {
        UpdateCertDTO request = UpdateCertDTO.builder().id(7L).build();
        assertThat(violationsOf(request)).isEmpty();
    }

    @Test
    void aCompleteUpdatePassesValidation() {
        UpdateCertDTO request = UpdateCertDTO.builder()
                .id(7L)
                .k8sId("statefulset/rocketmq-k8s")
                .cluster("production-cluster")
                .type("mTLS")
                .issuer("CN=rocketmq-ca")
                .san(java.util.List.of("rocketmq-broker-0"))
                .build();
        assertThat(violationsOf(request)).isEmpty();
    }

    @Test
    void theIdIsRequired() {
        UpdateCertDTO request = UpdateCertDTO.builder().type("TLS").build();
        assertThat(violationsOf(request)).containsExactly("id is required");
    }

    @Test
    void aSuppliedTypeMustBeOneOfTheThreeKinds() {
        for (String supported : new String[] {"TLS", "mTLS", "ServiceAccount"}) {
            UpdateCertDTO request = UpdateCertDTO.builder().id(7L).type(supported).build();
            assertThat(violationsOf(request)).isEmpty();
        }

        UpdateCertDTO unsupported = UpdateCertDTO.builder().id(7L).type("ssl").build();
        assertThat(violationsOf(unsupported))
                .containsExactly("type must be one of TLS, mTLS, ServiceAccount");
    }
}
