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
package org.apache.rocketmq.studio.ops.alert;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulkDeleteAlertRulesDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> messagesOf(BulkDeleteAlertRulesDTO dto) {
        return validator.validate(dto).stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.toSet());
    }

    @Test
    void acceptsNonEmptyIdList() {
        BulkDeleteAlertRulesDTO dto = new BulkDeleteAlertRulesDTO();
        dto.setIds(List.of(1L, 2L));

        assertTrue(validator.validate(dto).isEmpty());
    }

    @Test
    void rejectsNullIds() {
        BulkDeleteAlertRulesDTO dto = new BulkDeleteAlertRulesDTO();

        Set<String> messages = messagesOf(dto);

        assertTrue(messages.contains("ids are required"));
    }

    @Test
    void rejectsEmptyIdList() {
        BulkDeleteAlertRulesDTO dto = new BulkDeleteAlertRulesDTO();
        dto.setIds(List.of());

        Set<String> messages = messagesOf(dto);

        assertTrue(messages.contains("ids are required"));
    }

    @Test
    void rejectsNullIdInsideList() {
        BulkDeleteAlertRulesDTO dto = new BulkDeleteAlertRulesDTO();
        List<Long> ids = new ArrayList<>();
        ids.add(1L);
        ids.add(null);
        dto.setIds(ids);

        Set<String> messages = messagesOf(dto);

        assertTrue(messages.contains("rule id is required"));
    }

    @Test
    void rejectsMoreThanHundredIds() {
        BulkDeleteAlertRulesDTO dto = new BulkDeleteAlertRulesDTO();
        List<Long> many = new ArrayList<>();
        for (long i = 1; i <= 101; i++) {
            many.add(i);
        }
        dto.setIds(many);

        Set<String> messages = messagesOf(dto);

        assertTrue(messages.contains("at most 100 rule ids are allowed"));
    }

    @Test
    void acceptsExactlyHundredIds() {
        BulkDeleteAlertRulesDTO dto = new BulkDeleteAlertRulesDTO();
        List<Long> many = new ArrayList<>();
        for (long i = 1; i <= 100; i++) {
            many.add(i);
        }
        dto.setIds(many);

        assertEquals(0, messagesOf(dto).size());
    }
}
