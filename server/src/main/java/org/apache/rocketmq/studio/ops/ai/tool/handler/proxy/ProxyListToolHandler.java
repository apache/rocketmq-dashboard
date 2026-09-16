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
package org.apache.rocketmq.studio.ops.ai.tool.handler.proxy;

import org.apache.rocketmq.studio.cluster.broker.ClusterProvider;
import org.apache.rocketmq.studio.cluster.proxy.ProxyVO;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.proxy.ProxyListInput;
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

/**
 * Platform-level Proxy data-endpoint discovery (decision 26). With {@code clusterName} the
 * resolver pins the owning instance; otherwise every Apache instance is aggregated and
 * deduplicated by address. Discovery is via the heartbeat-syncer group and does not establish
 * cluster membership or management capability. No instanceId.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProxyListToolHandler
        implements ToolHandler<ProxyListInput, ListOutput<ProxyVO>> {

    private final PlatformClusterResolver clusterResolver;
    private final ClusterProvider clusterProvider;

    @Override
    public String name() {
        return "rmq.proxy.list";
    }

    @Override
    public Class<ProxyListInput> inputType() {
        return ProxyListInput.class;
    }

    @Override
    public ListOutput<ProxyVO> execute(ProxyListInput input, ToolExecutionContext context) {
        Map<String, ProxyVO> unique = new LinkedHashMap<>();
        if (StringUtils.hasText(input.clusterName())) {
            PlatformClusterResolver.ManagedCluster cluster = clusterResolver.require(input.clusterName());
            clusterProvider.discoverProxies(cluster.instanceId())
                    .forEach(proxy -> unique.putIfAbsent(proxy.getAddr(), proxy));
        } else {
            for (InstanceVO instance : clusterResolver.manageableInstances()) {
                try {
                    clusterProvider.discoverProxies(instance.getName())
                            .forEach(proxy -> unique.putIfAbsent(proxy.getAddr(), proxy));
                } catch (Exception e) {
                    log.warn("Skipping instance {} during proxy aggregation: {}",
                            instance.getName(), e.getMessage());
                }
            }
        }
        return new ListOutput<>(List.copyOf(unique.values()));
    }
}
