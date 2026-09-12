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
import org.apache.rocketmq.studio.instance.group.SubscriptionEntryVO;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupDescribeInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupDescribeOutput;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class GroupDescribeToolHandler implements ToolHandler<GroupDescribeInput, GroupDescribeOutput> {

    private final MetadataService metadataService;

    @Override
    public String name() {
        return "rmq.group.describe";
    }

    @Override
    public Class<GroupDescribeInput> inputType() {
        return GroupDescribeInput.class;
    }

    @Override
    public GroupDescribeOutput execute(
            GroupDescribeInput input,
            ToolExecutionContext context) {
        String groupName = input.group();
        String cluster = context.cluster();
        ConsumerGroupVO group = metadataService.consumerGroupRuntimeView(
                context.cluster(), groupName);
        List<SubscriptionEntryVO> subscriptions =
                metadataService.getGroupSubscriptions(context.cluster(), groupName);
        return GroupDescribeOutput.from(
                group, cluster, groupName, subscriptions, health(group),
                metadataService.consumerGroupConfigurations(context.cluster(), groupName));
    }

    private static GroupDescribeOutput.Health health(ConsumerGroupVO group) {
        List<String> reasons = new ArrayList<>();
        String status;
        if (!group.isConsumeStatsAvailable()) {
            status = "UNKNOWN";
            reasons.add("Broker consume statistics are unavailable.");
        } else if (group.getOnlineInstances() <= 0 && group.getTotalLag() > 0) {
            status = "UNHEALTHY";
            reasons.add("The group has accumulated messages but no online consumer.");
        } else if (group.getOnlineInstances() <= 0) {
            status = "WARNING";
            reasons.add("The group has no online consumer.");
        } else if (group.getTotalLag() > 0) {
            status = "WARNING";
            reasons.add("The group has accumulated messages.");
        } else {
            status = "HEALTHY";
        }
        return new GroupDescribeOutput.Health(status, List.copyOf(reasons));
    }
}
