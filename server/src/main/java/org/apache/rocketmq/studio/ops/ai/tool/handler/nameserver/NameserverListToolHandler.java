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

import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.nameserver.NameserverListInput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Platform-level NameServer endpoint list (decision 26). With {@code clusterName} the resolver
 * pins the owning instance; otherwise every Apache instance's endpoints are aggregated and
 * deduplicated. No instanceId.
 */
@Component
@RequiredArgsConstructor
public class NameserverListToolHandler
        implements ToolHandler<NameserverListInput, ListOutput<NameserverListToolHandler.Item>> {

    private final PlatformClusterResolver clusterResolver;

    @Override
    public String name() {
        return "rmq.nameserver.list";
    }

    @Override
    public Class<NameserverListInput> inputType() {
        return NameserverListInput.class;
    }

    @Override
    public ListOutput<Item> execute(NameserverListInput input, ToolExecutionContext context) {
        Set<String> addresses = new TreeSet<>();
        if (StringUtils.hasText(input.clusterName())) {
            addresses.addAll(clusterResolver.require(input.clusterName()).nameServerAddrs());
        } else {
            for (InstanceVO instance : clusterResolver.manageableInstances()) {
                addresses.addAll(PlatformClusterResolver.splitEndpoints(instance.getEndpoint()));
            }
        }
        List<Item> nodes = addresses.stream()
                .map(address -> new Item(address, address, address, null, null, "UNKNOWN", null))
                .toList();
        return new ListOutput<>(nodes);
    }

    public record Item(
            String id,
            String name,
            String namesrvAddr,
            String k8sNamespace,
            String k8sId,
            String status,
            String description) {
    }
}
