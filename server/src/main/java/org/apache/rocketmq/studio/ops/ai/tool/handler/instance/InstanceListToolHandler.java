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
package org.apache.rocketmq.studio.ops.ai.tool.handler.instance;

import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceService;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.instance.InstanceListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.instance.InstanceListItem;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;

/** Platform-level managed-instance discovery for MCP planners and operators. */
@Component
@RequiredArgsConstructor
public class InstanceListToolHandler
        implements ToolHandler<InstanceListInput, ListOutput<InstanceListItem>> {

    private final InstanceService instanceService;

    @Override
    public String name() {
        return "rmq.instance.list";
    }

    @Override
    public Class<InstanceListInput> inputType() {
        return InstanceListInput.class;
    }

    @Override
    public ListOutput<InstanceListItem> execute(
            InstanceListInput input, ToolExecutionContext context) {
        InstanceType type = parseType(input.type());
        return new ListOutput<>(instanceService.listInstances(type, input.search()).stream()
                .map(InstanceListToolHandler::toItem)
                .toList());
    }

    private static InstanceType parseType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return InstanceType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(400,
                    "type must be one of: " + Arrays.toString(InstanceType.values()));
        }
    }

    private static InstanceListItem toItem(InstanceVO instance) {
        InstanceVendor vendor = instance.getVendor() == null ? InstanceVendor.APACHE : instance.getVendor();
        return new InstanceListItem(
                instance.getName(),
                vendor.name(),
                instance.getType() == null ? null : instance.getType().name(),
                instance.getRegionId(),
                instance.getRegionName(),
                instance.getTopicCount(),
                instance.getConsumerGroupCount(),
                instance.isResourceCountsAvailable());
    }
}
