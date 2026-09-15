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

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupClientsOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupTopicInput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GroupClientsToolHandler implements ToolHandler<GroupTopicInput, GroupClientsOutput> {

    private final MetadataService metadataService;

    @Override
    public String name() {
        return "rmq.group.clients";
    }

    @Override
    public Class<GroupTopicInput> inputType() {
        return GroupTopicInput.class;
    }

    @Override
    public GroupClientsOutput execute(
            GroupTopicInput input,
            ToolExecutionContext context) {
        ConsumerGroupVO group = metadataService.consumerGroupRuntimeView(
                context.cluster(), input.group());
        return GroupClientsOutput.from(
                group, context.cluster(), input.group(), input.topic());
    }
}
