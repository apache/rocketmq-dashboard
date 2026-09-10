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
package org.apache.rocketmq.studio.cluster.broker;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestConnectionDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void namesrvAddrShouldBeRequired() {
        TestConnectionDTO request = TestConnectionDTO.builder().build();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("namesrvAddr is required");
    }

    @Test
    void blankNamesrvAddrShouldBeRejected() {
        TestConnectionDTO request = TestConnectionDTO.builder().namesrvAddr("   ").build();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("namesrvAddr is required");
    }

    @Test
    void multiEntryNamesrvAddrShouldPassValidation() {
        TestConnectionDTO request = TestConnectionDTO.builder()
                .namesrvAddr("10.0.0.1:9876;10.0.0.2:9876")
                .build();

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.getNamesrvAddr()).isEqualTo("10.0.0.1:9876;10.0.0.2:9876");
    }
}
