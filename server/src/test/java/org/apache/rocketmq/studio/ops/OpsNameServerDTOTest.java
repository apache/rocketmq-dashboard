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
package org.apache.rocketmq.studio.ops;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsNameServerDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private OpsNameServerDTO sample() {
        OpsNameServerDTO dto = new OpsNameServerDTO();
        dto.setNamesrvAddr("10.132.218.11:9876");
        return dto;
    }

    @Test
    void acceptsNamesrvAddr() {
        assertTrue(validator.validate(sample()).isEmpty());
    }

    @Test
    void acceptsMultiAddressList() {
        OpsNameServerDTO dto = sample();
        dto.setNamesrvAddr("10.0.0.1:9876;10.0.0.2:9876");

        assertTrue(validator.validate(dto).isEmpty());
    }

    @Test
    void rejectsMissingNamesrvAddr() {
        OpsNameServerDTO dto = new OpsNameServerDTO();

        Set<ConstraintViolation<OpsNameServerDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertEquals("namesrvAddr is required", violations.iterator().next().getMessage());
    }

    @Test
    void rejectsBlankNamesrvAddr() {
        OpsNameServerDTO dto = sample();
        dto.setNamesrvAddr(" ");

        Set<ConstraintViolation<OpsNameServerDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertEquals("namesrvAddr is required", violations.iterator().next().getMessage());
    }
}
