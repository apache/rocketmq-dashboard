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
package org.apache.rocketmq.studio.ops.ai.tool.handler.dashboard;

import org.apache.rocketmq.studio.ops.dashboard.ClusterOverviewVO;
import org.apache.rocketmq.studio.ops.dashboard.DashboardDataVO;
import org.apache.rocketmq.studio.ops.dashboard.DashboardService;
import org.apache.rocketmq.studio.ops.dashboard.DashboardStatsVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ClusterInput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DashboardSummaryToolHandler
        implements ToolHandler<ClusterInput, DashboardSummaryToolHandler.Output> {

    private final DashboardService dashboardService;

    @Override
    public String name() {
        return "rmq.dashboard.summary";
    }

    @Override
    public Class<ClusterInput> inputType() {
        return ClusterInput.class;
    }

    @Override
    public Output execute(ClusterInput input, ToolExecutionContext context) {
        DashboardDataVO dashboard = dashboardService.getDashboard(context.cluster());
        return new Output(context.cluster(), clusters(dashboard).stream().map(DashboardSummaryToolHandler::clusterProjection).toList(),
                statsProjection(dashboard == null ? null : dashboard.getStats()));
    }

    private static List<ClusterOverviewVO> clusters(DashboardDataVO dashboard) {
        if (dashboard == null || dashboard.getClusters() == null) {
            return List.of();
        }
        return dashboard.getClusters();
    }

    private static Cluster clusterProjection(ClusterOverviewVO cluster) {
        return new Cluster(
                cluster.getId(),
                cluster.getName(),
                cluster.getType() == null ? null : cluster.getType().name(),
                cluster.getStatus() == null ? null : cluster.getStatus().name(),
                cluster.getBrokers(),
                cluster.getProxies(),
                cluster.getTopics(),
                cluster.getGroups(),
                cluster.getTpsIn(),
                cluster.getTpsOut(),
                cluster.getVersion(),
                cluster.getThroughput() == null ? List.of() : cluster.getThroughput());
    }

    private static Stats statsProjection(DashboardStatsVO stats) {
        DashboardStatsVO safeStats = stats == null ? new DashboardStatsVO() : stats;
        return new Stats(
                safeStats.getTotalClusters(),
                safeStats.getHealthyClusters(),
                safeStats.getTotalBrokers(),
                safeStats.getTotalProxies(),
                safeStats.getTotalNameServers(),
                safeStats.getTotalTopics(),
                safeStats.getTotalConsumerGroups(),
                safeStats.getTotalMessagesToday(),
                safeStats.getMessagesPerSecond(),
                safeStats.getTpsIn(),
                safeStats.getTpsOut());
    }

    public record Output(String cluster, List<Cluster> clusters, Stats stats) {
    }

    public record Cluster(
            String id,
            String name,
            String type,
            String status,
            int brokers,
            Integer proxies,
            int topics,
            int groups,
            long tpsIn,
            long tpsOut,
            String version,
            List<Integer> throughput) {
    }

    public record Stats(
            int totalClusters,
            int healthyClusters,
            int totalBrokers,
            Integer totalProxies,
            Integer totalNameServers,
            int totalTopics,
            int totalConsumerGroups,
            long totalMessagesToday,
            long messagesPerSecond,
            long tpsIn,
            long tpsOut) {
    }
}
