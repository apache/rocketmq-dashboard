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

import org.apache.rocketmq.studio.ops.ai.tool.contract.cluster.ClusterListItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.cluster.ClusterListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;

import org.apache.rocketmq.studio.cluster.broker.ClusterService;
import org.apache.rocketmq.studio.cluster.broker.ClusterVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClusterListToolHandler
        implements ToolHandler<ClusterListInput, ListOutput<ClusterListItem>> {

    private final ClusterService clusterService;

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
        return new ListOutput<>(clusterService.listClusters(context.cluster()).stream()
                .filter(cluster -> matchesStatus(cluster, input.status()))
                .map(ClusterListItem::from)
                .toList());
    }

    private static boolean matchesStatus(ClusterVO cluster, String status) {
        return status == null
                || cluster.getStatus() != null
                && cluster.getStatus().name().equalsIgnoreCase(status.trim());
    }
}
