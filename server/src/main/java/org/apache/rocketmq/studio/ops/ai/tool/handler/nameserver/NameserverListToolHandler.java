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

import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.cluster.nameserver.NameServerVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ClusterListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ClusterInput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class NameserverListToolHandler
        implements ToolHandler<ClusterInput, ClusterListOutput<NameserverListToolHandler.Item>> {

    private final RuntimeAdminClientResolver runtimeAdminClientResolver;

    @Override
    public String name() {
        return "rmq.nameserver.list";
    }

    @Override
    public Class<ClusterInput> inputType() {
        return ClusterInput.class;
    }

    @Override
    public ClusterListOutput<Item> execute(
            ClusterInput input, ToolExecutionContext context) {
        String endpoint = runtimeAdminClientResolver.resolveEndpoint(context.cluster());
        List<Item> nodes = java.util.Arrays.stream(endpoint.split("[;,]"))
                .map(String::trim).filter(address -> !address.isEmpty()).distinct().sorted()
                .map(address -> new Item(address, address, address, null, null, "UNKNOWN", null)).toList();
        return new ClusterListOutput<>(context.cluster(), nodes);
    }

    private static Item project(NameServerVO nameserver) {
        String address = nameserver.getAddr();
        String status = nameserver.getStatus() == null ? "UNKNOWN" : nameserver.getStatus().name();
        return new Item(address, address, address, null, null, status, null);
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
