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
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeleteNameserverRegistryDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private DeleteNameserverRegistryDTO sample() {
        return DeleteNameserverRegistryDTO.builder()
            .id(11L)
            .build();
    }

    @Test
    void acceptsNumericId() {
        assertTrue(validator.validate(sample()).isEmpty());
    }

    @Test
    void rejectsMissingId() {
        DeleteNameserverRegistryDTO dto = new DeleteNameserverRegistryDTO();

        Set<ConstraintViolation<DeleteNameserverRegistryDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertEquals("id is required", violations.iterator().next().getMessage());
    }
}
