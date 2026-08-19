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

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.ops.alert.AlertRuleVO;
import org.apache.rocketmq.studio.ops.alert.AlertService;
import org.apache.rocketmq.studio.ops.dashboard.ClusterOverviewVO;
import org.apache.rocketmq.studio.ops.dashboard.DashboardDataVO;
import org.apache.rocketmq.studio.ops.dashboard.DashboardService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReadToolResultSanitizationTest {

    @Test
    void listHandlersShouldNormalizeNullServiceCollections() {
        MetadataService metadataService = mock(MetadataService.class);
        AlertService alertService = mock(AlertService.class);
        when(metadataService.listTopics("instance-a", null, null)).thenReturn(null);
        when(metadataService.listConsumerGroups("instance-a", null)).thenReturn(null);
        when(alertService.listRules()).thenReturn(null);

        Object topics = new TopicListToolHandler(metadataService).execute(Map.of("cluster", "instance-a"));
        Object groups = new ConsumerGroupListToolHandler(metadataService).execute(Map.of("cluster", "instance-a"));
        Object rules = new AlertRuleListToolHandler(alertService).execute(Map.of());

        assertThat((List<?>) topics).isEmpty();
        assertThat((List<?>) groups).isEmpty();
        assertThat((List<?>) rules).isEmpty();
    }

    @Test
    void alertHandlerShouldIgnoreNullRowsAndNestedListEntries() {
        AlertService alertService = mock(AlertService.class);
        AlertRuleVO rule = AlertRuleVO.builder()
                .name("broker unavailable")
                .metric("broker_up")
                .channels(Arrays.asList(null, "email"))
                .build();
        when(alertService.listRules()).thenReturn(Arrays.asList(null, rule));

        List<?> rows = (List<?>) new AlertRuleListToolHandler(alertService).execute(Map.of());

        assertThat(rows).singleElement().satisfies(row ->
                assertThat((List<?>) ((Map<?, ?>) row).get("channels")).isEqualTo(List.of("email")));
    }

    @Test
    void dashboardHandlerShouldIgnoreNullClusterRows() {
        DashboardService dashboardService = mock(DashboardService.class);
        when(dashboardService.getDashboard()).thenReturn(DashboardDataVO.builder()
                .clusters(Arrays.asList((ClusterOverviewVO) null))
                .build());

        assertThatThrownBy(() -> new DashboardSummaryToolHandler(dashboardService)
                .execute(Map.of("cluster", "instance-a")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Dashboard cluster not found: instance-a");
    }
}
