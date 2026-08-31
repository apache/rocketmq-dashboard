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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.body.SubscriptionGroupWrapper;
import org.apache.rocketmq.remoting.protocol.body.TopicConfigSerializeWrapper;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.apache.rocketmq.studio.common.domain.enums.ClusterType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceRepository;
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
    private final InstanceRepository instanceRepository;

    @Override
    public DashboardDataVO getDashboardData() {
        List<InstanceVO> apacheInstances = instanceRepository.findAll().stream()
                .filter(instance -> instance.getVendor() == null || instance.getVendor() == InstanceVendor.APACHE)
                .sorted(Comparator.comparing(InstanceVO::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (apacheInstances.isEmpty()) {
            String namesrvAddr = properties.getNamesrvAddr();
            if (!StringUtils.hasText(namesrvAddr)) {
                log.warn("NameServer address not configured, returning empty dashboard");
                return unavailableTopologyDashboard();
            }
            return adminFactory.execute(namesrvAddr, null,
                    admin -> collectDashboardData(admin, ClusterType.V5_PROXY_CLUSTER, countEndpoints(namesrvAddr)));
        }
        return aggregateInstances(apacheInstances);
    }

    /**
     * Aggregates every registered Apache instance into one overview. Instances whose endpoint
     * cannot be reached contribute a warning row instead of failing the whole dashboard.
     */
    private DashboardDataVO aggregateInstances(List<InstanceVO> instances) {
        int totalClusters = 0;
        int healthyClusters = 0;
        int totalBrokers = 0;
        int totalNameServers = 0;
        int totalTopics = 0;
        int totalGroups = 0;
        long tpsIn = 0;
        long tpsOut = 0;
        long messagesToday = 0;
        List<ClusterOverviewVO> clusters = new ArrayList<>();

        for (InstanceVO instance : instances) {
            DashboardDataVO part;
            try {
                part = getDashboardData(instance.getName());
            } catch (Exception e) {
                log.warn("Failed to collect dashboard data for instance {}: {}",
                        instance.getName(), e.getMessage());
                clusters.add(unavailableInstanceCluster(instance));
                totalClusters++;
                continue;
            }
            DashboardStatsVO stats = part.getStats();
            totalClusters += stats.getTotalClusters();
            healthyClusters += stats.getHealthyClusters();
            totalBrokers += stats.getTotalBrokers();
            totalNameServers += stats.getTotalNameServers() == null ? 0 : stats.getTotalNameServers();
            totalTopics += stats.getTotalTopics();
            totalGroups += stats.getTotalConsumerGroups();
            tpsIn += stats.getTpsIn();
            tpsOut += stats.getTpsOut();
            messagesToday += stats.getTotalMessagesToday();
            for (ClusterOverviewVO cluster : part.getClusters()) {
                clusters.add(scopeClusterToInstance(instance.getName(), cluster));
            }
        }

        DashboardStatsVO stats = DashboardStatsVO.builder()
                .totalClusters(totalClusters)
                .healthyClusters(healthyClusters)
                .totalBrokers(totalBrokers)
                .totalProxies(null)
                .totalNameServers(totalNameServers)
                .totalTopics(totalTopics)
                .totalConsumerGroups(totalGroups)
                .totalMessagesToday(messagesToday)
                .messagesPerSecond(tpsIn + tpsOut)
                .tpsIn(tpsIn)
                .tpsOut(tpsOut)
                .build();
        return DashboardDataVO.builder().stats(stats).clusters(clusters).build();
    }

    private ClusterOverviewVO scopeClusterToInstance(String instanceName, ClusterOverviewVO cluster) {
        return ClusterOverviewVO.builder()
                .id(instanceName + "/" + cluster.getId())
                .name(instanceName + " / " + cluster.getName())
                .type(cluster.getType())
                .status(cluster.getStatus())
                .brokers(cluster.getBrokers())
                .proxies(cluster.getProxies())
                .topics(cluster.getTopics())
                .groups(cluster.getGroups())
                .tpsIn(cluster.getTpsIn())
                .tpsOut(cluster.getTpsOut())
                .version(cluster.getVersion())
                .throughput(cluster.getThroughput())
                .build();
    }

    private ClusterOverviewVO unavailableInstanceCluster(InstanceVO instance) {
        ClusterType clusterType = clusterTypeFor(instance);
        return ClusterOverviewVO.builder()
                .id(instance.getName())
                .name(instance.getName())
                .type(clusterType)
                .status(ClusterStatus.warning)
                .brokers(0)
                .proxies(clusterType == ClusterType.V4_DIRECT ? 0 : null)
                .topics(0)
                .groups(0)
                .tpsIn(0)
                .tpsOut(0)
                .version("unknown")
                .throughput(List.of())
                .build();
    }

    @Override
    public DashboardDataVO getDashboardData(String instanceId) {
        InstanceVO instance = runtimeAdminClientResolver.resolveInstance(instanceId);
        Integer configuredNameServers = instance.getType() == InstanceType.DIRECT
                ? countEndpoints(instance.getEndpoint()) : null;
        return runtimeAdminClientResolver.execute(instance,
                admin -> collectDashboardData(admin, clusterTypeFor(instance), configuredNameServers));
    }

    private DashboardDataVO collectDashboardData(
            MQAdminExt admin, ClusterType clusterType, Integer configuredNameServers) {
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

            Set<String> topologyUnavailableClusters = new HashSet<>();
            for (Map.Entry<String, Set<String>> clusterEntry : clusterAddrTable.entrySet()) {
                Set<String> brokerNames = clusterEntry.getValue();
                if (brokerNames == null || brokerNames.isEmpty()) {
                    topologyUnavailableClusters.add(clusterEntry.getKey());
                    continue;
                }
                boolean incomplete = brokerNames.stream().anyMatch(brokerName -> {
                    BrokerData brokerData = brokerAddrTable.get(brokerName);
                    return brokerData == null || brokerData.getBrokerAddrs() == null
                            || brokerData.getBrokerAddrs().get(0L) == null;
                });
                if (incomplete) {
                    topologyUnavailableClusters.add(clusterEntry.getKey());
                }
            }

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

            // Count topics and subscription groups from each master broker. Topic metadata is
            // available in the broker config response, so this remains bounded by broker count
            // instead of resolving a NameServer route for every Topic.
            Set<String> allTopics = new HashSet<>();
            Map<String, Set<String>> topicsByCluster = new HashMap<>();
            Set<String> topicCountsUnavailableClusters = new HashSet<>();
            Set<String> allGroups = new HashSet<>();
            Map<String, Set<String>> groupsByCluster = new HashMap<>();
            Set<String> groupCountsUnavailableClusters = new HashSet<>();
            for (String brokerAddr : masterAddrs) {
                String clusterName = brokerAddrToCluster.get(brokerAddr);
                try {
                    TopicConfigSerializeWrapper topicConfig = admin.getAllTopicConfig(brokerAddr, 5000);
                    if (topicConfig == null || topicConfig.getTopicConfigTable() == null) {
                        markCountUnavailable(topicCountsUnavailableClusters, clusterName);
                    } else {
                        // A broker's self-named stats topic (e.g. "broker-a") is not a user topic;
                        // pass the owning cluster's broker names so SystemTopicFilter can rule it out.
                        Set<String> owningBrokerNames = clusterName == null
                                ? Set.of()
                                : clusterAddrTable.getOrDefault(clusterName, Set.of());
                        for (String topic : topicConfig.getTopicConfigTable().keySet()) {
                            if (isSystemTopic(topic, owningBrokerNames)) {
                                continue;
                            }
                            allTopics.add(topic);
                            // A broker outside every cluster (registration/unregistration race)
                            // has no cluster name; count it globally but not per-cluster, matching
                            // the consumer group path below.
                            if (clusterName != null) {
                                topicsByCluster.computeIfAbsent(clusterName, ignored -> new HashSet<>())
                                        .add(topic);
                            }
                        }
                    }
                } catch (Exception e) {
                    markCountUnavailable(topicCountsUnavailableClusters, clusterName);
                    log.warn("Failed to get topic config from broker {}: {}", brokerAddr, e.getMessage());
                }

                try {
                    SubscriptionGroupWrapper subscriptionGroupWrapper = admin.getAllSubscriptionGroup(brokerAddr, 5000);
                    if (subscriptionGroupWrapper == null
                            || subscriptionGroupWrapper.getSubscriptionGroupTable() == null) {
                        markCountUnavailable(groupCountsUnavailableClusters, clusterName);
                    } else {
                        for (Map.Entry<String, SubscriptionGroupConfig> entry :
                                subscriptionGroupWrapper.getSubscriptionGroupTable().entrySet()) {
                            String groupName = entry.getKey();
                            if (!isSystemGroup(groupName)) {
                                allGroups.add(groupName);
                                if (clusterName != null) {
                                    groupsByCluster.computeIfAbsent(clusterName, ignored -> new HashSet<>())
                                            .add(groupName);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    markCountUnavailable(groupCountsUnavailableClusters, clusterName);
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

                        messagesToday += parseMessagesToday(table);
                    }
                } catch (Exception e) {
                    log.warn("Failed to get runtime info from broker {}: {}", brokerAddr, e.getMessage());
                }
            }
            totalTopics = allTopics.size();
            totalGroups = allGroups.size();

            // Build per-cluster overview
            for (Map.Entry<String, Set<String>> clusterEntry : clusterAddrTable.entrySet()) {
                String clusterName = clusterEntry.getKey();
                Set<String> brokerNames = clusterEntry.getValue() == null ? Set.of() : clusterEntry.getValue();
                int clusterBrokers = 0;
                long clusterTpsIn = 0;
                long clusterTpsOut = 0;
                String version = "unknown";
                boolean runtimeMetricsUnavailable = topologyUnavailableClusters.contains(clusterName)
                        || topicCountsUnavailableClusters.contains(clusterName)
                        || groupCountsUnavailableClusters.contains(clusterName);

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
                        .proxies(clusterType == ClusterType.V4_DIRECT ? 0 : null)
                        .topics(topicsByCluster.getOrDefault(clusterName, Set.of()).size())
                        .groups(groupsByCluster.getOrDefault(clusterName, Set.of()).size())
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
                .totalProxies(clusterType == ClusterType.V4_DIRECT ? 0 : null)
                .totalNameServers(configuredNameServers)
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
        return switch (instance.getType()) {
            case DIRECT -> ClusterType.V4_DIRECT;
            case PROXY_LOCAL -> ClusterType.V5_PROXY_LOCAL;
            case CLOUD, PROXY_CLUSTER -> ClusterType.V5_PROXY_CLUSTER;
        };
    }

    private DashboardDataVO unavailableTopologyDashboard() {
        DashboardStatsVO stats = DashboardStatsVO.builder()
                .totalClusters(0)
                .healthyClusters(0)
                .totalBrokers(0)
                .totalProxies(null)
                .totalNameServers(null)
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

    private int countEndpoints(String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            return 0;
        }
        return (int) Arrays.stream(endpoint.split("[;,]"))
                .map(String::trim)
                .filter(address -> !address.isEmpty())
                .distinct()
                .count();
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

    private long parseMessagesToday(Map<String, String> runtimeStats) {
        String morningValue = runtimeStats.get("msgPutTotalTodayMorning");
        String currentValue = runtimeStats.get("msgPutTotalTodayNow");
        if (morningValue == null || currentValue == null) {
            return 0;
        }
        try {
            long morning = Long.parseLong(morningValue.trim());
            long current = Long.parseLong(currentValue.trim());
            if (morning < 0 || current < 0) {
                return 0;
            }
            return Math.max(0, current - morning);
        } catch (NumberFormatException exception) {
            log.debug("Failed to parse today's message counters: morning={}, current={}",
                    morningValue, currentValue);
            return 0;
        }
    }

    private boolean isSystemTopic(String topic) {
        return SystemTopicFilter.isSystem(topic);
    }

    private boolean isSystemTopic(String topic, Set<String> brokerNames) {
        return SystemTopicFilter.isSystem(topic, brokerNames);
    }

    private boolean isSystemGroup(String group) {
        return SystemGroupFilter.isSystem(group);
    }

    private void markCountUnavailable(Set<String> unavailableClusters, String clusterName) {
        if (clusterName != null) {
            unavailableClusters.add(clusterName);
        }
    }
}
