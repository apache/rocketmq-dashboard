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
package org.apache.rocketmq.studio.common.domain;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the shared delete contract of {@link DeleteRequestDTO}: the id is required as a String
 * (some backends, like the Tencent Cloud ACL, identify resources by name), and the optional
 * instance id routes the delete to a cloud-vendor backend.
 */
class DeleteRequestDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(DeleteRequestDTO request) {
        Set<ConstraintViolation<DeleteRequestDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void theIdIsRequired() {
        DeleteRequestDTO missing = new DeleteRequestDTO();
        assertThat(violationsOf(missing)).containsExactly("id is required");

        DeleteRequestDTO blank = new DeleteRequestDTO();
        blank.setId("   ");
        assertThat(violationsOf(blank)).containsExactly("id is required");
    }

    @Test
    void aNameShapedIdIsValid() {
        DeleteRequestDTO request = new DeleteRequestDTO();
        request.setId("arn::tencent::account-1");

        assertThat(violationsOf(request)).isEmpty();
        assertThat(request.getId()).isEqualTo("arn::tencent::account-1");
        assertThat(request.getInstanceId()).isNull();
    }

    @Test
    void theInstanceIdIsOptionalRouting() {
        DeleteRequestDTO request = new DeleteRequestDTO();
        request.setId("42");
        request.setInstanceId("instance-1");

        assertThat(violationsOf(request)).isEmpty();
        assertThat(request.getInstanceId()).isEqualTo("instance-1");
    }
}
