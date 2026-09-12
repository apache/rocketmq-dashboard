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

import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.handler.MutationToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.PlanDescription;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConsumerGroupDeleteToolHandler extends MutationToolHandler<GroupInput, Void> {

    private static final PlanDescription PLAN_DESCRIPTION = new PlanDescription(
            "delete consumer group '%s' in cluster '%s'.",
            List.of("Removes the consumer group's broker-side subscription configuration."),
            List.of("Deleting the group removes its broker-side subscription configuration."));

    private final MetadataService metadataService;

    public ConsumerGroupDeleteToolHandler(MetadataService metadataService) {
        super(GroupInput.class);
        this.metadataService = metadataService;
    }

    @Override
    public String name() {
        return "rmq.group.delete";
    }

    @Override
    public ToolPlan preview(GroupInput input, ToolExecutionContext context) {
        ConsumerGroupVO current = metadataService.requireConsumerGroup(
                context.cluster(), input.group());
        GroupInput before = GroupInput.from(current);
        return PLAN_DESCRIPTION.builder(input.group(), context.cluster())
                .before(before)
                .build();
    }

    @Override
    public Void execute(GroupInput input, ToolExecutionContext context) {
        metadataService.deleteConsumerGroup(context.cluster(), input.group());
        return null;
    }
}
