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
package org.apache.rocketmq.studio.ops.ai.tool.handler.cluster;

import org.apache.rocketmq.studio.ops.ai.tool.contract.cluster.ClusterListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.cluster.ClusterListItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver.ManagedBroker;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver.ManagedCluster;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Platform-level physical cluster/broker overview (decision 25): aggregates every Apache
 * instance through the resolver, deduplicates by physical cluster name, and flattens to one
 * row per broker replica. No instanceId.
 */
@Component
@RequiredArgsConstructor
public class ClusterListToolHandler
        implements ToolHandler<ClusterListInput, ListOutput<ClusterListItem>> {

    private final PlatformClusterResolver clusterResolver;

    @Override
    public String name() {
        return "rmq.cluster.list";
    }

    @Override
    public Class<ClusterListInput> inputType() {
        return ClusterListInput.class;
    }

    @Override
    public ListOutput<ClusterListItem> execute(
            ClusterListInput input, ToolExecutionContext context) {
        return new ListOutput<>(clusterResolver.scanWithBrokerVersions().stream()
                .filter(cluster -> matchesStatus(cluster, input.status()))
                .flatMap(cluster -> cluster.brokers().stream()
                        .map(broker -> toItem(cluster, broker)))
                .toList());
    }

    private static ClusterListItem toItem(ManagedCluster cluster, ManagedBroker broker) {
        return new ClusterListItem(
                cluster.clusterName(),
                broker.address(),
                broker.brokerName(),
                broker.brokerId(),
                broker.version());
    }

    private static boolean matchesStatus(ManagedCluster cluster, String status) {
        return status == null
                || cluster.status() != null
                && cluster.status().name().equalsIgnoreCase(status.trim());
    }
}
