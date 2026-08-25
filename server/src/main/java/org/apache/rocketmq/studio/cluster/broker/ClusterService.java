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

import org.apache.rocketmq.studio.cluster.config.BrokerConfigUpdateFailureVO;
import org.apache.rocketmq.studio.cluster.config.ClusterConfigPreviewVO;
import org.apache.rocketmq.studio.cluster.config.ClusterConfigUpdateResultVO;
import org.apache.rocketmq.studio.cluster.config.ClusterConfigVO;
import org.apache.rocketmq.studio.cluster.config.UpdateConfigDTO;
import org.apache.rocketmq.studio.cluster.nameserver.CreateNameServerDTO;
import org.apache.rocketmq.studio.cluster.nameserver.DeleteNameServerDTO;
import org.apache.rocketmq.studio.cluster.nameserver.NameServerVO;
import org.apache.rocketmq.studio.cluster.nameserver.NameserverRegistryService;
import org.apache.rocketmq.studio.cluster.nameserver.NameserverRegistryVO;
import org.apache.rocketmq.studio.cluster.nameserver.RestartNameServerDTO;
import org.apache.rocketmq.studio.cluster.nameserver.UpdateNameServerDTO;
import org.apache.rocketmq.studio.cluster.nameserver.UpgradeNameServerDTO;
import org.apache.rocketmq.studio.cluster.proxy.ProxyVO;
import org.apache.rocketmq.studio.cluster.proxy.RestartProxyDTO;

