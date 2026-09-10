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

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateCertDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void missingIdIsRejectedTest() {
        UpdateCertDTO request = new UpdateCertDTO();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactly("id is required");
    }

    @Test
    void unsupportedTypeIsRejectedTest() {
        UpdateCertDTO request = UpdateCertDTO.builder()
                .id(1L)
                .type("PEM")
                .build();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactly("type must be one of TLS, mTLS, ServiceAccount");
    }

    @Test
    void completeRequestPassesValidationTest() {
        UpdateCertDTO request = UpdateCertDTO.builder()
                .id(1L)
                .k8sId("cert-1")
                .type("TLS")
                .issuer("rocketmq-issuer")
                .san(List.of("broker-a", "broker-b"))
                .build();

        assertThat(validator.validate(request)).isEmpty();
    }
}
