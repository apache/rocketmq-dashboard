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

import org.apache.rocketmq.studio.ops.ai.tool.contract.alert.AlertRuleListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.alert.AlertRuleListItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.alert.AlertRuleVO;
import org.apache.rocketmq.studio.ops.alert.AlertService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertRuleListToolHandlerTest {

    @Mock
    private AlertService alertService;

    @InjectMocks
    private AlertRuleListToolHandler handler;

    @Test
    void executeShouldListRulesOfEveryInstanceTest() {
        assertThat(handler.name()).isEqualTo("rmq.alert.rule.list");
        when(alertService.listRules()).thenReturn(List.of(
                rule(1L, "global-lag", "consumer.lag.total", true, null),
                rule(2L, "instance-a-tps", "broker.tps.in", true, "instance-a"),
                rule(3L, "instance-b-tps", "broker.tps.in", false, "instance-b")));

        ListOutput<AlertRuleListItem> all = handler.execute(
                new AlertRuleListInput(null, null), context("instance-a"));
        assertThat(all.items()).extracting(AlertRuleListItem::name)
                .containsExactly("global-lag", "instance-a-tps", "instance-b-tps");

        ListOutput<AlertRuleListItem> enabledOnly = handler.execute(
                new AlertRuleListInput(null, true), context("instance-a"));
        assertThat(enabledOnly.items()).extracting(AlertRuleListItem::id).containsExactly(1L, 2L);

        ListOutput<AlertRuleListItem> filtered = handler.execute(
                new AlertRuleListInput("instance-b", null), context("instance-a"));
        assertThat(filtered.items()).extracting(AlertRuleListItem::name)
                .containsExactly("instance-b-tps");
    }

    private static AlertRuleVO rule(
            Long id, String name, String metric, boolean enabled, String instanceId) {
        return AlertRuleVO.builder()
                .id(id)
                .name(name)
                .metric(metric)
                .operator(">")
                .threshold(100.0)
                .channels(List.of("webhook"))
                .enabled(enabled)
                .instanceId(instanceId)
                .build();
    }
}
