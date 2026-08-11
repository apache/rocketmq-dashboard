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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.body.SubscriptionGroupWrapper;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.route.QueueData;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.apache.rocketmq.studio.common.domain.enums.ClusterType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.ops.dashboard.ClusterOverviewVO;
import org.apache.rocketmq.studio.ops.dashboard.DashboardDataVO;
import org.apache.rocketmq.studio.ops.dashboard.DashboardProvider;
import org.apache.rocketmq.studio.ops.dashboard.DashboardStatsVO;
import org.apache.rocketmq.studio.common.util.SystemGroupFilter;
import org.apache.rocketmq.studio.common.util.SystemTopicFilter;
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
    private final RuntimeAdminClientResolver runtimeAdminClientResolver;

    @Override
    public DashboardDataVO getDashboardData() {
        String namesrvAddr = properties.getNamesrvAddr();
        if (!StringUtils.hasText(namesrvAddr)) {
            log.warn("NameServer address not configured, returning empty dashboard");
            return emptyDashboard();
        }
        return adminFactory.execute(namesrvAddr, null,
                admin -> collectDashboardData(admin, ClusterType.V5_PROXY_CLUSTER));
    }

    @Override
    public DashboardDataVO getDashboardData(String instanceId) {
        InstanceVO instance = runtimeAdminClientResolver.resolveInstance(instanceId);
        return runtimeAdminClientResolver.execute(instance,
                admin -> collectDashboardData(admin, clusterTypeFor(instance)));
    }

    private DashboardDataVO collectDashboardData(MQAdminExt admin, ClusterType clusterType) {
        int totalClusters = 0;
        int totalBrokers = 0;
        int totalTopics = 0;
        int totalGroups = 0;
        long tpsIn = 0;
        long tpsOut = 0;
        long messagesToday = 0;
        List<ClusterOverviewVO> clusters = new ArrayList<>();
        Map<String, KVTable> runtimeStatsByBroker = new HashMap<>();

        try {
            ClusterInfo clusterInfo = admin.examineBrokerClusterInfo();
            if (clusterInfo == null) {
                throw new BusinessException(502, "Failed to collect dashboard data: NameServer returned no cluster topology");
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

            // Build brokerName -> clusterName and brokerAddr -> clusterName maps
            // for per-cluster topic and group counting.
            Map<String, String> brokerAddrToCluster = new HashMap<>();
            for (Map.Entry<String, Set<String>> clusterEntry : clusterAddrTable.entrySet()) {
                String clusterName = clusterEntry.getKey();
                if (clusterEntry.getValue() == null) {
                    continue;
                }
                for (String brokerName : clusterEntry.getValue()) {
                    BrokerData brokerData = brokerAddrTable.get(brokerName);
                    if (brokerData != null && brokerData.getBrokerAddrs() != null) {
                        String masterAddr = brokerData.getBrokerAddrs().get(0L);
                        if (masterAddr != null) {
                            brokerAddrToCluster.put(masterAddr, clusterName);
                        }
                    }
                }
            }

            // Count topics globally and per-cluster
            Map<String, Integer> topicsByCluster = new HashMap<>();
            try {
                TopicList topicList = admin.fetchAllTopicList();
                Set<String> topics = topicList == null || topicList.getTopicList() == null
                        ? Set.of() : topicList.getTopicList();
                totalTopics = (int) topics.stream()
                        .filter(t -> !isSystemTopic(t))
                        .count();
                // For each non-system topic, determine its cluster via route data
                for (String topicName : topics) {
                    if (isSystemTopic(topicName)) {
                        continue;
                    }
                    try {
                        TopicRouteData route = admin.examineTopicRouteInfo(topicName);
                        if (route != null && route.getQueueDatas() != null) {
                            for (QueueData qd : route.getQueueDatas()) {
                                BrokerData bd = brokerAddrTable.get(qd.getBrokerName());
                                if (bd != null && bd.getBrokerAddrs() != null) {
                                    String addr = bd.getBrokerAddrs().get(0L);
                                    String cluster = brokerAddrToCluster.get(addr);
                                    if (cluster != null) {
                                        topicsByCluster.merge(cluster, 1, Integer::sum);
                                    }
                                    break; // one queue is enough to determine the cluster
                                }
                            }
                        }
                    } catch (Exception ignored) {
                        // Skip topics whose route data is unavailable
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to fetch topic list: {}", e.getMessage());
            }

            // Count subscription groups globally and per-cluster, and collect TPS from each master broker
            Set<String> allGroups = new HashSet<>();
            Map<String, Integer> groupsByCluster = new HashMap<>();
            for (String brokerAddr : masterAddrs) {
                String clusterName = brokerAddrToCluster.get(brokerAddr);
                try {
                    SubscriptionGroupWrapper subscriptionGroupWrapper = admin.getAllSubscriptionGroup(brokerAddr, 5000);
                    if (subscriptionGroupWrapper != null && subscriptionGroupWrapper.getSubscriptionGroupTable() != null) {
                        for (Map.Entry<String, SubscriptionGroupConfig> entry :
                                subscriptionGroupWrapper.getSubscriptionGroupTable().entrySet()) {
                            String groupName = entry.getKey();
                            if (!isSystemGroup(groupName)) {
                                allGroups.add(groupName);
                                if (clusterName != null) {
                                    groupsByCluster.merge(clusterName, 1, Integer::sum);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to get subscription groups from broker {}: {}", brokerAddr, e.getMessage());
                }

                // Get runtime stats for TPS
                try {
                    KVTable runtimeInfo = admin.fetchBrokerRuntimeStats(brokerAddr);
                    runtimeStatsByBroker.put(brokerAddr, runtimeInfo);
                    if (runtimeInfo != null && runtimeInfo.getTable() != null) {
                        Map<String, String> table = runtimeInfo.getTable();
                        tpsIn += parseTps(table.get("putTps"));
                        tpsOut += parseTps(table.get("getTransferredTps"));

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
                            KVTable runtimeInfo = runtimeStatsByBroker.get(masterAddr);
                            if (runtimeInfo != null && runtimeInfo.getTable() != null) {
                                clusterTpsIn += parseTps(runtimeInfo.getTable().get("putTps"));
                                clusterTpsOut += parseTps(runtimeInfo.getTable().get("getTransferredTps"));
                                String value = runtimeInfo.getTable().get("brokerVersionDesc");
                                if (value != null && "unknown".equals(version)) {
                                    String brokerVersion = value.trim();
                                    if (!brokerVersion.isEmpty()) {
                                        version = brokerVersion;
                                    }
                                }
                            } else {
                                runtimeMetricsUnavailable = true;
                            }
                        }
                    }
                }

                clusters.add(ClusterOverviewVO.builder()
                        .id(clusterName)
                        .name(clusterName)
                        .type(clusterType)
                        .status(runtimeMetricsUnavailable ? ClusterStatus.warning : ClusterStatus.healthy)
                        .brokers(clusterBrokers)
                        .proxies(0)
                        .topics(topicsByCluster.getOrDefault(clusterName, 0))
                        .groups(groupsByCluster.getOrDefault(clusterName, 0))
                        .tpsIn(clusterTpsIn)
                        .tpsOut(clusterTpsOut)
                        .version(version)
                        .throughput(List.of())
                        .build());
            }

        } catch (Exception e) {
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            log.error("Failed to collect dashboard data from RocketMQ cluster", e);
            throw new BusinessException(502, "Failed to collect dashboard data: " + e.getMessage());
        }

        long messagesPerSecond = tpsIn + tpsOut;

        int healthyClusters = Math.toIntExact(clusters.stream()
                .filter(cluster -> cluster.getStatus() == ClusterStatus.healthy)
                .count());

        DashboardStatsVO stats = DashboardStatsVO.builder()
                .totalClusters(totalClusters)
                .healthyClusters(healthyClusters)
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

    private ClusterType clusterTypeFor(InstanceVO instance) {
        return instance.getType() == InstanceType.DIRECT
                ? ClusterType.V4_DIRECT : ClusterType.V5_PROXY_CLUSTER;
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
        return SystemTopicFilter.isSystem(topic);
    }

    private boolean isSystemGroup(String group) {
        return SystemGroupFilter.isSystem(group);
    }
}
