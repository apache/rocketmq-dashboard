/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
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
package org.apache.rocketmq.studio.ops.ai.tool.handler.dlq;

import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.instance.dlq.DLQGroupVO;
import org.apache.rocketmq.studio.instance.dlq.DLQService;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolError;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.handler.MutationToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.contract.dlq.DLQClearInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.PlanDescription;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DLQClearToolHandler extends MutationToolHandler<DLQClearInput, Void> {

    private static final PlanDescription PLAN_DESCRIPTION = new PlanDescription(
            "clear DLQ for consumer group '%s' in cluster '%s'.",
            List.of("Deletes the physical dead-letter topic and its retained messages."),
            List.of("All retained dead-letter messages for this group will be deleted."));

    private final MetadataService metadataService;
    private final DLQService dlqService;

    public DLQClearToolHandler(MetadataService metadataService, DLQService dlqService) {
        super(DLQClearInput.class);
        this.metadataService = metadataService;
        this.dlqService = dlqService;
    }

    @Override
    public String name() {
        return "rmq.dlq.clear";
    }

    @Override
    public ToolPlan preview(DLQClearInput input, ToolExecutionContext context) {
        String instanceId = context.cluster();
        String groupName = input.group();
        DLQGroupVO group = dlqService.listDLQGroups(instanceId, groupName, 1, 100)
                .getItems().stream()
                .filter(item -> groupName.equals(item.getGroupName()))
                .findFirst()
                .orElseThrow(() -> ToolError.DLQ_GROUP_NOT_FOUND.exception(groupName));
        if (!group.isStatsAvailable()) {
            throw ToolError.DLQ_STATISTICS_UNAVAILABLE.exception(groupName);
        }
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("group", group.getGroupName());
        before.put("topic", group.getDlqTopic());
        before.put("messageCount", group.getMessageCount());
        before.put("statsAvailable", group.isStatsAvailable());
        before.put("status", group.getStatus());
        return PLAN_DESCRIPTION.builder(groupName, context.cluster())
                .before(before)
                .build();
    }

    @Override
    public Void execute(DLQClearInput input, ToolExecutionContext context) {
        metadataService.deleteTopic(context.cluster(), "%DLQ%" + input.group());
        return null;
    }
}
