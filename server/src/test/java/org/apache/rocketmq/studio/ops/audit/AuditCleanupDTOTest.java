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
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the audit cleanup contract of {@link AuditCleanupDTO}: the retention window must be at
 * least one day and at most a year - a cleanup can neither delete fresh audit records nor be
 * deferred forever. (A separate runtime guard also refuses to delete records newer than
 * {@code beforeDays}.)
 */
class AuditCleanupDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(AuditCleanupDTO request) {
        Set<ConstraintViolation<AuditCleanupDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void theWindowMustBeGreaterThanZero() {
        AuditCleanupDTO request = AuditCleanupDTO.builder().beforeDays(0).build();
        assertThat(violationsOf(request)).containsExactly("beforeDays must be greater than 0");
    }

    @Test
    void theWindowIsCappedAtOneYear() {
        AuditCleanupDTO request = AuditCleanupDTO.builder().beforeDays(366).build();
        assertThat(violationsOf(request)).containsExactly("beforeDays must not exceed 365");
    }

    @Test
    void theBoundariesAreValid() {
        assertThat(violationsOf(AuditCleanupDTO.builder().beforeDays(1).build())).isEmpty();
        assertThat(violationsOf(AuditCleanupDTO.builder().beforeDays(365).build())).isEmpty();
    }
}
