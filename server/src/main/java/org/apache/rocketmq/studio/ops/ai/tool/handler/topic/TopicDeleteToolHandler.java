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
package org.apache.rocketmq.studio.ops.ai.tool.handler.topic;

import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.handler.MutationToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.PlanDescription;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TopicDeleteToolHandler extends MutationToolHandler<TopicInput, Void> {

    private static final PlanDescription PLAN_DESCRIPTION = new PlanDescription(
            "delete topic '%s' in cluster '%s'.",
            List.of("Deletes the topic route and makes retained messages unavailable."),
            List.of("Deleting the topic makes its retained messages unavailable to producers and consumers."));

    private final MetadataService metadataService;

    public TopicDeleteToolHandler(MetadataService metadataService) {
        super(TopicInput.class);
        this.metadataService = metadataService;
    }

    @Override
    public String name() {
        return "rmq.topic.delete";
    }

    @Override
    public ToolPlan preview(TopicInput input, ToolExecutionContext context) {
        TopicVO current = metadataService.getTopic(
                context.cluster(), null, input.topic());
        TopicInput before = TopicInput.from(current);
        return PLAN_DESCRIPTION.builder(input.topic(), context.cluster())
                .before(before)
                .build();
    }

    @Override
    public Void execute(TopicInput input, ToolExecutionContext context) {
        metadataService.deleteTopic(context.cluster(), input.topic());
        return null;
    }

}
