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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterService {

    private final ClusterRepository clusterRepository;
    private final ClusterProvider clusterProvider;

    public List<ClusterVO> listClusters() {
        log.info("Listing all clusters");
        return clusterRepository.findAll();
    }

    public ClusterVO getCluster(String id) {
        log.info("Getting cluster detail: {}", id);
        return clusterRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "Cluster not found: " + id));
    }

    public ClusterVO updateClusterConfig(UpdateConfigDTO command) {
        log.info("Updating cluster config for: {}", command.getId());
        ClusterVO cluster = clusterRepository.findById(command.getId())
                .orElseThrow(() -> new BusinessException(404, "Cluster not found: " + command.getId()));

        ClusterConfigVO config = copyConfig(cluster.getConfig());

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
        if (command.getWriteQueueNums() != null) {
            config.setWriteQueueNums(command.getWriteQueueNums());
        }
        if (command.getReadQueueNums() != null) {
            config.setReadQueueNums(command.getReadQueueNums());
        }
        if (command.getBrokerPermission() != null) {
            config.setBrokerPermission(command.getBrokerPermission());
        }

        clusterRepository.updateConfig(command.getId(), config);
        cluster.setConfig(config);
        log.info("Cluster config updated successfully for: {}", command.getId());
        return cluster;
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
        log.info("Creating NameServer for cluster: {}", command.getClusterId());
        clusterRepository.findById(command.getClusterId())
                .orElseThrow(() -> new BusinessException(404, "Cluster not found: " + command.getClusterId()));
        throw unsupportedOperation("NameServer create");
    }

    public void updateNameServer(UpdateNameServerDTO command) {
        log.info("Updating NameServer: {} in cluster: {}", command.getAddr(), command.getClusterId());
        ClusterVO cluster = clusterRepository.findById(command.getClusterId())
                .orElseThrow(() -> new BusinessException(404, "Cluster not found: " + command.getClusterId()));
        requireNameServer(cluster, command.getAddr());
        throw unsupportedOperation("NameServer update");
    }

    public boolean restartNameServer(RestartNameServerDTO command) {
        log.info("Restarting NameServer: {} in cluster: {}", command.getAddr(), command.getClusterId());
        ClusterVO cluster = clusterRepository.findById(command.getClusterId())
                .orElseThrow(() -> new BusinessException(404, "Cluster not found: " + command.getClusterId()));
        requireNameServer(cluster, command.getAddr());
        throw unsupportedOperation("NameServer restart");
    }

    public boolean upgradeNameServer(UpgradeNameServerDTO command) {
        log.info("Upgrading NameServer: {} to version: {} in cluster: {}",
                command.getAddr(), command.getTargetVersion(), command.getClusterId());
        ClusterVO cluster = clusterRepository.findById(command.getClusterId())
                .orElseThrow(() -> new BusinessException(404, "Cluster not found: " + command.getClusterId()));
        requireNameServer(cluster, command.getAddr());
        throw unsupportedOperation("NameServer upgrade");
    }

    public boolean deleteNameServer(DeleteNameServerDTO command) {
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
