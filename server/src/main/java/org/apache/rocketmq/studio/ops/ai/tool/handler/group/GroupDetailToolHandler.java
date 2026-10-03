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
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.apache.rocketmq.studio.instance.group.SubscriptionEntryVO;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupDetailInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupDetailOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated consumer group detail (decision 11): pure composition of existing
 * MetadataService reads — runtime view, subscriptions, configurations, queue progress
 * and online clients. The optional topicName filters the progress and clients blocks;
 * without online consumers both blocks stay empty instead of failing the call.
 */
@Component
@RequiredArgsConstructor
public class GroupDetailToolHandler implements ToolHandler<GroupDetailInput, GroupDetailOutput> {

    private final MetadataService metadataService;

    @Override
    public String name() {
        return "rmq.group.detail";
    }

    @Override
    public Class<GroupDetailInput> inputType() {
        return GroupDetailInput.class;
    }

    @Override
    public GroupDetailOutput execute(
            GroupDetailInput input,
            ToolExecutionContext context) {
        String instanceId = context.instanceId();
        String groupName = input.groupName();
        ConsumerGroupVO group = metadataService.consumerGroupRuntimeView(instanceId, groupName);
        List<SubscriptionEntryVO> subscriptions =
                metadataService.getGroupSubscriptions(instanceId, groupName);
        List<ConsumerGroupVO> configurations =
                metadataService.consumerGroupConfigurations(instanceId, groupName);
        List<QueueProgressVO> progress =
                metadataService.getGroupProgress(instanceId, groupName);
        return GroupDetailOutput.from(
                group, instanceId, groupName, subscriptions, health(group),
                configurations, progress, input.topicName());
    }

    private static GroupDetailOutput.Health health(ConsumerGroupVO group) {
        List<String> reasons = new ArrayList<>();
        String status;
        if (!group.isConsumeStatsAvailable()) {
            status = "UNKNOWN";
            reasons.add("Broker consume statistics are unavailable.");
        } else if (group.getOnlineInstances() < 0) {
            status = "UNKNOWN";
            reasons.add("Consumer connection information is unavailable.");
        } else if (group.getOnlineInstances() == 0 && group.getTotalLag() > 0) {
            status = "UNHEALTHY";
            reasons.add("The group has accumulated messages but no online consumer.");
        } else if (group.getOnlineInstances() == 0) {
            status = "WARNING";
            reasons.add("The group has no online consumer.");
        } else if (group.getTotalLag() < 0) {
            // The -1 sentinel means the lag could not be resolved (a queue whose diff is unknown),
            // so no verdict may be derived from it, not even the "nothing accumulated" one.
            status = "UNKNOWN";
            reasons.add("Consumer lag information is unavailable.");
        } else if (group.getTotalLag() > 0) {
            status = "WARNING";
            reasons.add("The group has accumulated messages.");
        } else {
            status = "HEALTHY";
        }
        return new GroupDetailOutput.Health(status, List.copyOf(reasons));
    }
}
