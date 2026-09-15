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
package org.apache.rocketmq.studio.ops.ai.tool.handler.client;

import org.apache.rocketmq.studio.cluster.client.ClientConnectionVO;
import org.apache.rocketmq.studio.cluster.client.ClientService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.client.ClientListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ClusterListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ClientListToolHandler
        implements ToolHandler<ClientListInput, ClusterListOutput<ClientConnectionVO>> {

    private final ClientService clientService;

    @Override
    public String name() {
        return "rmq.client.list";
    }

    @Override
    public Class<ClientListInput> inputType() {
        return ClientListInput.class;
    }

    @Override
    public ClusterListOutput<ClientConnectionVO> execute(
            ClientListInput input, ToolExecutionContext context) {
        String instanceId = context.cluster();
        String clusterFilter = null;
        return list(input, instanceId, clusterFilter, context.cluster());
    }

    private ClusterListOutput<ClientConnectionVO> list(
            ClientListInput input, String instanceId, String clusterFilter, String cluster) {
        List<ClientConnectionVO> connections = clientService.listConnections(
                instanceId, clusterFilter, input.type());
        return new ClusterListOutput<>(cluster, connections);
    }
}
