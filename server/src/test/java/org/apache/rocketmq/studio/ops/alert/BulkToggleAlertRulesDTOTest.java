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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the bulk-toggle contract of {@link BulkToggleAlertRulesDTO}: the rule ids are required,
 * capped at 100 and may not contain nulls, and the enabled flag must be present.
 */
class BulkToggleAlertRulesDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violationsOf(BulkToggleAlertRulesDTO request) {
        Set<ConstraintViolation<BulkToggleAlertRulesDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    private BulkToggleAlertRulesDTO request(List<Long> ids, Boolean enabled) {
        BulkToggleAlertRulesDTO request = new BulkToggleAlertRulesDTO();
        request.setIds(ids);
        request.setEnabled(enabled);
        return request;
    }

    private static List<Long> ids(int count) {
        List<Long> ids = new ArrayList<>(count);
        for (long i = 1; i <= count; i++) {
            ids.add(i);
        }
        return ids;
    }

    @Test
    void aValidRequestPassesUntouched() {
        assertThat(violationsOf(request(ids(2), true))).isEmpty();
        assertThat(violationsOf(request(ids(2), false))).isEmpty();
    }

    @Test
    void theIdsAreRequired() {
        assertThat(violationsOf(request(null, true))).containsExactly("ids are required");
        assertThat(violationsOf(request(new ArrayList<>(), true))).containsExactly("ids are required");
    }

    @Test
    void atMostOneHundredRuleIdsAreAllowed() {
        assertThat(violationsOf(request(ids(100), true))).isEmpty();
        assertThat(violationsOf(request(ids(101), true)))
                .containsExactly("at most 100 rule ids are allowed");
    }

    @Test
    void aNullRuleIdIsRejected() {
        List<Long> withNull = new ArrayList<>(ids(1));
        withNull.add(null);
        assertThat(violationsOf(request(withNull, true))).containsExactly("rule id is required");
    }

    @Test
    void theEnabledFlagIsRequired() {
        assertThat(violationsOf(request(ids(1), null))).containsExactly("enabled is required");
    }
}
