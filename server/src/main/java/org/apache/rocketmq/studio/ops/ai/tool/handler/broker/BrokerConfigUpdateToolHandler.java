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
package org.apache.rocketmq.studio.ops.ai.tool.handler.broker;

import org.apache.rocketmq.studio.cluster.broker.ClusterService;
import org.apache.rocketmq.studio.cluster.config.ClusterConfigUpdateResultVO;
import org.apache.rocketmq.studio.cluster.config.UpdateConfigDTO;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.handler.MutationToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.contract.broker.BrokerConfigUpdateInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.broker.BrokerConfigUpdateOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.PlanDescription;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BrokerConfigUpdateToolHandler extends MutationToolHandler<BrokerConfigUpdateInput, BrokerConfigUpdateOutput> {

    private static final PlanDescription PLAN_DESCRIPTION = new PlanDescription(
            "update Broker configuration in Instance '%s'.",
            List.of(),
            List.of());

    private final ClusterService clusterService;

    public BrokerConfigUpdateToolHandler(ClusterService clusterService) {
        super(BrokerConfigUpdateInput.class);
        this.clusterService = clusterService;
    }

    @Override
    public String name() {
        return "rmq.broker.config_update";
    }

    @Override
    public ToolPlan preview(BrokerConfigUpdateInput input, ToolExecutionContext context) {
        var current = clusterService.readBrokerConfigs(context.cluster());
        var proposed = clusterService.proposeBrokerConfigs(toCommand(input, context.cluster()), current);
        return PLAN_DESCRIPTION.builder(context.cluster())
                .before(java.util.Map.of("brokers", current))
                .after(java.util.Map.of("brokers", proposed))
                .impact("Updates the requested configuration fields on " + current.size() + " master Broker(s).")
                .build();
    }

    @Override
    public BrokerConfigUpdateOutput execute(
            BrokerConfigUpdateInput input, ToolExecutionContext context) {
        ClusterConfigUpdateResultVO result = clusterService.updateClusterConfig(
                toCommand(input, context.cluster()), context.cluster());
        return BrokerConfigUpdateOutput.from(result);
    }

    private static UpdateConfigDTO toCommand(BrokerConfigUpdateInput input, String instanceId) {
        return UpdateConfigDTO.builder()
                .instanceId(instanceId)
                .flushDiskType(input.flushDiskType())
                .autoCreateTopicEnable(input.autoCreateTopicEnable())
                .autoCreateSubscriptionGroup(input.autoCreateSubscriptionGroup())
                .maxMessageSize(input.maxMessageSize())
                .fileReservedTime(input.fileReservedTime())
                .writeQueueNums(input.writeQueueNums())
                .readQueueNums(input.readQueueNums())
                .brokerPermission(input.brokerPermission())
                .build();
    }
}
