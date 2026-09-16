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
package org.apache.rocketmq.studio.ops.ai.tool.handler.nameserver;

import org.apache.rocketmq.studio.cluster.nameserver.NameServerConfigDiffService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.nameserver.NameserverConfigInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.nameserver.NameserverConfigItem;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Read-only NameServer endpoint configuration (decision 15), addressed by physical cluster name
 * (decision 26). Replaces the former {@code rmq.nameserver.config.diff}: no diff, no mutation.
 * The resolver maps clusterName to its owning instance so the cluster-details lookup resolves the
 * live topology (fixes the §15.5.5 root cause where the instance id was used as the cluster key).
 */
@Component
@RequiredArgsConstructor
public class NameServerConfigToolHandler
        implements ToolHandler<NameserverConfigInput, ListOutput<NameserverConfigItem>> {

    private final PlatformClusterResolver clusterResolver;
    private final NameServerConfigDiffService configDiffService;

    @Override
    public String name() {
        return "rmq.nameserver.config";
    }

    @Override
    public Class<NameserverConfigInput> inputType() {
        return NameserverConfigInput.class;
    }

    @Override
    public ListOutput<NameserverConfigItem> execute(
            NameserverConfigInput input, ToolExecutionContext context) {
        String instanceId = clusterResolver.resolveInstanceId(input.clusterName());
        return new ListOutput<>(configDiffService.read(input.clusterName(), instanceId).stream()
                .map(node -> new NameserverConfigItem(node.addr(), node.config()))
                .toList());
    }
}
