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
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeleteRequestDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private DeleteRequestDTO sample() {
        DeleteRequestDTO dto = new DeleteRequestDTO();
        dto.setId("42");
        dto.setInstanceId("inst-1");
        return dto;
    }

    @Test
    void acceptsCompletePayload() {
        assertTrue(validator.validate(sample()).isEmpty());
    }

    @Test
    void rejectsMissingId() {
        DeleteRequestDTO dto = sample();
        dto.setId(null);

        Set<ConstraintViolation<DeleteRequestDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertEquals("id is required", violations.iterator().next().getMessage());
    }

    @Test
    void rejectsBlankId() {
        DeleteRequestDTO dto = sample();
        dto.setId(" ");

        Set<ConstraintViolation<DeleteRequestDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertEquals("id is required", violations.iterator().next().getMessage());
    }

    @Test
    void instanceIdRemainsOptional() {
        DeleteRequestDTO dto = sample();
        dto.setInstanceId(null);

        assertTrue(validator.validate(dto).isEmpty());
    }
}
