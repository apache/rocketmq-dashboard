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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestConnectionDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsNamesrvAddr() {
        TestConnectionDTO dto = TestConnectionDTO.builder()
            .namesrvAddr("10.132.218.11:9876")
            .build();

        Set<ConstraintViolation<TestConnectionDTO>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty());
    }

    @Test
    void rejectsMissingNamesrvAddr() {
        TestConnectionDTO dto = TestConnectionDTO.builder().build();

        Set<ConstraintViolation<TestConnectionDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertEquals("namesrvAddr is required", violations.iterator().next().getMessage());
    }

    @Test
    void rejectsBlankNamesrvAddr() {
        TestConnectionDTO dto = TestConnectionDTO.builder()
            .namesrvAddr("  ")
            .build();

        Set<ConstraintViolation<TestConnectionDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertEquals("namesrvAddr is required", violations.iterator().next().getMessage());
    }

    @Test
    void acceptsSemicolonSeparatedClusterAddresses() {
        TestConnectionDTO dto = TestConnectionDTO.builder()
            .namesrvAddr("10.0.0.1:9876;10.0.0.2:9876")
            .build();

        assertTrue(validator.validate(dto).isEmpty());
    }
}
