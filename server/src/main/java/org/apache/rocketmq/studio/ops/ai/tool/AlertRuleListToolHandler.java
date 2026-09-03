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

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.ops.alert.AlertRuleVO;
import org.apache.rocketmq.studio.ops.alert.AlertService;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AlertRuleListToolHandler implements ToolHandler {

    private static final String NAME = "rmq.alert.rule.list";

    private final AlertService alertService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Object execute(Map<String, Object> input) {
        String search = (String) input.get("search");
        Boolean enabled = (Boolean) input.get("enabled");
        PageResult<AlertRuleVO> page = alertService.listRules(
                search, enabled, ToolListPagination.page(input), ToolListPagination.pageSize(input));
        return ToolListPagination.pagedResult(page, page.getItems().stream()
                .map(AlertRuleListToolHandler::safeProjection)
                .toList());
    }

    private static Map<String, Object> safeProjection(AlertRuleVO rule) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", rule.getId());
        result.put("name", require(rule.getName(), "name"));
        result.put("metric", require(rule.getMetric(), "metric"));
        result.put("operator", blankIfNull(rule.getOperator()));
        result.put("threshold", rule.getThreshold());
        result.put("thresholdUnit", blankIfNull(rule.getThresholdUnit()));
        result.put("duration", blankIfNull(rule.getDuration()));
        result.put("channels", copyList(rule.getChannels()));
        result.put("enabled", rule.isEnabled());
        result.put("description", blankIfNull(rule.getDescription()));
        return result;
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Alert rule " + field + " is unavailable");
        }
        return value;
    }

    private static List<String> copyList(List<String> value) {
        return value == null ? List.of() : List.copyOf(value);
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
