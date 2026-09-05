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

class CreateNameServerDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private CreateNameServerDTO validPayload() {
        return CreateNameServerDTO.builder()
            .clusterId("cluster-1")
            .addr("10.132.218.11:9876")
            .version("5.3.2")
            .build();
    }

    @Test
    void acceptsCompletePayload() {
        Set<ConstraintViolation<CreateNameServerDTO>> violations = validator.validate(validPayload());
        assertTrue(violations.isEmpty());
    }

    @Test
    void rejectsMissingClusterId() {
        CreateNameServerDTO dto = validPayload();
        dto.setClusterId(null);

        Set<ConstraintViolation<CreateNameServerDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertEquals("clusterId is required", violations.iterator().next().getMessage());
    }

    @Test
    void rejectsBlankClusterId() {
        CreateNameServerDTO dto = validPayload();
        dto.setClusterId("   ");

        Set<ConstraintViolation<CreateNameServerDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertEquals("clusterId is required", violations.iterator().next().getMessage());
    }

    @Test
    void rejectsMissingAddr() {
        CreateNameServerDTO dto = validPayload();
        dto.setAddr(null);

        Set<ConstraintViolation<CreateNameServerDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertEquals("addr is required", violations.iterator().next().getMessage());
    }

    @Test
    void rejectsBlankAddr() {
        CreateNameServerDTO dto = validPayload();
        dto.setAddr(" ");

        Set<ConstraintViolation<CreateNameServerDTO>> violations = validator.validate(dto);

        assertEquals(1, violations.size());
        assertEquals("addr is required", violations.iterator().next().getMessage());
    }

    @Test
    void versionRemainsOptional() {
        CreateNameServerDTO dto = validPayload();
        dto.setVersion(null);

        Set<ConstraintViolation<CreateNameServerDTO>> violations = validator.validate(dto);

        assertTrue(violations.isEmpty());
    }
}
