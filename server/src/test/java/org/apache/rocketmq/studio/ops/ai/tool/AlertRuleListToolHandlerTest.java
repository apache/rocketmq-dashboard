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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertRuleListToolHandlerTest {

    @Test
    void executeShouldDelegateAndProjectRules() {
        AlertService service = mock(AlertService.class);
        AlertRuleVO rule = AlertRuleVO.builder()
                .id(5L)
                .name("Broker disk usage")
                .metric("broker.disk.usage_ratio")
                .operator(">=")
                .threshold(85)
                .thresholdUnit("%")
                .duration("5m")
                .channels(List.of("dingtalk"))
                .enabled(true)
                .description("disk high")
                .build();
        PageResult<AlertRuleVO> page = PageResult.of(List.of(rule), 1, 1, 20);
        when(service.listRules(null, null, 1, 20)).thenReturn(page);

        Object output = new AlertRuleListToolHandler(service).execute(Map.of());

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) output;
        Map<?, ?> row = (Map<?, ?>) ((List<?>) result.get("items")).get(0);
        assertThat(row.get("id")).isEqualTo(5L);
        assertThat(row.get("name")).isEqualTo("Broker disk usage");
        assertThat(row.get("metric")).isEqualTo("broker.disk.usage_ratio");
        assertThat(row.get("operator")).isEqualTo(">=");
        assertThat(row.get("threshold")).isEqualTo(85.0);
        assertThat(row.get("thresholdUnit")).isEqualTo("%");
        assertThat(row.get("channels")).isEqualTo(List.of("dingtalk"));
        assertThat(row.get("enabled")).isEqualTo(true);
        verify(service).listRules(null, null, 1, 20);
    }

    @Test
    void executeShouldForwardSearchEnabledAndPaging() {
        AlertService service = mock(AlertService.class);
        PageResult<AlertRuleVO> page = PageResult.of(List.of(), 0, 2, 10);
        when(service.listRules("disk", false, 2, 10)).thenReturn(page);

        new AlertRuleListToolHandler(service).execute(Map.of(
                "search", "disk", "enabled", false, "page", 2, "pageSize", 10));

        verify(service).listRules("disk", false, 2, 10);
    }

    @Test
    void handlerNameShouldBeRmqAlertRuleList() {
        assertThat(new AlertRuleListToolHandler(mock(AlertService.class)).name())
                .isEqualTo("rmq.alert.rule.list");
    }

    @Test
    void missingNameOrMetricIsRejected() {
        AlertService service = mock(AlertService.class);
        AlertRuleVO noName = AlertRuleVO.builder().metric("broker.up").build();
        AlertRuleVO noMetric = AlertRuleVO.builder().name("Broker up").build();
        when(service.listRules(null, null, 1, 20))
                .thenReturn(PageResult.of(List.of(noName), 1, 1, 20),
                        PageResult.of(List.of(noMetric), 1, 1, 20));

        AlertRuleListToolHandler handler = new AlertRuleListToolHandler(service);
        assertThatThrownBy(() -> handler.execute(Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Alert rule name is unavailable");
        assertThatThrownBy(() -> handler.execute(Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Alert rule metric is unavailable");
    }

    @Test
    void absentOptionalFieldsProjectAsBlanksAndEmptyChannels() {
        AlertService service = mock(AlertService.class);
        AlertRuleVO rule = AlertRuleVO.builder()
                .id(9L)
                .name("Broker up")
                .metric("broker.up")
                .build();
        PageResult<AlertRuleVO> page = PageResult.of(List.of(rule), 1, 1, 20);
        when(service.listRules(null, null, 1, 20)).thenReturn(page);

        Object output = new AlertRuleListToolHandler(service).execute(Map.of());

        Map<?, ?> row = (Map<?, ?>) ((List<?>) ((Map<?, ?>) output).get("items")).get(0);
        assertThat(row.get("operator")).isEqualTo("");
        assertThat(row.get("thresholdUnit")).isEqualTo("");
        assertThat(row.get("duration")).isEqualTo("");
        assertThat(row.get("description")).isEqualTo("");
        assertThat(row.get("channels")).isEqualTo(List.of());
        assertThat(row.get("enabled")).isEqualTo(false);
    }
}
