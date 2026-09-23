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
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.ops.ai.tool.contract.alert.SystemAlertItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.alert.SystemAlertListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.PageOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.apache.rocketmq.studio.ops.alert.AlertDomain;
import org.apache.rocketmq.studio.ops.alert.AlertService;
import org.apache.rocketmq.studio.ops.alert.SystemAlertVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class SystemAlertListToolHandler
        implements ToolHandler<SystemAlertListInput, PageOutput<SystemAlertItem>> {

    private final AlertService alertService;

    @Override
    public String name() {
        return "rmq.alert.system.list";
    }

    @Override
    public Class<SystemAlertListInput> inputType() {
        return SystemAlertListInput.class;
    }

    @Override
    public PageOutput<SystemAlertItem> execute(SystemAlertListInput input, ToolExecutionContext context) {
        PageResult<SystemAlertVO> result = alertService.listAlerts(
                input.level(), parseDomain(input.domain()), input.instanceId(), input.transition(),
                input.labelKey(), input.labelValue(), parseTime(input.from(), "from"),
                parseTime(input.to(), "to"), input.page().page(), input.page().pageSize(),
                input.notificationSuppressed());
        return PageOutput.from(result, SystemAlertItem::from);
    }

    private static AlertDomain parseDomain(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return AlertDomain.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new BusinessException(400, "domain must be BUSINESS or CLUSTER");
        }
    }

    private static LocalDateTime parseTime(String value, String field) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException error) {
            throw new BusinessException(400, field + " must be an ISO-8601 local date-time");
        }
    }
}
