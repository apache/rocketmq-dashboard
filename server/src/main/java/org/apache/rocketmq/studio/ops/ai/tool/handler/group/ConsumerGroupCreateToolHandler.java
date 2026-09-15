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
package org.apache.rocketmq.studio.ops.ai.tool.handler.group;

import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.handler.MutationToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupListItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.PlanDescription;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConsumerGroupCreateToolHandler extends MutationToolHandler<GroupInput, GroupListItem> {

    private static final PlanDescription PLAN_DESCRIPTION = new PlanDescription(
            "create consumer group '%s' in cluster '%s'.",
            List.of("Creates the consumer group's broker-side subscription configuration."),
            List.of());

    private final MetadataService metadataService;

    public ConsumerGroupCreateToolHandler(MetadataService metadataService) {
        super(GroupInput.class);
        this.metadataService = metadataService;
    }

    @Override
    public String name() {
        return "rmq.group.create";
    }

    @Override
    public ToolPlan preview(GroupInput input, ToolExecutionContext context) {
        String instanceId = context.cluster();
        ConsumerGroupVO current = metadataService.findConsumerGroup(instanceId, input.group()).orElse(null);
        GroupInput before = current == null ? null : GroupInput.from(current);
        return PLAN_DESCRIPTION.builder(input.group(), context.cluster())
                .before(before)
                .after(GroupInput.from(input.toConsumerGroupVO()))
                .build();
    }

    @Override
    public GroupListItem execute(GroupInput input, ToolExecutionContext context) {
        ConsumerGroupVO group = input.toConsumerGroupVO();
        group.setInstanceId(context.cluster());
        return GroupListItem.from(metadataService.createConsumerGroup(group));
    }
}
