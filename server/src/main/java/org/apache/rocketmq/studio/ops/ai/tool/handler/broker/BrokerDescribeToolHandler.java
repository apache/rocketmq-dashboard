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

import org.apache.rocketmq.studio.cluster.broker.BrokerVO;
import org.apache.rocketmq.studio.cluster.broker.ClusterProvider;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.ops.ai.tool.contract.broker.BrokerDescribeInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.broker.BrokerDescribeOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Single-broker full view addressed by the clusterName + brokerName pair (decision 20/26):
 * the resolver pins the physical cluster's owning instance, cluster membership is verified, and
 * the replica-group detail is merged with its runtime statistics (absorbing the former
 * rmq.broker.runtime_info). No instanceId.
 */
@Component
@RequiredArgsConstructor
public class BrokerDescribeToolHandler
        implements ToolHandler<BrokerDescribeInput, BrokerDescribeOutput> {

    private final PlatformClusterResolver clusterResolver;
    private final ClusterProvider clusterProvider;

    @Override
    public String name() {
        return "rmq.broker.describe";
    }

    @Override
    public Class<BrokerDescribeInput> inputType() {
        return BrokerDescribeInput.class;
    }

    @Override
    public BrokerDescribeOutput execute(BrokerDescribeInput input, ToolExecutionContext context) {
        PlatformClusterResolver.ManagedCluster cluster = clusterResolver.require(input.clusterName());
        String brokerName = input.brokerName();
        if (!cluster.brokerNames().contains(brokerName)) {
            throw new BusinessException(404,
                    "Broker not found in cluster " + cluster.clusterName() + ": " + brokerName);
        }
        BrokerVO broker = clusterProvider.discoverBrokers(cluster.instanceId(), brokerName).stream()
                .filter(candidate -> brokerName.equals(candidate.getName()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "Broker not found: " + brokerName));
        return BrokerDescribeOutput.from(broker);
    }
}