import org.apache.rocketmq.studio.common.domain.enums.FlushDiskType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.apache.rocketmq.studio.provider.apache.RocketMQBrokerConfigService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterService {

    private static final long REGISTRY_PROBE_TIMEOUT_SECONDS = 15;
    private static final int REGISTRY_PROBE_MAX_CONCURRENCY = 8;
    private static final int REGISTRY_PROBE_QUEUE_CAPACITY = 32;

    private final ClusterRepository clusterRepository;
    private final ClusterProvider clusterProvider;
    private final RocketMQBrokerConfigService brokerConfigService;
    private final AuditService auditService;
    private final NameserverRegistryService registryService;

    // Bounded so blocked probes cannot accumulate threads; replaceable in unit tests.
    private RegistryProbeRunner registryProbeRunner = new RegistryProbeRunner(
            REGISTRY_PROBE_MAX_CONCURRENCY, REGISTRY_PROBE_QUEUE_CAPACITY,
            REGISTRY_PROBE_TIMEOUT_SECONDS * 1000L);

    void setRegistryProbeRunner(RegistryProbeRunner registryProbeRunner) {
        this.registryProbeRunner = registryProbeRunner;
    }

    @PreDestroy
    void closeRegistryProbeRunner() {
        registryProbeRunner.close();
    }

    public List<ClusterVO> listClusters() {
        log.info("Listing all clusters");
        List<ClusterVO> discovered = clusterProvider.discoverClusters();
        if (discovered != null && !discovered.isEmpty()) {
            discovered.forEach(this::enrichWithLiveConfig);
            return discovered;
        }
        return List.of();
    }

    /**
     * Probes every nameserver address registered in rmq_nameserver concurrently and
     * aggregates the online clusters. A single unreachable or timed-out entry is
     * logged and skipped without affecting the other entries.
     */
    public List<ClusterVO> listRegistryClusters() {
        List<NameserverRegistryVO> probeable = registryService.list().stream()
                .filter(entry -> entry.getNamesrvAddr() != null && !entry.getNamesrvAddr().isBlank())
                .toList();
        if (probeable.isEmpty()) {
            return List.of();
        }
        return registryProbeRunner.probeAll(probeable, this::probeRegistryEntry);
    }

    private List<ClusterVO> probeRegistryEntry(NameserverRegistryVO entry) {
        try {
            List<ClusterVO> clusters = clusterProvider.discoverClustersAt(entry.getNamesrvAddr());
            for (ClusterVO cluster : clusters) {
                cluster.setNsClusterName(cluster.getName());
                cluster.setName(entry.getName());
                cluster.setEndpoint(entry.getNamesrvAddr());
            }
            return clusters;
        } catch (Exception e) {
            log.warn("Failed to probe NameServer registry entry {} ({}): {}",
                    entry.getName(), entry.getNamesrvAddr(), e.getMessage());
            return List.of();
        }
    }

    public List<ClusterVO> listClusters(String instanceId) {
        log.info("Listing clusters for instance: {}", instanceId);
        List<ClusterVO> discovered = clusterProvider.discoverClusters(instanceId);
        if (discovered != null && !discovered.isEmpty()) {
            discovered.forEach(cluster -> enrichWithLiveConfig(cluster, instanceId));
            return discovered;
        }
        return List.of();
    }

    public ClusterVO getCluster(String id) {
        log.info("Getting cluster detail: {}", id);
        ClusterVO live = clusterProvider.refreshClusterDetail(id);
        if (live != null) {
            enrichWithLiveConfig(live);
            return live;
        }
        throw new BusinessException(503, "Cluster details are unavailable: " + id);
    }

    public ClusterVO getCluster(String id, String instanceId) {
        log.info("Getting cluster detail: {}", id);
        ClusterVO live = clusterProvider.refreshClusterDetail(id, instanceId);
        if (live != null) {
            enrichWithLiveConfig(live, instanceId);
            return live;
        }
        throw new BusinessException(503, "Cluster details are unavailable: " + id);
    }

    public List<ProxyVO> listProxies(String clusterId) {
        ClusterVO cluster = resolveCluster(clusterId);
        if (cluster.getProxies() == null || cluster.getProxies().isEmpty()) {
            return List.of();
        }
        return List.copyOf(cluster.getProxies());
    }

    public void requireProxy(String clusterId, String addr) {
        ClusterVO cluster = resolveCluster(clusterId);
        requireProxy(cluster, addr);
    }

    public ClusterConfigPreviewVO previewClusterConfig(UpdateConfigDTO command) {
        log.info("Previewing cluster config update for: {}", command.getId());
        requireMatchingDefaultQueueNums(command);
        ClusterVO cluster = resolveCluster(command.getId(), command.getInstanceId());
        ClusterConfigVO currentConfig = copyConfig(cluster.getConfig());
        ClusterConfigVO proposedConfig = copyConfig(currentConfig);
        applyConfig(command, proposedConfig);
        List<ClusterConfigPreviewVO.ConfigChangeVO> changes =
                findConfigChanges(currentConfig, proposedConfig);
        return ClusterConfigPreviewVO.builder()
                .cluster(cluster)
                .currentConfig(currentConfig)
                .proposedConfig(proposedConfig)
                .targetBrokers(targetBrokers(cluster))
                .brokerProperties(buildBrokerPropertyMap(command))
                .changes(changes)
                .changed(!changes.isEmpty())
                .build();
    }

    /**
     * Attach live broker configuration (read from the first reachable master broker via the
     * admin API) to a discovered cluster. Falls back to the persisted config, if any, when the
     * live read is unavailable.
     */
    private void enrichWithLiveConfig(ClusterVO cluster) {
        enrichWithLiveConfig(cluster, null);
    }

    private void enrichWithLiveConfig(ClusterVO cluster, String instanceId) {
        if (cluster.getBrokers() != null) {
            for (BrokerVO broker : cluster.getBrokers()) {
                if (broker.getAddr() != null && !broker.getAddr().isEmpty()) {
                    try {
                        cluster.setConfig(brokerConfigService.getBrokerConfig(broker.getAddr(), instanceId));
                        return;
                    } catch (Exception e) {
                        log.warn("Failed to read live config from broker {}: {}",
                                broker.getAddr(), e.getMessage());
                    }
                }
            }
        }
        if (cluster.getConfig() == null && cluster.getId() != null) {
            clusterRepository.findById(cluster.getId()).ifPresent(stored -> cluster.setConfig(stored.getConfig()));
        }
    }

    public ClusterConfigUpdateResultVO updateClusterConfig(UpdateConfigDTO command) {
        log.info("Updating cluster config for: {}", command.getId());
        requireMatchingDefaultQueueNums(command);
        ClusterVO cluster = resolveCluster(command.getId(), command.getInstanceId());

        ClusterConfigVO config = copyConfig(cluster.getConfig());
        applyConfig(command, config);

        List<String> successfulBrokers = new ArrayList<>();
        List<BrokerConfigUpdateFailureVO> failedBrokers = new ArrayList<>();
        if (cluster.getBrokers() != null && !cluster.getBrokers().isEmpty()) {
            Properties brokerProps = buildBrokerProperties(command);
            for (BrokerVO broker : cluster.getBrokers()) {
                String address = broker.getAddr();
                if (address == null || address.isEmpty()) {
                    continue;
                }
                try {
                    if (command.getInstanceId() == null || command.getInstanceId().isBlank()) {
                        brokerConfigService.updateBrokerConfig(address, command.getId(), brokerProps);
                    } else {
                        brokerConfigService.updateBrokerConfig(
                                address, command.getId(), command.getInstanceId(), brokerProps);
                    }
                    successfulBrokers.add(address);
                } catch (Exception e) {
                    failedBrokers.add(BrokerConfigUpdateFailureVO.builder()
                            .address(address)
                            .message(e.getMessage())
                            .build());
                }
            }
        }
        if (successfulBrokers.isEmpty() && failedBrokers.isEmpty()) {
            failedBrokers.add(BrokerConfigUpdateFailureVO.builder()
                    .address("N/A")
                    .message("No broker address is available for configuration update")
                    .build());
        }

        ClusterConfigUpdateResultVO.Status status = updateStatus(successfulBrokers, failedBrokers);
        if (failedBrokers.isEmpty()) {
            clusterRepository.updateConfig(command.getId(), config);
            cluster.setConfig(config);
        }
        recordConfigUpdateAudit(command.getId(), status, successfulBrokers, failedBrokers);
        log.info("Cluster config update finished for {} with status {}", command.getId(), status);
        return ClusterConfigUpdateResultVO.builder()
                .cluster(cluster)
                .status(status)
                .successfulBrokers(List.copyOf(successfulBrokers))
                .failedBrokers(List.copyOf(failedBrokers))
                .build();
    }

    private void applyConfig(UpdateConfigDTO command, ClusterConfigVO config) {
        if (command.getFlushDiskType() != null) {
            config.setFlushDiskType(parseFlushDiskType(command.getFlushDiskType()));
        }
        if (command.getAutoCreateTopicEnable() != null) {
            config.setAutoCreateTopicEnable(command.getAutoCreateTopicEnable());
        }
        if (command.getAutoCreateSubscriptionGroup() != null) {
            config.setAutoCreateSubscriptionGroup(command.getAutoCreateSubscriptionGroup());
        }
        if (command.getMaxMessageSize() != null) {
            config.setMaxMessageSize(command.getMaxMessageSize());
        }
        if (command.getFileReservedTime() != null) {
            config.setFileReservedTime(command.getFileReservedTime());
        }
        // The broker exposes a single defaultTopicQueueNums property, so a partial update of only
        // one of the write/read queues must mirror the value onto the other to keep the stored
        // config consistent with what the broker actually applies.
        if (command.getWriteQueueNums() != null || command.getReadQueueNums() != null) {
            int queueNums = command.getWriteQueueNums() != null
                    ? command.getWriteQueueNums() : command.getReadQueueNums();
            config.setWriteQueueNums(queueNums);
            config.setReadQueueNums(queueNums);
        }
        if (command.getBrokerPermission() != null) {
            config.setBrokerPermission(command.getBrokerPermission());
        }
    }

    private ClusterConfigUpdateResultVO.Status updateStatus(
            List<String> successfulBrokers,
            List<BrokerConfigUpdateFailureVO> failedBrokers) {
        if (failedBrokers.isEmpty()) {
            return ClusterConfigUpdateResultVO.Status.SUCCESS;
        }
        if (successfulBrokers.isEmpty()) {
            return ClusterConfigUpdateResultVO.Status.FAILED;
        }
        return ClusterConfigUpdateResultVO.Status.PARTIAL;
    }

    private void recordConfigUpdateAudit(
            String clusterId,
            ClusterConfigUpdateResultVO.Status status,
            List<String> successfulBrokers,
            List<BrokerConfigUpdateFailureVO> failedBrokers) {
        String detail = "successfulBrokers=" + successfulBrokers
                + ", failedBrokers=" + failedBrokers.stream()
                .map(failure -> failure.getAddress() + ": " + failure.getMessage())
                .toList();
        try {
            auditService.record("UPDATE_CLUSTER_CONFIG", "CLUSTER", "CLUSTER:" + clusterId,
                    clusterId, detail, status.name());
        } catch (Exception e) {
            log.warn("Failed to record cluster config update audit for {}: {}", clusterId, e.getMessage());
        }
    }

    private ClusterVO resolveCluster(String clusterId) {
        ClusterVO live = clusterProvider.refreshClusterDetail(clusterId);
        if (live != null) {
            enrichWithLiveConfig(live);
            return live;
        }
        return clusterRepository.findById(clusterId)
                .orElseThrow(() -> new BusinessException(404, "Cluster not found: " + clusterId));
    }

    private ClusterVO resolveCluster(String clusterId, String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return resolveCluster(clusterId);
        }
        ClusterVO live = clusterProvider.refreshClusterDetail(clusterId, instanceId);
        if (live != null) {
            enrichWithLiveConfig(live, instanceId);
            return live;
        }
        return clusterRepository.findById(clusterId)
                .orElseThrow(() -> new BusinessException(404, "Cluster not found: " + clusterId));
    }

    private ClusterConfigVO copyConfig(ClusterConfigVO config) {
        if (config == null) {
            return new ClusterConfigVO();
        }
        return ClusterConfigVO.builder()
                .writeQueueNums(config.getWriteQueueNums())
                .readQueueNums(config.getReadQueueNums())
                .maxMessageSize(config.getMaxMessageSize())
                .msgTraceTopicName(config.getMsgTraceTopicName())
                .autoCreateTopicEnable(config.isAutoCreateTopicEnable())
                .autoCreateSubscriptionGroup(config.isAutoCreateSubscriptionGroup())
                .deleteWhen(config.getDeleteWhen())
                .fileReservedTime(config.getFileReservedTime())
                .flushDiskType(config.getFlushDiskType())
                .brokerPermission(config.getBrokerPermission())
                .build();
    }

    private Properties buildBrokerProperties(UpdateConfigDTO command) {
        Properties props = new Properties();
        buildBrokerPropertyMap(command).forEach(props::setProperty);
        return props;
    }

    private Map<String, String> buildBrokerPropertyMap(UpdateConfigDTO command) {
        Map<String, String> props = new LinkedHashMap<>();
        if (command.getFlushDiskType() != null) {
            props.put("flushDiskType", command.getFlushDiskType());
        }
        if (command.getAutoCreateTopicEnable() != null) {
            props.put("autoCreateTopicEnable", command.getAutoCreateTopicEnable().toString());
        }
        if (command.getAutoCreateSubscriptionGroup() != null) {
            props.put("autoCreateSubscriptionGroup", command.getAutoCreateSubscriptionGroup().toString());
        }
        if (command.getMaxMessageSize() != null) {
            props.put("maxMessageSize", command.getMaxMessageSize().toString());
        }
        if (command.getFileReservedTime() != null) {
            props.put("fileReservedTime", command.getFileReservedTime().toString());
        }
        if (command.getWriteQueueNums() != null) {
            props.put("defaultTopicQueueNums", command.getWriteQueueNums().toString());
        }
        if (command.getReadQueueNums() != null) {
            props.put("defaultTopicQueueNums", command.getReadQueueNums().toString());
        }
        if (command.getBrokerPermission() != null) {
            props.put("brokerPermission", command.getBrokerPermission().toString());
        }
        return props;
    }

    private List<ClusterConfigPreviewVO.BrokerTargetVO> targetBrokers(ClusterVO cluster) {
        if (cluster.getBrokers() == null) {
            return List.of();
        }
        return cluster.getBrokers().stream()
                .filter(broker -> broker.getAddr() != null && !broker.getAddr().isBlank())
                .map(broker -> ClusterConfigPreviewVO.BrokerTargetVO.builder()
                        .name(broker.getName())
                        .address(broker.getAddr())
                        .build())
                .toList();
    }

    private List<ClusterConfigPreviewVO.ConfigChangeVO> findConfigChanges(
            ClusterConfigVO current,
            ClusterConfigVO proposed) {
        List<ClusterConfigPreviewVO.ConfigChangeVO> changes = new ArrayList<>();
        addConfigChange(changes, "flushDiskType", "flushDiskType",
                current.getFlushDiskType(), proposed.getFlushDiskType());
        addConfigChange(changes, "autoCreateTopicEnable", "autoCreateTopicEnable",
                current.isAutoCreateTopicEnable(), proposed.isAutoCreateTopicEnable());
        addConfigChange(changes, "autoCreateSubscriptionGroup", "autoCreateSubscriptionGroup",
                current.isAutoCreateSubscriptionGroup(), proposed.isAutoCreateSubscriptionGroup());
        addConfigChange(changes, "maxMessageSize", "maxMessageSize",
                current.getMaxMessageSize(), proposed.getMaxMessageSize());
        addConfigChange(changes, "fileReservedTime", "fileReservedTime",
                current.getFileReservedTime(), proposed.getFileReservedTime());
        addConfigChange(changes, "writeQueueNums", "defaultTopicQueueNums",
                current.getWriteQueueNums(), proposed.getWriteQueueNums());
        addConfigChange(changes, "readQueueNums", "defaultTopicQueueNums",
                current.getReadQueueNums(), proposed.getReadQueueNums());
        addConfigChange(changes, "brokerPermission", "brokerPermission",
                current.getBrokerPermission(), proposed.getBrokerPermission());
        return changes;
    }

    private void addConfigChange(
            List<ClusterConfigPreviewVO.ConfigChangeVO> changes,
            String field,
            String brokerProperty,
            Object currentValue,
            Object proposedValue) {
        if (Objects.equals(currentValue, proposedValue)) {
            return;
        }
        changes.add(ClusterConfigPreviewVO.ConfigChangeVO.builder()
                .field(field)
                .currentValue(previewValue(currentValue))
                .proposedValue(previewValue(proposedValue))
                .brokerProperty(brokerProperty)
                .build());
    }

    private String previewValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private void requireMatchingDefaultQueueNums(UpdateConfigDTO command) {
        if (command.getWriteQueueNums() != null && command.getReadQueueNums() != null
                && !command.getWriteQueueNums().equals(command.getReadQueueNums())) {
            throw new BusinessException(400,
                    "RocketMQ broker default queue count requires matching writeQueueNums and readQueueNums");
        }
    }

    private FlushDiskType parseFlushDiskType(String value) {
        try {
            return FlushDiskType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(400, "Invalid flushDiskType: " + value);
        }
    }

    public boolean restartBroker(String clusterId, String brokerName) {
        log.info("Restarting broker: {} in cluster: {}", brokerName, clusterId);
        ClusterVO cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new BusinessException(404, "Cluster not found: " + clusterId));
        if (cluster.getBrokers() == null || cluster.getBrokers().stream()
                .noneMatch(broker -> brokerName.equals(broker.getName()))) {
            throw new BusinessException(404, "Broker not found: " + brokerName);
        }
        throw unsupportedOperation("Broker restart");
    }

    public NameServerVO createNameServer(CreateNameServerDTO command) {
        requireNameServerCommand(command);
        log.info("Creating NameServer for cluster: {}", command.getClusterId());
        clusterRepository.findById(command.getClusterId())
                .orElseThrow(() -> new BusinessException(404, "Cluster not found: " + command.getClusterId()));
        throw unsupportedOperation("NameServer create");
    }

    public void updateNameServer(UpdateNameServerDTO command) {
        requireNameServerCommand(command);
        log.info("Updating NameServer: {} in cluster: {}", command.getAddr(), command.getClusterId());
        ClusterVO cluster = clusterRepository.findById(command.getClusterId())
                .orElseThrow(() -> new BusinessException(404, "Cluster not found: " + command.getClusterId()));
        requireNameServer(cluster, command.getAddr());
        throw unsupportedOperation("NameServer update");
    }

    public boolean restartNameServer(RestartNameServerDTO command) {
        requireNameServerCommand(command);
        log.info("Restarting NameServer: {} in cluster: {}", command.getAddr(), command.getClusterId());
        ClusterVO cluster = clusterRepository.findById(command.getClusterId())
                .orElseThrow(() -> new BusinessException(404, "Cluster not found: " + command.getClusterId()));
        requireNameServer(cluster, command.getAddr());
        throw unsupportedOperation("NameServer restart");
    }

    public boolean upgradeNameServer(UpgradeNameServerDTO command) {
        requireNameServerCommand(command);
        log.info("Upgrading NameServer: {} to version: {} in cluster: {}",
                command.getAddr(), command.getTargetVersion(), command.getClusterId());
        ClusterVO cluster = clusterRepository.findById(command.getClusterId())
                .orElseThrow(() -> new BusinessException(404, "Cluster not found: " + command.getClusterId()));
        requireNameServer(cluster, command.getAddr());
        throw unsupportedOperation("NameServer upgrade");
    }

    public boolean deleteNameServer(DeleteNameServerDTO command) {
        requireNameServerCommand(command);
        log.info("Deleting NameServer: {} from cluster: {}", command.getAddr(), command.getClusterId());
        ClusterVO cluster = clusterRepository.findById(command.getClusterId())
                .orElseThrow(() -> new BusinessException(404, "Cluster not found: " + command.getClusterId()));
        requireNameServer(cluster, command.getAddr());
        throw unsupportedOperation("NameServer delete");
    }

    public boolean restartProxy(RestartProxyDTO command) {
        log.info("Restarting Proxy: {} in cluster: {}", command.getAddr(), command.getClusterId());
        ClusterVO cluster = clusterRepository.findById(command.getClusterId())
                .orElseThrow(() -> new BusinessException(404, "Cluster not found: " + command.getClusterId()));
        requireProxy(cluster, command.getAddr());
        throw unsupportedOperation("Proxy restart");
    }

    private void requireNameServerCommand(Object command) {
        if (command == null) {
            throw new BusinessException(400, "NameServer request is required");
        }
    }

    private void requireNameServer(ClusterVO cluster, String addr) {
        if (cluster.getNameServers() == null || cluster.getNameServers().stream()
                .noneMatch(nameServer -> addr.equals(nameServer.getAddr()))) {
            throw new BusinessException(404, "NameServer not found: " + addr);
        }
    }

    private void requireProxy(ClusterVO cluster, String addr) {
        if (cluster.getProxies() == null || cluster.getProxies().stream()
                .noneMatch(proxy -> addr.equals(proxy.getAddr()))) {
            throw new BusinessException(404, "Proxy not found: " + addr);
        }
    }

    private BusinessException unsupportedOperation(String operation) {
        return new BusinessException(501, operation + " is not implemented by the current cluster provider");
    }
}
