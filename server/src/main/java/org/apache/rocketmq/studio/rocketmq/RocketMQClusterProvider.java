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
package org.apache.rocketmq.studio.rocketmq;

import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.cluster.broker.BrokerVO;
import org.apache.rocketmq.studio.cluster.broker.ClusterProvider;
import org.apache.rocketmq.studio.cluster.broker.ClusterVO;
import org.apache.rocketmq.studio.cluster.nameserver.NameServerVO;
import org.apache.rocketmq.studio.common.domain.enums.BrokerStatus;
import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Real cluster discovery implementation using DefaultMQAdminExt.
 * Falls back to returning empty results when adminExt is not configured or connection fails.
 */
@Service
@Primary
public class RocketMQClusterProvider implements ClusterProvider {

    private static final Logger log = LoggerFactory.getLogger(RocketMQClusterProvider.class);

    private final DefaultMQAdminExt adminExt;
    private final RocketMQProperties properties;

    @Autowired
    public RocketMQClusterProvider(
            @Autowired(required = false) DefaultMQAdminExt adminExt,
            RocketMQProperties properties) {
        this.adminExt = adminExt;
        this.properties = properties;
    }

    @Override
    public List<ClusterVO> discoverClusters() {
        if (adminExt == null) {
            log.debug("DefaultMQAdminExt not configured, returning empty cluster list");
            return Collections.emptyList();
        }

        try {
            ClusterInfo clusterInfo = adminExt.examineBrokerClusterInfo();
            if (clusterInfo == null || clusterInfo.getClusterAddrTable() == null) {
                return Collections.emptyList();
            }

            Map<String, Set<String>> clusterAddrTable = clusterInfo.getClusterAddrTable();
            Map<String, BrokerData> brokerAddrTable = clusterInfo.getBrokerAddrTable();

            List<ClusterVO> clusters = new ArrayList<>();
            for (Map.Entry<String, Set<String>> entry : clusterAddrTable.entrySet()) {
                String clusterName = entry.getKey();
                Set<String> brokerNames = entry.getValue();

                List<BrokerVO> brokers = buildBrokerList(brokerNames, brokerAddrTable);
                List<NameServerVO> nameServers = buildNameServerList();

                ClusterVO cluster = ClusterVO.builder()
                        .name(clusterName)
                        .status(ClusterStatus.healthy)
                        .brokers(brokers)
                        .nameServers(nameServers)
                        .build();
                cluster.setId(clusterName);
                clusters.add(cluster);
            }
            return clusters;
        } catch (Exception e) {
            log.warn("Failed to discover clusters via NameServer: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public ClusterVO refreshClusterDetail(String clusterId) {
        if (adminExt == null) {
            log.debug("DefaultMQAdminExt not configured, cannot refresh cluster detail");
            return null;
        }

        try {
            ClusterInfo clusterInfo = adminExt.examineBrokerClusterInfo();
            if (clusterInfo == null || clusterInfo.getClusterAddrTable() == null) {
                return null;
            }

            Map<String, Set<String>> clusterAddrTable = clusterInfo.getClusterAddrTable();
            Map<String, BrokerData> brokerAddrTable = clusterInfo.getBrokerAddrTable();

            Set<String> brokerNames = clusterAddrTable.get(clusterId);
            if (brokerNames == null) {
                return null;
            }

            List<BrokerVO> brokers = buildBrokerList(brokerNames, brokerAddrTable);
            List<NameServerVO> nameServers = buildNameServerList();

            ClusterVO cluster = ClusterVO.builder()
                    .name(clusterId)
                    .status(ClusterStatus.healthy)
                    .brokers(brokers)
                    .nameServers(nameServers)
                    .build();
            cluster.setId(clusterId);
            return cluster;
        } catch (Exception e) {
            log.warn("Failed to refresh cluster detail for {}: {}", clusterId, e.getMessage());
            return null;
        }
    }

    private List<BrokerVO> buildBrokerList(Set<String> brokerNames,
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
            enrichBrokerWithRuntimeInfo(builder, masterAddr);

            brokers.add(builder.build());
        }
        return brokers;
    }

    private void enrichBrokerWithRuntimeInfo(BrokerVO.BrokerVOBuilder builder, String brokerAddr) {
        try {
            KVTable runtimeInfo = adminExt.fetchBrokerRuntimeStats(brokerAddr);
            if (runtimeInfo == null || runtimeInfo.getTable() == null) {
                return;
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

            String getTransferredTps = table.get("getTransferedTps");
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
        } catch (Exception e) {
            log.debug("Failed to get runtime info for broker at {}: {}", brokerAddr, e.getMessage());
        }
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

    private List<NameServerVO> buildNameServerList() {
        List<NameServerVO> nameServers = new ArrayList<>();
        String namesrvAddr = properties.getNamesrvAddr();
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
