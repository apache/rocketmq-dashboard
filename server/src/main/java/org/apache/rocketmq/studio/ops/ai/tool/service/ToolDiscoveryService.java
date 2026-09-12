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
package org.apache.rocketmq.studio.ops.ai.tool.service;

import org.apache.rocketmq.studio.instance.InstanceResolver;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.ops.ai.AiToolVO;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.CapabilityResolver;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolError;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class ToolDiscoveryService {

    private final ToolCatalog catalog;
    private final CapabilityResolver capabilityResolver;
    private final InstanceResolver instanceResolver;

    public ToolDiscoveryService(
            ToolCatalog catalog,
            CapabilityResolver capabilityResolver,
            InstanceResolver instanceResolver) {
        this.catalog = catalog;
        this.capabilityResolver = capabilityResolver;
        this.instanceResolver = instanceResolver;
    }

    public List<AiToolVO> listTools(String cluster) {
        if (cluster == null || cluster.isBlank()) {
            throw ToolError.TOOL_CLUSTER_REQUIRED.exception();
        }
        InstanceVO instance = instanceResolver.findByName(cluster)
                .orElseThrow(() -> ToolError.INSTANCE_NOT_FOUND.exception(cluster));
        Set<String> capabilities = capabilityResolver.resolve(instance);

        return catalog.list().stream()
                .filter(definition -> capabilities.containsAll(
                        definition.requiredCapabilities()))
                .map(definition -> toView(definition, catalog.getVersion()))
                .toList();
    }

    private static AiToolVO toView(
            ToolDefinition definition,
            String catalogVersion) {
        return AiToolVO.builder()
                .name(definition.name())
                .version(catalogVersion)
                .cli(definition.cli())
                .description(definition.description())
                .parameters(definition.inputSchema())
                .riskLevel(definition.riskLevel().name())
                .operationLevel(definition.riskLevel().operationLevel())
                .permission(definition.permission())
                .requiredCapabilities(definition.requiredCapabilities())
                .outputSchema(definition.outputSchema())
                .viewHint(definition.viewHint())
                .deprecated(definition.deprecated())
                .replacement(definition.replacement())
                .implemented(true)
                .build();
    }
}
