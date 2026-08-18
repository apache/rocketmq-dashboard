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
package org.apache.rocketmq.studio.cluster.broker;

import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.cluster.config.ClusterConfigVO;
import org.apache.rocketmq.studio.cluster.nameserver.NameServerVO;
import org.apache.rocketmq.studio.common.domain.enums.BrokerStatus;
import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.apache.rocketmq.studio.common.domain.enums.ClusterType;
import org.apache.rocketmq.studio.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link ClusterProvider} backed by a live NameServer connection.
 *
 * <p>Replaces the former hard-coded stub: cluster topology is read on demand from the RocketMQ
 * NameServer through {@link MqAdminExtFactory#examineBrokerClusterInfo}. Discovery honours the
 * {@link MqAdminProperties#getNamesrvAddr() configured NameServer}; when none is configured no
 * clusters are returned and callers fall back to the interactive connection-test flow.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealClusterProvider implements ClusterProvider {

    private final MqAdminExtFactory adminFactory;
    private final MqAdminProperties properties;

    @Override
    public List<ClusterVO> discoverClusters() {
        String namesrvAddr = properties.getNamesrvAddr();
        if (namesrvAddr == null || namesrvAddr.isBlank()) {
            log.info("No NameServer configured; skipping cluster discovery");
            return List.of();
        }
        return describeClusters(namesrvAddr);
    }

    @Override
    public List<ClusterVO> discoverClustersAt(String namesrvAddr) {
        if (namesrvAddr == null || namesrvAddr.isBlank()) {
            return List.of();
        }
        return describeClusters(namesrvAddr);
    }

    @Override
    public ClusterVO refreshClusterDetail(String clusterId) {
        if (clusterId == null || clusterId.isBlank()) {
            throw new BusinessException(400, "Cluster ID is required");
        }
        String namesrvAddr = properties.getNamesrvAddr();
        if (namesrvAddr == null || namesrvAddr.isBlank()) {
            throw new BusinessException(400, "No NameServer configured for cluster " + clusterId);
        }
        return adminFactory.execute(namesrvAddr, null, admin -> toClusterVOs(namesrvAddr,
                        admin.examineBrokerClusterInfo()).stream()
                .filter(cluster -> clusterId.equals(cluster.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "Cluster not found: " + clusterId)));
    }

    /**
     * Connects to the given NameServer and maps its live topology into a {@link ClusterVO}.
     *
     * @param namesrvAddr NameServer address list, e.g. {@code host1:9876;host2:9876}
     * @return the cluster snapshot reported by the NameServer
     * @throws BusinessException if the NameServer is unreachable or returns an error
     */
    public ClusterVO describeCluster(String namesrvAddr) {
        return describeClusters(namesrvAddr).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "No cluster found from NameServer " + namesrvAddr));
    }

    /**
     * Returns one topology snapshot for every cluster reported by the configured NameServer.
     */
    public List<ClusterVO> describeClusters(String namesrvAddr) {
        return adminFactory.execute(namesrvAddr, null,
                admin -> toClusterVOs(namesrvAddr, admin.examineBrokerClusterInfo()));
    }

    private List<ClusterVO> toClusterVOs(String namesrvAddr, ClusterInfo clusterInfo) {
        if (clusterInfo == null) {
            return List.of();
        }
        Map<String, BrokerData> brokerAddrTable =
                clusterInfo.getBrokerAddrTable() == null ? Map.of() : clusterInfo.getBrokerAddrTable();
        Map<String, Set<String>> clusterAddrTable =
                clusterInfo.getClusterAddrTable() == null ? Map.of() : clusterInfo.getClusterAddrTable();

        if (clusterAddrTable.isEmpty()) {
            return List.of(toClusterVO(namesrvAddr, "DefaultCluster", brokerAddrTable.values()));
        }

        return clusterAddrTable.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> toClusterVO(namesrvAddr, entry.getKey(), entry.getValue().stream()
                        .map(brokerAddrTable::get)
                        .filter(java.util.Objects::nonNull)
                        .toList()))
                .toList();
    }

    private ClusterVO toClusterVO(String namesrvAddr, String clusterName, Collection<BrokerData> brokerData) {
        List<BrokerVO> brokers = brokerData.stream()
                .map(this::toBrokerVO)
                .sorted(Comparator.comparing(BrokerVO::getName,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<NameServerVO> nameServers = Arrays.stream(namesrvAddr.split("[;,]"))
                .map(String::trim)
                .filter(addr -> !addr.isEmpty())
                .map(addr -> NameServerVO.builder().addr(addr).status(ClusterStatus.healthy).build())
                .toList();

        // A live cluster has no proxy or config history until one is provisioned;
        // default to empty collections so the web UI never dereferences nulls.
        ClusterVO cluster = ClusterVO.builder()
                .name(clusterName)
                .nsClusterName(clusterName)
                .type(ClusterType.V4_DIRECT)
                .endpoint(namesrvAddr)
                .status(ClusterStatus.healthy)
                .brokers(brokers)
                .proxies(List.of())
                .nameServers(nameServers)
                .config(new ClusterConfigVO())
                .tpsHistory(List.of())
                .build();
        cluster.setId(clusterName);
        return cluster;
    }

    private BrokerVO toBrokerVO(BrokerData data) {
        String addr = null;
        if (data.getBrokerAddrs() != null) {
            addr = data.getBrokerAddrs().get(MixAll.MASTER_ID);
            if (addr == null) {
                addr = data.getBrokerAddrs().values().stream().findFirst().orElse(null);
            }
        }
        return BrokerVO.builder()
                .name(data.getBrokerName())
                .addr(addr)
                .status(BrokerStatus.running)
                .build();
    }
}
