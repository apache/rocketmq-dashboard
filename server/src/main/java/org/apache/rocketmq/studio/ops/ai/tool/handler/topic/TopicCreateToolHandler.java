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
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.PlanDescription;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TopicCreateToolHandler extends MutationToolHandler<TopicInput, TopicOutput> {

    private static final PlanDescription PLAN_DESCRIPTION = new PlanDescription(
            "create topic '%s' in cluster '%s'.",
            List.of("Creates the topic configuration on reachable master brokers."),
            List.of());

    private final MetadataService metadataService;

    public TopicCreateToolHandler(MetadataService metadataService) {
        super(TopicInput.class);
        this.metadataService = metadataService;
    }

    @Override
    public String name() {
        return "rmq.topic.create";
    }

    @Override
    public ToolPlan preview(TopicInput input, ToolExecutionContext context) {
        String instanceId = context.cluster();
        TopicInput before = metadataService.findTopic(
                        instanceId, null, input.topic())
                .map(TopicInput::from)
                .orElse(null);
        return PLAN_DESCRIPTION.builder(input.topic(), context.cluster())
                .before(before)
                .after(TopicInput.from(input.toTopicVO()))
                .build();
    }

    @Override
    public TopicOutput execute(TopicInput input, ToolExecutionContext context) {
        TopicVO topic = input.toTopicVO();
        return TopicOutput.from(metadataService.createTopic(context.cluster(), topic));
    }
}
