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
package org.apache.rocketmq.studio.ops.ai.tool.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Reverse lookup from a physical RocketMQ cluster name to the Studio instances that can
 * manage it. Platform-level infrastructure tools (nameserver/broker/proxy/cluster) carry
 * {@code clusterName} instead of {@code instanceId}; this resolver scans every Apache
 * instance through the pooled admin client and reports which physical clusters each one
 * exposes. Scans are performed per call (no caching); only the underlying admin clients
 * are pooled by {@link RuntimeAdminClientResolver}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformClusterResolver {

    private final InstanceRepository instanceRepository;
    private final RuntimeAdminClientResolver runtimeAdminClientResolver;

    public record ManagedBroker(
            String brokerName,
            Long brokerId,
            String address,
            boolean master,
            String version) {
    }

    public record ManagedCluster(
            String clusterName,
            String instanceId,
            List<String> nameServerAddrs,
            List<ManagedBroker> brokers) {

        /** healthy when every master replica answered the runtime-stats probe. */
        public ClusterStatus status() {
            List<ManagedBroker> masters = brokers.stream()
                    .filter(ManagedBroker::master)
                    .toList();
            boolean complete = !masters.isEmpty()
                    && masters.stream().allMatch(broker -> broker.version() != null);
            return complete ? ClusterStatus.healthy : ClusterStatus.warning;
        }

        public Set<String> brokerNames() {
            return brokers.stream()
                    .map(ManagedBroker::brokerName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    /** Apache instances with a usable endpoint, ordered by name for deterministic resolution. */
    public List<InstanceVO> manageableInstances() {
        return instanceRepository.findAll().stream()
                .filter(Objects::nonNull)
                .filter(instance -> instance.getVendor() == null || instance.getVendor() == InstanceVendor.APACHE)
                .filter(instance -> StringUtils.hasText(instance.getName()))
                .filter(instance -> StringUtils.hasText(instance.getEndpoint()))
                .sorted(Comparator.comparing(InstanceVO::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Physical clusters reachable from every manageable instance, deduplicated by cluster name. */
    public List<ManagedCluster> scan() {
        return collect(false);
    }

    /** Same as {@link #scan()} plus a best-effort broker version read for cluster overviews. */
    public List<ManagedCluster> scanWithBrokerVersions() {
        return collect(true);
    }

    /** First instance that manages the given physical cluster; 404 when no instance owns it. */
    public ManagedCluster require(String clusterName) {
        if (!StringUtils.hasText(clusterName)) {
            throw new BusinessException(400, "clusterName is required");
        }
        String normalized = clusterName.trim();
        return scan().stream()
                .filter(cluster -> normalized.equals(cluster.clusterName()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "Cluster not found: " + normalized));
    }

    public String resolveInstanceId(String clusterName) {
        return require(clusterName).instanceId();
    }

    public static List<String> splitEndpoints(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return List.of();
        }
        return Arrays.stream(endpoint.split("[;,]"))
                .map(String::trim)
                .filter(address -> !address.isEmpty())
                .distinct()
                .toList();
    }

    private List<ManagedCluster> collect(boolean withVersions) {
        Map<String, ManagedCluster> unique = new LinkedHashMap<>();
        for (InstanceVO instance : manageableInstances()) {
            try {
                runtimeAdminClientResolver.execute(instance, admin -> inspect(instance, admin, withVersions))
                        .forEach(cluster -> unique.putIfAbsent(cluster.clusterName(), cluster));
            } catch (Exception e) {
                log.warn("Skipping instance {} during platform cluster scan: {}",
                        instance.getName(), e.getMessage());
            }
        }
        return List.copyOf(unique.values());
    }

    private List<ManagedCluster> inspect(
            InstanceVO instance, MQAdminExt admin, boolean withVersions) throws Exception {
        ClusterInfo info = admin.examineBrokerClusterInfo();
        if (info == null || info.getClusterAddrTable() == null) {
            return List.of();
        }
        Map<String, BrokerData> brokerAddrTable =
                info.getBrokerAddrTable() == null ? Map.of() : info.getBrokerAddrTable();
        List<String> nameServerAddrs = splitEndpoints(instance.getEndpoint());
        Map<String, String> versionsByAddress = new HashMap<>();
        List<ManagedCluster> clusters = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : new TreeMap<>(info.getClusterAddrTable()).entrySet()) {
            if (!StringUtils.hasText(entry.getKey())) {
                continue;
            }
            List<ManagedBroker> brokers = new ArrayList<>();
            TreeSet<String> brokerNames = entry.getValue() == null
                    ? new TreeSet<>() : new TreeSet<>(entry.getValue());
            for (String brokerName : brokerNames) {
                BrokerData data = brokerAddrTable.get(brokerName);
                if (data == null || data.getBrokerAddrs() == null) {
                    continue;
                }
                for (Map.Entry<Long, String> replica : new TreeMap<>(data.getBrokerAddrs()).entrySet()) {
                    if (!StringUtils.hasText(replica.getValue())) {
                        continue;
                    }
                    boolean master = replica.getKey() != null && replica.getKey() == MixAll.MASTER_ID;
                    String address = replica.getValue().trim();
                    String version = null;
                    if (withVersions && master) {
                        if (!versionsByAddress.containsKey(address)) {
                            versionsByAddress.put(address, fetchVersion(admin, address));
                        }
                        version = versionsByAddress.get(address);
                    }
                    brokers.add(new ManagedBroker(brokerName, replica.getKey(), address, master, version));
                }
            }
            clusters.add(new ManagedCluster(entry.getKey().trim(), instance.getName(),
                    nameServerAddrs, List.copyOf(brokers)));
        }
        return clusters;
    }

    private String fetchVersion(MQAdminExt admin, String brokerAddr) {
        try {
            KVTable runtimeInfo = admin.fetchBrokerRuntimeStats(brokerAddr);
            if (runtimeInfo == null || runtimeInfo.getTable() == null) {
                return null;
            }
            Map<String, String> table = runtimeInfo.getTable();
            String version = table.getOrDefault("brokerVersionDesc",
                    table.getOrDefault("rocketmqVersion", ""));
            return StringUtils.hasText(version) ? version : null;
        } catch (Exception e) {
            log.warn("Failed to read runtime version from broker {}: {}", brokerAddr, e.getMessage());
            return null;
        }
    }
}
