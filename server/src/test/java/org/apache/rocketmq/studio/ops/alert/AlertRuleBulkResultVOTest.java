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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AlertRuleBulkResultVOTest {

    @Test
    void carriesSucceededIdsFailuresAndUpdatedRules() {
        AlertRuleVO updated = AlertRuleVO.builder()
            .id(2L)
            .name("disk-high")
            .domain(AlertDomain.CLUSTER)
            .metric("broker.disk.usage_ratio")
            .operator(">")
            .threshold(0.9)
            .build();

        AlertRuleBulkResultVO result = AlertRuleBulkResultVO.builder()
            .succeededIds(List.of(1L, 2L))
            .failures(Map.of(3L, "rule not found"))
            .updatedRules(List.of(updated))
            .build();

        assertEquals(List.of(1L, 2L), result.getSucceededIds());
        assertEquals(Map.of(3L, "rule not found"), result.getFailures());
        assertEquals(List.of(updated), result.getUpdatedRules());
    }

    @Test
    void builderLeavesUntouchedFieldsNull() {
        AlertRuleBulkResultVO result = AlertRuleBulkResultVO.builder().build();

        assertNull(result.getSucceededIds());
        assertNull(result.getFailures());
        assertNull(result.getUpdatedRules());
    }
}
