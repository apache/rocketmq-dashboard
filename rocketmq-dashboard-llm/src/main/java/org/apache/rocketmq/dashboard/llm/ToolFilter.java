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
package org.apache.rocketmq.dashboard.llm;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.rocketmq.dashboard.cli.schema.RiskLevel;
import org.apache.rocketmq.dashboard.cli.schema.ToolDefinition;

/**
 * Filters tool definitions based on user role and cluster capabilities.
 */
public class ToolFilter {

    /**
     * Filter tools for a specific user and cluster.
     * <ul>
     *   <li>ADMIN users: see all tools supported by the cluster</li>
     *   <li>Normal users: L1 (read-only) tools only, filtered by capability</li>
     * </ul>
     *
     * @param allTools   all registered tool definitions
     * @param userRole   the user's role (e.g. "ADMIN")
     * @param capability the cluster capability to filter against
     * @return filtered list of tool definitions
     */
    public static List<ToolDefinition> filterForUser(List<ToolDefinition> allTools,
                                                      String userRole,
                                                      ClusterCapability capability) {
        return allTools.stream()
                .filter(t -> "ADMIN".equals(userRole) || t.getRiskLevel() == RiskLevel.L1)
                .filter(t -> capability == null || capability.supports(t.getResource()))
                .collect(Collectors.toList());
    }
}
