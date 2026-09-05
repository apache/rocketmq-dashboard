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

import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class AlertRuleCommandDtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void deleteAlertRuleRequiresAnIdTest() {
        DeleteAlertRuleDTO request = DeleteAlertRuleDTO.builder().build();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactly("id is required");
    }

    @Test
    void acknowledgeSystemAlertRequiresAnIdTest() {
        AcknowledgeSystemAlertDTO request = AcknowledgeSystemAlertDTO.builder().build();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactly("id is required");
    }

    @Test
    void bulkToggleRequiresIdsAndEnabledTest() {
        BulkToggleAlertRulesDTO empty = new BulkToggleAlertRulesDTO();
        empty.setIds(List.of());
        empty.setEnabled(null);

        assertThat(validator.validate(empty))
                .extracting(violation -> violation.getMessage())
                .contains("ids are required", "enabled is required");
    }

    @Test
    void bulkToggleRejectsMoreThanOneHundredIdsTest() {
        BulkToggleAlertRulesDTO tooMany = new BulkToggleAlertRulesDTO();
        tooMany.setIds(IntStream.rangeClosed(1, 101).mapToLong(Long::valueOf).boxed()
                .collect(Collectors.toCollection(ArrayList::new)));
        tooMany.setEnabled(true);

        assertThat(validator.validate(tooMany))
                .extracting(violation -> violation.getMessage())
                .contains("at most 100 rule ids are allowed");
    }

    @Test
    void validBulkTogglePassesValidationTest() {
        BulkToggleAlertRulesDTO request = new BulkToggleAlertRulesDTO();
        request.setIds(List.of(1L, 2L));
        request.setEnabled(true);

        assertThat(validator.validate(request)).isEmpty();
    }
}
