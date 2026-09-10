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

class DeleteNameServerDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private DeleteNameServerDTO sample() {
        return DeleteNameServerDTO.builder()
            .clusterId("cluster-1")
            .addr("10.132.218.11:9876")
            .build();
    }

    @Test
    void acceptsCompletePayload() {
        assertTrue(validator.validate(sample()).isEmpty());
    }

    @Test
    void rejectsMissingClusterId() {
        DeleteNameServerDTO dto = sample();
        dto.setClusterId(null);

        Set<ConstraintViolation<DeleteNameServerDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertEquals("clusterId is required", violations.iterator().next().getMessage());
    }

    @Test
    void rejectsBlankClusterId() {
        DeleteNameServerDTO dto = sample();
        dto.setClusterId("  ");

        Set<ConstraintViolation<DeleteNameServerDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertEquals("clusterId is required", violations.iterator().next().getMessage());
    }

    @Test
    void rejectsMissingAddr() {
        DeleteNameServerDTO dto = sample();
        dto.setAddr(null);

        Set<ConstraintViolation<DeleteNameServerDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertEquals("addr is required", violations.iterator().next().getMessage());
    }

    @Test
    void rejectsBlankAddr() {
        DeleteNameServerDTO dto = sample();
        dto.setAddr(" ");

        Set<ConstraintViolation<DeleteNameServerDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertEquals("addr is required", violations.iterator().next().getMessage());
    }
}
