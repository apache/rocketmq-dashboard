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

import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.cluster.broker.BrokerVO;
import org.apache.rocketmq.studio.cluster.broker.ClusterProvider;
import org.apache.rocketmq.studio.cluster.broker.ClusterVO;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.cluster.nameserver.NameServerVO;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.domain.enums.BrokerStatus;
import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Real cluster discovery implementation using the RocketMQ admin API.
 * Falls back to returning empty results when no NameServer is configured or connection fails.
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class RocketMQClusterProvider implements ClusterProvider {

    private final MqAdminExtFactory adminFactory;
    private final RocketMQProperties properties;
    private final RuntimeAdminClientResolver runtimeAdminClientResolver;

    @Override
    public List<ClusterVO> discoverClusters() {
        return discoverClusters(null);
    }

    @Override
    public List<ClusterVO> discoverClusters(String instanceId) {
        String namesrvAddr = resolveNamesrvAddr(instanceId);
        if (!StringUtils.hasText(namesrvAddr)) {
            log.debug("NameServer address not configured, returning empty cluster list");
            return Collections.emptyList();
        }

        try {
            return adminFactory.execute(namesrvAddr, null, admin -> {
                ClusterInfo clusterInfo = admin.examineBrokerClusterInfo();
                if (clusterInfo == null || clusterInfo.getClusterAddrTable() == null) {
                    return Collections.<ClusterVO>emptyList();
                }

                Map<String, Set<String>> clusterAddrTable = clusterInfo.getClusterAddrTable();
                Map<String, BrokerData> brokerAddrTable = clusterInfo.getBrokerAddrTable();

                List<ClusterVO> clusters = new ArrayList<>();
                for (Map.Entry<String, Set<String>> entry : clusterAddrTable.entrySet()) {
                    String clusterName = entry.getKey();
                    Set<String> brokerNames = entry.getValue();

                    List<BrokerVO> brokers = buildBrokerList(admin, brokerNames, brokerAddrTable);
                    List<NameServerVO> nameServers = buildNameServerList(namesrvAddr);

                    ClusterVO cluster = buildClusterVO(clusterName, brokers, nameServers);
                    clusters.add(cluster);
                }
                return clusters;
            });
        } catch (Exception e) {
            log.warn("Failed to discover clusters via NameServer: {}", e.getMessage());
            throw new BusinessException(502,
                    "Failed to discover clusters via NameServer: " + rootMessage(e));
        }
    }

    @Override
    public ClusterVO refreshClusterDetail(String clusterId) {
        return refreshClusterDetail(clusterId, null);
    }

    @Override
    public ClusterVO refreshClusterDetail(String clusterId, String instanceId) {
        String namesrvAddr = resolveNamesrvAddr(instanceId);
        if (!StringUtils.hasText(namesrvAddr)) {
            log.debug("NameServer address not configured, cannot refresh cluster detail");
            return null;
        }

        try {
            return adminFactory.execute(namesrvAddr, null, admin -> {
                ClusterInfo clusterInfo = admin.examineBrokerClusterInfo();
                if (clusterInfo == null || clusterInfo.getClusterAddrTable() == null) {
                    return null;
                }

                Map<String, Set<String>> clusterAddrTable = clusterInfo.getClusterAddrTable();
                Map<String, BrokerData> brokerAddrTable = clusterInfo.getBrokerAddrTable();

                Set<String> brokerNames = clusterAddrTable.get(clusterId);
                if (brokerNames == null) {
                    return null;
                }

                List<BrokerVO> brokers = buildBrokerList(admin, brokerNames, brokerAddrTable);
                List<NameServerVO> nameServers = buildNameServerList(namesrvAddr);

                return buildClusterVO(clusterId, brokers, nameServers);
            });
        } catch (Exception e) {
            log.warn("Failed to refresh cluster detail for {}: {}", clusterId, e.getMessage());
            return null;
        }
    }

    /**
     * Build a cluster view with safe defaults so downstream consumers (web UI, dashboard)
     * never receive null collections for fields like proxies or tpsHistory.
     */
    private ClusterVO buildClusterVO(String clusterName, List<BrokerVO> brokers,
                                     List<NameServerVO> nameServers) {
        ClusterVO cluster = ClusterVO.builder()
                .name(clusterName)
                .status(hasUnavailableRuntimeStats(brokers) ? ClusterStatus.warning : ClusterStatus.healthy)
                .brokers(brokers != null ? brokers : Collections.emptyList())
                .proxies(Collections.emptyList())
                .nameServers(nameServers != null ? nameServers : Collections.emptyList())
                .tpsHistory(Collections.emptyList())
                .topicCount(0)
                .groupCount(0)
                .build();
        cluster.setId(clusterName);
        return cluster;
    }

    private List<BrokerVO> buildBrokerList(MQAdminExt admin, Set<String> brokerNames,
                                           Map<String, BrokerData> brokerAddrTable) {
        List<BrokerVO> brokers = new ArrayList<>();
        if (brokerNames == null || brokerAddrTable == null) {
            return brokers;
        }

        for (String brokerName : brokerNames) {
            BrokerData brokerData = brokerAddrTable.get(brokerName);
            if (brokerData == null || brokerData.getBrokerAddrs() == null) {
                continue;
            }

            // Use master address (brokerId = 0) preferentially
            String masterAddr = brokerData.getBrokerAddrs().get(0L);
            if (masterAddr == null && !brokerData.getBrokerAddrs().isEmpty()) {
                masterAddr = brokerData.getBrokerAddrs().values().iterator().next();
            }
            if (masterAddr == null) {
                continue;
            }

            BrokerVO.BrokerVOBuilder builder = BrokerVO.builder()
                    .name(brokerName)
                    .addr(masterAddr)
                    .status(BrokerStatus.running);

            // Try to get runtime info for version and TPS
            builder.runtimeStatsAvailable(enrichBrokerWithRuntimeInfo(admin, builder, masterAddr));

            brokers.add(builder.build());
        }
        return brokers;
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }

    private boolean enrichBrokerWithRuntimeInfo(MQAdminExt admin, BrokerVO.BrokerVOBuilder builder, String brokerAddr) {
        try {
            KVTable runtimeInfo = admin.fetchBrokerRuntimeStats(brokerAddr);
            if (runtimeInfo == null || runtimeInfo.getTable() == null) {
                return false;
            }

            Map<String, String> table = runtimeInfo.getTable();

            String version = table.getOrDefault("brokerVersionDesc",
                    table.getOrDefault("rocketmqVersion", ""));
            if (!version.isEmpty()) {
                builder.version(version);
            }

            // Parse TPS from runtime stats
            String putTps = table.get("putTps");
            if (putTps != null && !putTps.isEmpty()) {
                builder.tpsIn(parseFirstTpsValue(putTps));
            }

            String getTransferredTps = table.get("getTransferredTps");
            if (getTransferredTps != null && !getTransferredTps.isEmpty()) {
                builder.tpsOut(parseFirstTpsValue(getTransferredTps));
            }

            // Disk usage
            String diskRatio = table.get("commitLogDiskRatio");
            if (diskRatio != null && !diskRatio.isEmpty()) {
                try {
                    builder.diskUsage(Double.parseDouble(diskRatio));
                } catch (NumberFormatException ignored) {
                    // keep default
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("Failed to get runtime info for broker at {}: {}", brokerAddr, e.getMessage());
            return false;
        }
    }

    private boolean hasUnavailableRuntimeStats(List<BrokerVO> brokers) {
        return brokers != null && brokers.stream().anyMatch(broker -> !broker.isRuntimeStatsAvailable());
    }

    /**
     * TPS properties are space-separated values representing 10s/1min/10min averages.
     * Parse the first value (10s average).
     */
    private int parseFirstTpsValue(String tpsStr) {
        try {
            String[] parts = tpsStr.trim().split("\\s+");
            if (parts.length > 0) {
                return (int) Double.parseDouble(parts[0]);
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return 0;
    }

    private String resolveNamesrvAddr(String instanceId) {
        if (StringUtils.hasText(instanceId)) {
            return runtimeAdminClientResolver.resolveEndpoint(instanceId);
        }
        return properties.getNamesrvAddr();
    }

    private List<NameServerVO> buildNameServerList(String namesrvAddr) {
        List<NameServerVO> nameServers = new ArrayList<>();
        if (namesrvAddr == null || namesrvAddr.isEmpty()) {
            return nameServers;
        }

        String[] addrs = namesrvAddr.split("[;,]");
        for (String addr : addrs) {
            String trimmed = addr.trim();
            if (!trimmed.isEmpty()) {
                nameServers.add(NameServerVO.builder()
                        .addr(trimmed)
                        .status(ClusterStatus.healthy)
                        .build());
            }
        }
        return nameServers;
    }
}
