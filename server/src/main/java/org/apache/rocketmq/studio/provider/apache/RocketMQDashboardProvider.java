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
package org.apache.rocketmq.studio.provider.apache;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.body.SubscriptionGroupWrapper;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.apache.rocketmq.studio.common.domain.enums.ClusterType;
import org.apache.rocketmq.studio.ops.dashboard.ClusterOverviewVO;
import org.apache.rocketmq.studio.ops.dashboard.DashboardDataVO;
import org.apache.rocketmq.studio.ops.dashboard.DashboardProvider;
import org.apache.rocketmq.studio.ops.dashboard.DashboardStatsVO;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
@Primary
public class RocketMQDashboardProvider implements DashboardProvider {

    private static final Set<String> SYSTEM_TOPIC_PREFIXES = Set.of(
            "rmq_sys_", "SCHEDULE_TOPIC_", "RMQ_SYS_", "broker_", "%RETRY%", "%DLQ%",
            "TBW102", "SELF_TEST_TOPIC", "BenchmarkTest", "OFFSET_MOVED_EVENT",
            "DefaultCluster", "broker-a", "broker-b"
    );

    private final MqAdminExtFactory adminFactory;
    private final RocketMQProperties properties;

    @Override
    public DashboardDataVO getDashboardData() {
        String namesrvAddr = properties.getNamesrvAddr();
        if (!StringUtils.hasText(namesrvAddr)) {
            log.warn("NameServer address not configured, returning empty dashboard");
            return emptyDashboard();
        }
        return adminFactory.execute(namesrvAddr, null, this::collectDashboardData);
    }

