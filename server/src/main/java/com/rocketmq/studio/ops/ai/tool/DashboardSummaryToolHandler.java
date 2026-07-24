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

import com.rocketmq.studio.common.exception.BusinessException;
import com.rocketmq.studio.ops.dashboard.ClusterOverviewVO;
import com.rocketmq.studio.ops.dashboard.DashboardDataVO;
import com.rocketmq.studio.ops.dashboard.DashboardService;
import com.rocketmq.studio.ops.dashboard.DashboardStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DashboardSummaryToolHandler implements ToolHandler {

    private static final String NAME = "rmq.dashboard.summary";

    private final DashboardService dashboardService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Object execute(Map<String, Object> input) {
        String clusterId = (String) input.get("cluster");
        DashboardDataVO dashboard = dashboardService.getDashboard();
        ClusterOverviewVO cluster = clusters(dashboard).stream()
                .filter(item -> clusterId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        404, "Dashboard cluster not found: " + clusterId));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cluster", clusterProjection(cluster));
        result.put("stats", statsProjection(dashboard.getStats()));
        return result;
    }

    private static List<ClusterOverviewVO> clusters(DashboardDataVO dashboard) {
        if (dashboard == null || dashboard.getClusters() == null) {
            return List.of();
        }
        return dashboard.getClusters();
    }

    private static Map<String, Object> clusterProjection(ClusterOverviewVO cluster) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", cluster.getId());
        result.put("name", cluster.getName());
        result.put("type", requiredEnumName(cluster.getType(), "type", cluster.getId()));
        result.put("status", requiredEnumName(
                cluster.getStatus(), "status", cluster.getId()));
        result.put("brokers", cluster.getBrokers());
        result.put("proxies", cluster.getProxies());
        result.put("topics", cluster.getTopics());
        result.put("groups", cluster.getGroups());
        result.put("tpsIn", cluster.getTpsIn());
        result.put("tpsOut", cluster.getTpsOut());
        result.put("version", cluster.getVersion());
        result.put("throughput", cluster.getThroughput() == null
                ? List.of()
                : cluster.getThroughput());
        return result;
    }

    private static Map<String, Object> statsProjection(DashboardStatsVO stats) {
        DashboardStatsVO safeStats = stats == null ? new DashboardStatsVO() : stats;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalClusters", safeStats.getTotalClusters());
        result.put("healthyClusters", safeStats.getHealthyClusters());
        result.put("totalBrokers", safeStats.getTotalBrokers());
        result.put("totalProxies", safeStats.getTotalProxies());
        result.put("totalNameServers", safeStats.getTotalNameServers());
        result.put("totalTopics", safeStats.getTotalTopics());
        result.put("totalConsumerGroups", safeStats.getTotalConsumerGroups());
        result.put("totalMessagesToday", safeStats.getTotalMessagesToday());
        result.put("messagesPerSecond", safeStats.getMessagesPerSecond());
        result.put("tpsIn", safeStats.getTpsIn());
        result.put("tpsOut", safeStats.getTpsOut());
        return result;
    }

    private static String requiredEnumName(Enum<?> value, String field, String clusterId) {
        if (value == null) {
            throw new IllegalStateException(
                    "Cluster " + field + " is unavailable: " + clusterId);
        }
        return value.name();
    }
}
