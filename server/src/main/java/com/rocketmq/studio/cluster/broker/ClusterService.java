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
package com.rocketmq.studio.cluster.broker;

import com.rocketmq.studio.cluster.config.ClusterConfigVO;
import com.rocketmq.studio.cluster.config.UpdateConfigDTO;
import com.rocketmq.studio.cluster.nameserver.CreateNameServerDTO;
import com.rocketmq.studio.cluster.nameserver.DeleteNameServerDTO;
import com.rocketmq.studio.cluster.nameserver.NameServerVO;
import com.rocketmq.studio.cluster.nameserver.RestartNameServerDTO;
import com.rocketmq.studio.cluster.nameserver.UpdateNameServerDTO;
import com.rocketmq.studio.cluster.nameserver.UpgradeNameServerDTO;
import com.rocketmq.studio.cluster.proxy.RestartProxyDTO;

import com.rocketmq.studio.common.domain.enums.ClusterStatus;
import com.rocketmq.studio.common.domain.enums.FlushDiskType;
import com.rocketmq.studio.common.exception.BusinessException;
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

        ClusterConfigVO config = cluster.getConfig();
        if (config == null) {
            config = new ClusterConfigVO();
        }

        if (command.getFlushDiskType() != null) {
            config.setFlushDiskType(FlushDiskType.valueOf(command.getFlushDiskType()));
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

        cluster.setConfig(config);
        clusterRepository.updateConfig(command.getId(), config);
        log.info("Cluster config updated successfully for: {}", command.getId());
        return cluster;
    }

    public boolean restartBroker(String clusterId, String brokerName) {
        log.info("Restarting broker: {} in cluster: {}", brokerName, clusterId);
        clusterRepository.findById(clusterId)
                .orElseThrow(() -> new BusinessException(404, "Cluster not found: " + clusterId));
        log.info("Broker restart initiated for: {} in cluster: {}", brokerName, clusterId);
        return true;
    }

    public NameServerVO createNameServer(CreateNameServerDTO command) {
        log.info("Creating NameServer for cluster: {}", command.getClusterId());
        clusterRepository.findById(command.getClusterId())
                .orElseThrow(() -> new BusinessException(404, "Cluster not found: " + command.getClusterId()));
        NameServerVO ns = NameServerVO.builder()
                .addr(command.getAddr())
                .status(ClusterStatus.healthy)
                .build();
        log.info("NameServer created: {}", command.getAddr());
        return ns;
    }

    public void updateNameServer(UpdateNameServerDTO command) {
        log.info("Updating NameServer: {} in cluster: {}", command.getAddr(), command.getClusterId());
        clusterRepository.findById(command.getClusterId())
                .orElseThrow(() -> new BusinessException(404, "Cluster not found: " + command.getClusterId()));
        log.info("NameServer updated: {}", command.getAddr());
    }

    public boolean restartNameServer(RestartNameServerDTO command) {
        log.info("Restarting NameServer: {} in cluster: {}", command.getAddr(), command.getClusterId());
        clusterRepository.findById(command.getClusterId())
                .orElseThrow(() -> new BusinessException(404, "Cluster not found: " + command.getClusterId()));
        log.info("NameServer restart initiated: {}", command.getAddr());
        return true;
    }

    public boolean upgradeNameServer(UpgradeNameServerDTO command) {
        log.info("Upgrading NameServer: {} to version: {} in cluster: {}",
                command.getAddr(), command.getTargetVersion(), command.getClusterId());
        clusterRepository.findById(command.getClusterId())
                .orElseThrow(() -> new BusinessException(404, "Cluster not found: " + command.getClusterId()));
        log.info("NameServer upgrade initiated: {}", command.getAddr());
        return true;
    }

    public boolean deleteNameServer(DeleteNameServerDTO command) {
        log.info("Deleting NameServer: {} from cluster: {}", command.getAddr(), command.getClusterId());
        clusterRepository.findById(command.getClusterId())
                .orElseThrow(() -> new BusinessException(404, "Cluster not found: " + command.getClusterId()));
        log.info("NameServer deleted: {}", command.getAddr());
        return true;
    }

    public boolean restartProxy(RestartProxyDTO command) {
        log.info("Restarting Proxy: {} in cluster: {}", command.getAddr(), command.getClusterId());
        clusterRepository.findById(command.getClusterId())
                .orElseThrow(() -> new BusinessException(404, "Cluster not found: " + command.getClusterId()));
        log.info("Proxy restart initiated: {}", command.getAddr());
        return true;
    }
}
