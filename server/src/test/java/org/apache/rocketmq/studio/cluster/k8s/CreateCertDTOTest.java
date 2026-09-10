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

import static org.assertj.core.api.Assertions.assertThat;

class CreateCertDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();


    @Test
    void identityAndTypeShouldBeRequired() {
        CreateCertDTO request = CreateCertDTO.builder().build();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("k8sId is required", "cluster is required",
                        "type is required");
    }

    @Test
    void unsupportedTypeShouldBeRejected() {
        CreateCertDTO request = CreateCertDTO.builder()
                .k8sId("cluster-1")
                .cluster("rmq-prod")
                .type("client-cert")
                .build();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("type must be one of TLS, mTLS, ServiceAccount");
    }

    @Test
    void validCertShouldPassValidation() {
        CreateCertDTO request = CreateCertDTO.builder()
                .k8sId("cluster-1")
                .cluster("rmq-prod")
                .type("TLS")
                .issuer("internal-ca")
                .san(java.util.List.of("broker-0.rmq-prod.svc"))
                .build();

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.getType()).isEqualTo("TLS");
    }
}
