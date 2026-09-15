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
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.broker.BrokerListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Platform-level Broker replica-group list (decision 26). With {@code clusterName} the resolver
 * pins the owning instance and only that physical cluster's brokers are returned; otherwise every
 * Apache instance is aggregated and deduplicated. Runtime statistics (tps/disk) come from
 * {@link ClusterProvider#discoverBrokers}. No instanceId.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BrokerListToolHandler
        implements ToolHandler<BrokerListInput, ListOutput<BrokerVO>> {

    private final PlatformClusterResolver clusterResolver;
    private final ClusterProvider clusterProvider;

    @Override
    public String name() {
        return "rmq.broker.list";
    }

    @Override
    public Class<BrokerListInput> inputType() {
        return BrokerListInput.class;
    }

    @Override
    public ListOutput<BrokerVO> execute(BrokerListInput input, ToolExecutionContext context) {
        Map<String, BrokerVO> unique = new LinkedHashMap<>();
        if (StringUtils.hasText(input.clusterName())) {
            PlatformClusterResolver.ManagedCluster cluster = clusterResolver.require(input.clusterName());
            Set<String> names = cluster.brokerNames();
            clusterProvider.discoverBrokers(cluster.instanceId(), null).stream()
                    .filter(broker -> names.contains(broker.getName()))
                    .forEach(broker -> unique.putIfAbsent(key(broker), broker));
        } else {
            for (InstanceVO instance : clusterResolver.manageableInstances()) {
                try {
                    clusterProvider.discoverBrokers(instance.getName(), null)
                            .forEach(broker -> unique.putIfAbsent(key(broker), broker));
                } catch (Exception e) {
                    log.warn("Skipping instance {} during broker aggregation: {}",
                            instance.getName(), e.getMessage());
                }
            }
        }
        return new ListOutput<>(List.copyOf(unique.values()));
    }

    private static String key(BrokerVO broker) {
        return broker.getName() + "@" + broker.getAddr();
    }
}
