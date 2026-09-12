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
package org.apache.rocketmq.studio.ops.ai.tool.handler.group;

import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.handler.MutationToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupTopicInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.SkipAccumulatedOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.PlanDescription;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class GroupSkipAccumulatedToolHandler
        extends MutationToolHandler<GroupTopicInput, SkipAccumulatedOutput> {

    private static final PlanDescription PLAN_DESCRIPTION = new PlanDescription(
            "skip accumulated messages for consumer group '%s' in cluster '%s'.",
            List.of(),
            List.of("Pending messages for the resolved topics will no longer be consumed."));

    private final MetadataService metadataService;

    public GroupSkipAccumulatedToolHandler(MetadataService metadataService) {
        super(GroupTopicInput.class);
        this.metadataService = metadataService;
    }

    @Override
    public String name() {
        return "rmq.group.skip_accumulated";
    }

    @Override
    public ToolPlan preview(GroupTopicInput input, ToolExecutionContext context) {
        List<String> topics = metadataService.resolveSkipAccumulatedTopics(
                context.cluster(), input.group(), input.topic());
        Map<String, Object> before = Map.of(
                "group", input.group(),
                "topics", topics,
                "position", "CURRENT_OFFSETS");
        Map<String, Object> after = Map.of(
                "group", input.group(),
                "topics", topics,
                "position", "LATEST_OFFSETS");
        return PLAN_DESCRIPTION.builder(input.group(), context.cluster())
                .before(before)
                .after(after)
                .impact("Advances offsets to the latest position for "
                        + topics.size() + " topic(s).")
                .build();
    }

    @Override
    public SkipAccumulatedOutput execute(GroupTopicInput input, ToolExecutionContext context) {
        List<String> topics = metadataService.skipAccumulated(
                context.cluster(), input.group(), input.topic());
        return new SkipAccumulatedOutput(input.group(), topics);
    }
}
