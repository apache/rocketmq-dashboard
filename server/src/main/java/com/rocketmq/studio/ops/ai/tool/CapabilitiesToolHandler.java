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
package com.rocketmq.studio.ops.ai.tool;

import com.rocketmq.studio.cluster.broker.ClusterService;
import com.rocketmq.studio.cluster.broker.ClusterVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CapabilitiesToolHandler implements ToolHandler {

    private static final String NAME = "rmq.capabilities";

    private final ClusterService clusterService;
    private final CapabilityResolver capabilityResolver;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Object execute(Map<String, Object> input) {
        String clusterId = (String) input.get("cluster");
        ClusterVO cluster = clusterService.getCluster(clusterId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cluster", cluster.getId());
        result.put("type", cluster.getType().name());
        result.put("version", cluster.getVersion());
        result.put("capabilities", capabilityResolver.resolve(cluster));
        return result;
    }
}
