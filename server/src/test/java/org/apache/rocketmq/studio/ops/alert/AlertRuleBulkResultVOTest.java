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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the bulk operation result of {@link AlertRuleBulkResultVO}: the succeeded ids, the
 * per-id failure reasons and the refreshed rules travel together - the response shape the
 * bulk toggle/delete dialogs render row by row.
 */
class AlertRuleBulkResultVOTest {

    @Test
    void theResultCarriesSuccessesFailuresAndRefreshedRules() {
        AlertRuleVO refreshed = AlertRuleVO.builder().id(7L).build();
        AlertRuleBulkResultVO result = AlertRuleBulkResultVO.builder()
                .succeededIds(List.of(7L, 8L))
                .failures(Map.of(9L, "rule not found"))
                .updatedRules(List.of(refreshed))
                .build();

        assertThat(result.getSucceededIds()).containsExactly(7L, 8L);
        assertThat(result.getFailures()).containsEntry(9L, "rule not found");
        assertThat(result.getUpdatedRules()).containsExactly(refreshed);
    }

    @Test
    void aFreshResultCarriesNothing() {
        AlertRuleBulkResultVO result = new AlertRuleBulkResultVO(null, null, null);
        assertThat(result.getSucceededIds()).isNull();
        assertThat(result.getFailures()).isNull();
        assertThat(result.getUpdatedRules()).isNull();
    }
}
