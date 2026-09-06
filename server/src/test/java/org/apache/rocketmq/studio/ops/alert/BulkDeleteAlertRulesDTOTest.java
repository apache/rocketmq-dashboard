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

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BulkDeleteAlertRulesDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();


    @Test
    void idsShouldBeRequired() {
        BulkDeleteAlertRulesDTO request = new BulkDeleteAlertRulesDTO();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("ids are required");
    }

    @Test
    void idListShouldBeBoundedAndNonNull() {
        java.util.List<Long> many = new java.util.ArrayList<>();
        for (long i = 0; i < 101; i++) {
            many.add(i);
        }
        BulkDeleteAlertRulesDTO oversized = new BulkDeleteAlertRulesDTO();
        oversized.setIds(many);

        BulkDeleteAlertRulesDTO nullEntry = new BulkDeleteAlertRulesDTO();
        nullEntry.setIds(java.util.Arrays.asList(1L, null));

        assertThat(validator.validate(oversized))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("at most 100 rule ids are allowed");
        assertThat(validator.validate(nullEntry))
                .extracting(violation -> violation.getMessage())
                .containsExactlyInAnyOrder("rule id is required");
    }

    @Test
    void validBulkDeleteShouldPassValidation() {
        BulkDeleteAlertRulesDTO request = new BulkDeleteAlertRulesDTO();
        request.setIds(java.util.List.of(1L, 2L));

        assertThat(validator.validate(request)).isEmpty();
    }
}
