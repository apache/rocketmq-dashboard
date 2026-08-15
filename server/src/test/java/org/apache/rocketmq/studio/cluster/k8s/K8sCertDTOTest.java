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
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class K8sCertDTOTest {

    @Test
    void createShouldRejectNullAndBlankSanValues() {
        CreateCertDTO command = CreateCertDTO.builder()
                .name("broker")
                .namespace("rocketmq")
                .cluster("production")
                .type("TLS")
                .issuer("cluster-ca")
                .san(Arrays.asList(null, " "))
                .build();

        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(command))
                    .extracting(violation -> violation.getMessage())
                    .containsOnly("san must not contain blank values");
        }
    }

    @Test
    void updateShouldRejectBlankSanValues() {
        UpdateCertDTO command = UpdateCertDTO.builder()
                .id("cert-1")
                .san(List.of("broker.example", ""))
                .build();

        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(command))
                    .extracting(violation -> violation.getMessage())
                    .containsExactly("san must not contain blank values");
        }
    }
}
