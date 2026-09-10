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
package org.apache.rocketmq.studio.ops.audit;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditCleanupDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> messagesOf(AuditCleanupDTO dto) {
        return validator.validate(dto).stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.toSet());
    }

    @Test
    void acceptsPositiveWithinLimit() {
        AuditCleanupDTO dto = AuditCleanupDTO.builder().beforeDays(30).build();
        assertTrue(validator.validate(dto).isEmpty());
    }

    @Test
    void acceptsBoundaryOfThreeHundredSixtyFiveDays() {
        AuditCleanupDTO dto = AuditCleanupDTO.builder().beforeDays(365).build();
        assertTrue(validator.validate(dto).isEmpty());
    }

    @Test
    void rejectsZero() {
        AuditCleanupDTO dto = AuditCleanupDTO.builder().beforeDays(0).build();
        Set<String> messages = messagesOf(dto);
        assertTrue(messages.contains("beforeDays must be greater than 0"));
    }

    @Test
    void rejectsNegative() {
        AuditCleanupDTO dto = AuditCleanupDTO.builder().beforeDays(-7).build();
        Set<String> messages = messagesOf(dto);
        assertTrue(messages.contains("beforeDays must be greater than 0"));
    }

    @Test
    void rejectsBeyondThreeHundredSixtyFiveDays() {
        AuditCleanupDTO dto = AuditCleanupDTO.builder().beforeDays(366).build();
        Set<String> messages = messagesOf(dto);
        assertTrue(messages.contains("beforeDays must not exceed 365"));
    }

    @Test
    void fieldIsOptional() {
        AuditCleanupDTO dto = new AuditCleanupDTO();
        assertTrue(validator.validate(dto).isEmpty());
    }
}
