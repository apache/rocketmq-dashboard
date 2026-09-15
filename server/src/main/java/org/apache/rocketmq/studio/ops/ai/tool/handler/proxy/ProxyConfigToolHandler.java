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

import org.apache.rocketmq.studio.cluster.proxy.ProxyAddressService;
import org.apache.rocketmq.studio.cluster.proxy.ProxyTopologyVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.proxy.ProxyConfigInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.proxy.ProxyConfigItem;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Stream;

/**
 * Read-only Proxy configuration/reachability snapshot (decision 15), addressed by physical cluster
 * name (decision 26). Replaces the former {@code rmq.proxy.config_update} L3 reload action: proxy
 * admin exposes no configuration read endpoint yet, so the topology + reachability probe snapshot
 * from {@link ProxyAddressService#buildTopology()} is surfaced as the "config" view. {@code
 * clusterName} gates the call (404 when unowned); {@code addr} optionally filters the snapshot.
 * The reload write path stays on REST only. No instanceId argument, no mutation.
 */
@Component
@RequiredArgsConstructor
public class ProxyConfigToolHandler
        implements ToolHandler<ProxyConfigInput, ListOutput<ProxyConfigItem>> {

    private final PlatformClusterResolver clusterResolver;
    private final ProxyAddressService proxyAddressService;

    @Override
    public String name() {
        return "rmq.proxy.config";
    }

    @Override
    public Class<ProxyConfigInput> inputType() {
        return ProxyConfigInput.class;
    }

    @Override
    public ListOutput<ProxyConfigItem> execute(ProxyConfigInput input, ToolExecutionContext context) {
        clusterResolver.require(input.clusterName());
        Stream<ProxyTopologyVO> topology = proxyAddressService.buildTopology().stream();
        if (StringUtils.hasText(input.addr())) {
            String addr = input.addr().trim();
            topology = topology.filter(node -> addr.equals(node.getProxyAddr()));
        }
        List<ProxyConfigItem> items = topology.map(ProxyConfigItem::from).toList();
        return new ListOutput<>(items);
    }
}
