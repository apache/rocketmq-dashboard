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
package org.apache.rocketmq.studio.ops.ai.tool;

import org.apache.rocketmq.studio.cluster.broker.ClusterService;
import org.apache.rocketmq.studio.cluster.broker.ClusterVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ClusterListToolHandler implements ToolHandler {

    private static final String NAME = "rmq.cluster.list";

    private final ClusterService clusterService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Object execute(Map<String, Object> input) {
        return clusterService.listClusters().stream()
                .map(ClusterListToolHandler::safeProjection)
                .toList();
    }

    private static Map<String, Object> safeProjection(ClusterVO cluster) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", blankIfNull(cluster.getId()));
        result.put("name", blankIfNull(cluster.getName()));
        result.put("type", requiredEnumName(cluster.getType(), "type", cluster.getId()));
        result.put("status", requiredEnumName(cluster.getStatus(), "status", cluster.getId()));
        result.put("version", blankIfNull(cluster.getVersion()));
        return result;
    }

    /**
     * The output schema declares id/name/version as required strings; providers that do
     * not populate them (notably the Apache runtime provider, which reports no cluster
     * version) must not emit nulls or schema validation turns the tool call into a 500.
     */
    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }

    private static String requiredEnumName(Enum<?> value, String field, String clusterId) {
        if (value == null) {
            throw new IllegalStateException(
                    "Cluster " + field + " is unavailable: " + clusterId);
        }
        return value.name();
    }
}
