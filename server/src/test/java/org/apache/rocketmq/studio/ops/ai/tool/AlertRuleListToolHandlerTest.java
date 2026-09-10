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
package org.apache.rocketmq.studio.ops.ai.tool;

import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.ops.alert.AlertRuleVO;
import org.apache.rocketmq.studio.ops.alert.AlertService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AlertRuleListToolHandler}: the list-style tool delegates to the
 * alert service with its filter/paging inputs and projects each alert rule row.
 */
@ExtendWith(MockitoExtension.class)
class AlertRuleListToolHandlerTest {

    @Mock
    private AlertService alertService;

    @InjectMocks
    private AlertRuleListToolHandler handler;

    private static AlertRuleVO rule() {
        return AlertRuleVO.builder()
                .id(1L)
                .name("CPU High")
                .metric("cpu")
                .operator(">")
                .threshold(90)
                .duration("1m")
                .enabled(true)
                .description("high cpu")
                .build();
    }

    @Test
    void reportsItsToolName() {
        assertThat(handler.name()).isEqualTo("rmq.alert.rule.list");
    }

    @Test
    @SuppressWarnings("unchecked")
    void delegatesAndProjectsTheRuleRows() {
        AlertRuleVO rule = rule();
        rule.setChannels(new ArrayList<>(List.of("email")));
        when(alertService.listRules("cpu", true, 1, 20))
                .thenReturn(PageResult.of(List.of(rule), 1L, 1, 20));

        Map<String, Object> result = (Map<String, Object>) handler.execute(
                Map.of("search", "cpu", "enabled", true));

        Map<String, Object> row = (Map<String, Object>) ((List<?>) result.get("items")).get(0);
        assertThat(row.get("id")).isEqualTo(1L);
        assertThat(row.get("name")).isEqualTo("CPU High");
        assertThat(row.get("metric")).isEqualTo("cpu");
        assertThat(row.get("operator")).isEqualTo(">");
        assertThat(row.get("threshold")).isEqualTo(90D);
        assertThat(row.get("thresholdUnit")).isEqualTo("");
        assertThat(row.get("duration")).isEqualTo("1m");
        assertThat(row.get("channels")).isEqualTo(List.of("email"));
        assertThat(row.get("enabled")).isEqualTo(true);
        assertThat(row.get("description")).isEqualTo("high cpu");
        assertThat(result.get("total")).isEqualTo(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void defaultsThePageWindowAndOptionalFilters() {
        when(alertService.listRules(null, null, 1, 20)).thenReturn(PageResult.empty(1, 20));

        Map<String, Object> result = (Map<String, Object>) handler.execute(Map.of());

        assertThat(result.get("items")).isEqualTo(List.of());
        assertThat(result.get("total")).isEqualTo(0L);
        verify(alertService).listRules(isNull(), isNull(), eq(1), eq(20));
    }

    @Test
    void rejectsRulesMissingRequiredProjectionFields() {
        AlertRuleVO rule = rule();
        rule.setMetric(null);
        when(alertService.listRules(null, null, 1, 20))
                .thenReturn(PageResult.of(List.of(rule), 1L, 1, 20));

        assertThatThrownBy(() -> handler.execute(Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("metric");
    }
}
