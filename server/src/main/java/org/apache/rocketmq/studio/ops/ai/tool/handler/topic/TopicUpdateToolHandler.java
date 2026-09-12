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
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicUpdateInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.PlanDescription;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TopicUpdateToolHandler extends MutationToolHandler<TopicUpdateInput, TopicOutput> {

    private static final PlanDescription PLAN_DESCRIPTION = new PlanDescription(
            "update topic '%s' in cluster '%s'.",
            List.of("Updates topic routing and queue configuration on reachable master brokers."),
            List.of());

    private final MetadataService metadataService;

    public TopicUpdateToolHandler(MetadataService metadataService) {
        super(TopicUpdateInput.class);
        this.metadataService = metadataService;
    }

    @Override
    public String name() {
        return "rmq.topic.update";
    }

    @Override
    public ToolPlan preview(TopicUpdateInput input, ToolExecutionContext context) {
        TopicVO current = metadataService.getTopic(
                context.cluster(), null, input.topic());
        TopicInput before = TopicInput.from(current);
        TopicInput after = TopicInput.from(input.mergeWith(current));
        return PLAN_DESCRIPTION.builder(input.topic(), context.cluster())
                .before(before)
                .after(after)
                .warningIf(before.equals(after),
                        "The requested topic configuration already matches the current state.")
                .build();
    }

    @Override
    public TopicOutput execute(TopicUpdateInput input, ToolExecutionContext context) {
        TopicVO current = metadataService.getTopic(
                context.cluster(), null, input.topic());
        TopicVO topic = input.mergeWith(current);
        return TopicOutput.from(metadataService.updateTopic(context.cluster(), topic));
    }

}
