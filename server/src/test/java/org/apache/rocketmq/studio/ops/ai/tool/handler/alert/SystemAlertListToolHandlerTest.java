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
package org.apache.rocketmq.studio.ops.ai.tool.handler.alert;

import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.domain.enums.AlertLevel;
import org.apache.rocketmq.studio.ops.ai.tool.contract.alert.SystemAlertItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.alert.SystemAlertListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.PageOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.PageRequest;
import org.apache.rocketmq.studio.ops.alert.AlertDomain;
import org.apache.rocketmq.studio.ops.alert.AlertService;
import org.apache.rocketmq.studio.ops.alert.SystemAlertVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemAlertListToolHandlerTest {

    @Mock
    private AlertService alertService;

    @InjectMocks
    private SystemAlertListToolHandler handler;

    @Test
    void executeShouldForwardFiltersAndMapSystemAlertsTest() {
        LocalDateTime from = LocalDateTime.of(2026, 8, 22, 8, 0);
        LocalDateTime to = LocalDateTime.of(2026, 8, 22, 9, 0);
        SystemAlertVO alert = SystemAlertVO.builder()
                .id(41L)
                .level(AlertLevel.warning)
                .title("Consumer lag")
                .description("lag exceeded threshold")
                .time(from)
                .acknowledged(true)
                .acknowledgedBy("operator")
                .acknowledgedAt(to)
                .domain(AlertDomain.CLUSTER)
                .transition("FIRING")
                .instanceId("instance-a")
                .currentValue(123.0)
                .notificationSuppressed(true)
                .suppressionReason("cluster incident already active")
                .labels(java.util.Map.of("cluster", "cluster-a"))
                .build();
        when(alertService.listAlerts("warning", AlertDomain.CLUSTER, "instance-a", "FIRING",
                "cluster", "cluster-a", from, to, 2, 10, true))
                .thenReturn(PageResult.of(List.of(alert), 11, 2, 10));

        PageOutput<SystemAlertItem> output = handler.execute(new SystemAlertListInput(
                "warning", "cluster", "instance-a", "FIRING", "cluster", "cluster-a",
                from.toString(), to.toString(), true, new PageRequest(2, 10)), context("instance-a"));

        assertThat(output.page()).isEqualTo(2);
        assertThat(output.pageSize()).isEqualTo(10);
        assertThat(output.total()).isEqualTo(11);
        assertThat(output.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(41L);
            assertThat(item.level()).isEqualTo("warning");
            assertThat(item.domain()).isEqualTo("CLUSTER");
            assertThat(item.time()).isEqualTo(from.toString());
            assertThat(item.labels()).containsEntry("cluster", "cluster-a");
        });
        verify(alertService).listAlerts("warning", AlertDomain.CLUSTER, "instance-a", "FIRING",
                "cluster", "cluster-a", from, to, 2, 10, true);
    }
}
