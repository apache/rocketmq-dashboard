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

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.ops.ai.tool.contract.alert.AlertRuleListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.alert.AlertRuleListItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.apache.rocketmq.studio.ops.alert.AlertRuleVO;
import org.apache.rocketmq.studio.ops.alert.AlertService;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class AlertRuleListToolHandler
        implements ToolHandler<AlertRuleListInput, ListOutput<AlertRuleListItem>> {

    private final AlertService alertService;

    @Override
    public String name() {
        return "rmq.alert.rule.list";
    }

    @Override
    public Class<AlertRuleListInput> inputType() {
        return AlertRuleListInput.class;
    }

    @Override
    public ListOutput<AlertRuleListItem> execute(
            AlertRuleListInput input, ToolExecutionContext context) {
        return new ListOutput<>(alertService.findRules(context.cluster()).stream()
                .filter(rule -> matchesEnabled(rule, input.enabled()))
                .filter(rule -> matchesSearch(rule, input.search()))
                .map(AlertRuleListItem::from)
                .toList());
    }

    private static boolean matchesEnabled(AlertRuleVO rule, Boolean enabled) {
        return enabled == null || rule.isEnabled() == enabled;
    }

    private static boolean matchesSearch(AlertRuleVO rule, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String normalizedSearch = search.trim().toLowerCase(Locale.ROOT);
        return contains(rule.getName(), normalizedSearch)
                || contains(rule.getMetric(), normalizedSearch)
                || contains(rule.getDescription(), normalizedSearch);
    }

    private static boolean contains(String value, String normalizedSearch) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedSearch);
    }

}
