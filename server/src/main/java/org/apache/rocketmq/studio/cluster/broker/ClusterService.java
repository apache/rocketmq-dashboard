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
import org.apache.rocketmq.studio.cluster.config.ClusterConfigUpdateResultVO;
import org.apache.rocketmq.studio.cluster.config.ClusterConfigVO;
import org.apache.rocketmq.studio.cluster.config.UpdateConfigDTO;
import org.apache.rocketmq.studio.cluster.nameserver.CreateNameServerDTO;
import org.apache.rocketmq.studio.cluster.nameserver.DeleteNameServerDTO;
import org.apache.rocketmq.studio.cluster.nameserver.NameServerVO;
import org.apache.rocketmq.studio.cluster.nameserver.RestartNameServerDTO;
import org.apache.rocketmq.studio.cluster.nameserver.UpdateNameServerDTO;
import org.apache.rocketmq.studio.cluster.nameserver.UpgradeNameServerDTO;
import org.apache.rocketmq.studio.cluster.proxy.RestartProxyDTO;

import org.apache.rocketmq.studio.common.domain.enums.FlushDiskType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.apache.rocketmq.studio.provider.apache.RocketMQBrokerConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterService {

    private final ClusterRepository clusterRepository;
    private final ClusterProvider clusterProvider;
    private final RocketMQBrokerConfigService brokerConfigService;
    private final AuditService auditService;

    public List<ClusterVO> listClusters() {
        log.info("Listing all clusters");
        List<ClusterVO> discovered = clusterProvider.discoverClusters();
        if (discovered != null && !discovered.isEmpty()) {
            discovered.forEach(this::enrichWithLiveConfig);
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

    /**
     * Attach live broker configuration (read from the first reachable master broker via the
     * admin API) to a discovered cluster. Falls back to the persisted config, if any, when the
     * live read is unavailable.
     */
    private void enrichWithLiveConfig(ClusterVO cluster) {
        if (cluster.getBrokers() != null) {
            for (BrokerVO broker : cluster.getBrokers()) {
                if (broker.getAddr() != null && !broker.getAddr().isEmpty()) {
                    try {
                        cluster.setConfig(brokerConfigService.getBrokerConfig(broker.getAddr()));
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
        ClusterVO cluster = resolveCluster(command.getId());

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
                    brokerConfigService.updateBrokerConfig(address, command.getId(), brokerProps);
                    successfulBrokers.add(address);
                } catch (Exception e) {
                    failedBrokers.add(BrokerConfigUpdateFailureVO.builder()
                            .address(address)
                            .message(e.getMessage())
                            .build());
                }
            }
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
            auditService.record("UPDATE_CLUSTER_CONFIG", "CLUSTER:" + clusterId, detail, status.name());
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
        if (command.getFlushDiskType() != null) {
            props.setProperty("flushDiskType", command.getFlushDiskType());
        }
        if (command.getAutoCreateTopicEnable() != null) {
            props.setProperty("autoCreateTopicEnable", command.getAutoCreateTopicEnable().toString());
        }
        if (command.getAutoCreateSubscriptionGroup() != null) {
            props.setProperty("autoCreateSubscriptionGroup", command.getAutoCreateSubscriptionGroup().toString());
        }
        if (command.getMaxMessageSize() != null) {
            props.setProperty("maxMessageSize", command.getMaxMessageSize().toString());
        }
        if (command.getFileReservedTime() != null) {
            props.setProperty("fileReservedTime", command.getFileReservedTime().toString());
        }
        if (command.getWriteQueueNums() != null) {
            props.setProperty("defaultTopicQueueNums", command.getWriteQueueNums().toString());
        }
        if (command.getReadQueueNums() != null) {
            props.setProperty("defaultTopicQueueNums", command.getReadQueueNums().toString());
        }
        if (command.getBrokerPermission() != null) {
            props.setProperty("brokerPermission", command.getBrokerPermission().toString());
        }
        return props;
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