    private DashboardDataVO collectDashboardData(MQAdminExt admin) {
        int totalClusters = 0;
        int totalBrokers = 0;
        int totalTopics = 0;
        int totalGroups = 0;
        long tpsIn = 0;
        long tpsOut = 0;
        long messagesToday = 0;
        List<ClusterOverviewVO> clusters = new ArrayList<>();

        try {
            ClusterInfo clusterInfo = admin.examineBrokerClusterInfo();
            if (clusterInfo == null) {
                log.warn("NameServer returned no cluster topology, returning empty dashboard");
                return emptyDashboard();
            }
            // The NameServer topology is decoded from JSON; a payload missing either
            // table yields null here. Treat it as empty so a partial topology degrades
            // instead of throwing and zeroing the whole dashboard.
            Map<String, Set<String>> clusterAddrTable =
                    clusterInfo.getClusterAddrTable() == null
                            ? Map.of() : clusterInfo.getClusterAddrTable();
            Map<String, BrokerData> brokerAddrTable =
                    clusterInfo.getBrokerAddrTable() == null
                            ? Map.of() : clusterInfo.getBrokerAddrTable();

            totalClusters = clusterAddrTable.size();
            totalBrokers = brokerAddrTable.size();

            // Collect all unique broker addresses (master only, brokerId=0)
            Set<String> masterAddrs = new HashSet<>();

            for (BrokerData brokerData : brokerAddrTable.values()) {
                if (brokerData == null || brokerData.getBrokerAddrs() == null) {
                    continue;
                }
                String masterAddr = brokerData.getBrokerAddrs().get(0L);
                if (masterAddr != null) {
                    masterAddrs.add(masterAddr);
                }
            }
            if (masterAddrs.isEmpty()) {
                log.warn("No master broker addresses discovered for dashboard overview");
            }

            // Count topics
            try {
                TopicList topicList = admin.fetchAllTopicList();
                Set<String> topics = topicList == null || topicList.getTopicList() == null
                        ? Set.of() : topicList.getTopicList();
                totalTopics = (int) topics.stream()
                        .filter(t -> !isSystemTopic(t))
                        .count();
            } catch (Exception e) {
                log.warn("Failed to fetch topic list: {}", e.getMessage());
            }

            // Count subscription groups and collect TPS from each master broker
            Set<String> allGroups = new HashSet<>();
            for (String brokerAddr : masterAddrs) {
                try {
                    SubscriptionGroupWrapper subscriptionGroupWrapper = admin.getAllSubscriptionGroup(brokerAddr, 5000);
                    if (subscriptionGroupWrapper != null && subscriptionGroupWrapper.getSubscriptionGroupTable() != null) {
                        for (Map.Entry<String, SubscriptionGroupConfig> entry :
                                subscriptionGroupWrapper.getSubscriptionGroupTable().entrySet()) {
                            String groupName = entry.getKey();
                            if (!isSystemGroup(groupName)) {
                                allGroups.add(groupName);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to get subscription groups from broker {}: {}", brokerAddr, e.getMessage());
                }

                // Get runtime stats for TPS
                try {
                    KVTable runtimeInfo = admin.fetchBrokerRuntimeStats(brokerAddr);
                    if (runtimeInfo != null && runtimeInfo.getTable() != null) {
                        Map<String, String> table = runtimeInfo.getTable();
                        tpsIn += parseTps(table.get("putTps"));
                        tpsOut += parseTps(table.get("getTransferedTps"));

                        String msgPutToday = table.get("msgPutTotalTodayMorning");
                        if (msgPutToday != null) {
                            try {
                                messagesToday += Long.parseLong(msgPutToday.trim());
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to get runtime info from broker {}: {}", brokerAddr, e.getMessage());
                }
            }
            totalGroups = allGroups.size();

            // Build per-cluster overview
            for (Map.Entry<String, Set<String>> clusterEntry : clusterAddrTable.entrySet()) {
                String clusterName = clusterEntry.getKey();
                Set<String> brokerNames = clusterEntry.getValue() == null ? Set.of() : clusterEntry.getValue();
                int clusterBrokers = 0;
                long clusterTpsIn = 0;
                long clusterTpsOut = 0;
                String version = "unknown";
                boolean runtimeMetricsUnavailable = false;

                for (String brokerName : brokerNames) {
                    BrokerData brokerData = brokerAddrTable.get(brokerName);
                    if (brokerData != null && brokerData.getBrokerAddrs() != null) {
                        clusterBrokers++;
                        String masterAddr = brokerData.getBrokerAddrs().get(0L);
                        if (masterAddr != null) {
                            try {
                                KVTable rt = admin.fetchBrokerRuntimeStats(masterAddr);
                                if (rt != null && rt.getTable() != null) {
                                    clusterTpsIn += parseTps(rt.getTable().get("putTps"));
                                    clusterTpsOut += parseTps(rt.getTable().get("getTransferedTps"));
                                    String v = rt.getTable().get("brokerVersionDesc");
                                    if (v != null && "unknown".equals(version)) {
                                        String brokerVersion = v.trim();
                                        if (!brokerVersion.isEmpty()) {
                                            version = brokerVersion;
                                        }
                                    }
                                } else {
                                    runtimeMetricsUnavailable = true;
                                }
                            } catch (Exception e) {
                                runtimeMetricsUnavailable = true;
                                log.warn("Failed to get runtime info for dashboard cluster {} from broker {}: {}",
                                        clusterName, masterAddr, e.getMessage());
                            }
                        }
                    }
                }

                clusters.add(ClusterOverviewVO.builder()
                        .id(clusterName)
                        .name(clusterName)
                        .type(ClusterType.V5_PROXY_CLUSTER)
                        .status(runtimeMetricsUnavailable ? ClusterStatus.warning : ClusterStatus.healthy)
                        .brokers(clusterBrokers)
                        .proxies(0)
                        .topics(0)
                        .groups(0)
                        .tpsIn(clusterTpsIn)
                        .tpsOut(clusterTpsOut)
                        .version(version)
                        .throughput(List.of())
                        .build());
            }

        } catch (Exception e) {
            log.error("Failed to collect dashboard data from RocketMQ cluster", e);
            return emptyDashboard();
        }

        long messagesPerSecond = tpsIn + tpsOut;

        DashboardStatsVO stats = DashboardStatsVO.builder()
                .totalClusters(totalClusters)
                .healthyClusters(totalClusters)
                .totalBrokers(totalBrokers)
                .totalProxies(0)
                .totalNameServers(0)
                .totalTopics(totalTopics)
                .totalConsumerGroups(totalGroups)
                .totalMessagesToday(messagesToday)
                .messagesPerSecond(messagesPerSecond)
                .tpsIn(tpsIn)
                .tpsOut(tpsOut)
                .build();

        return DashboardDataVO.builder()
                .stats(stats)
                .clusters(clusters)
                .build();
    }

    private DashboardDataVO emptyDashboard() {
        DashboardStatsVO stats = DashboardStatsVO.builder()
                .totalClusters(0)
                .healthyClusters(0)
                .totalBrokers(0)
                .totalProxies(0)
                .totalNameServers(0)
                .totalTopics(0)
                .totalConsumerGroups(0)
                .totalMessagesToday(0)
                .messagesPerSecond(0)
                .tpsIn(0)
                .tpsOut(0)
                .build();
        return DashboardDataVO.builder()
                .stats(stats)
                .clusters(List.of())
                .build();
    }

    /**
     * Parse TPS value from RocketMQ runtime stats format: "10minAvg 1minAvg 10secAvg"
     * Returns the 1-minute average (second value) as a long.
     */
    private long parseTps(String tpsStr) {
        if (tpsStr == null || tpsStr.isBlank()) {
            return 0;
        }
        try {
            String[] parts = tpsStr.trim().split("\\s+");
            if (parts.length >= 2) {
                return (long) Double.parseDouble(parts[1]);
            } else if (parts.length == 1) {
                return (long) Double.parseDouble(parts[0]);
            }
        } catch (NumberFormatException e) {
            log.debug("Failed to parse TPS value: {}", tpsStr);
        }
        return 0;
    }

    private boolean isSystemTopic(String topic) {
        for (String prefix : SYSTEM_TOPIC_PREFIXES) {
            if (topic.startsWith(prefix) || topic.equals(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSystemGroup(String group) {
        return group.startsWith("CID_RMQ_SYS_")
                || group.startsWith("rmq_sys_")
                || group.equals("TOOLS_CONSUMER")
                || group.equals("FILTERSRV_CONSUMER")
                || group.equals("CID_ONSAPI_OWNER")
                || group.equals("CID_ONSAPI_PERMISSION")
                || group.equals("CID_ONSAPI_PULL")
                || group.startsWith("SELF_TEST_");
    }
}
