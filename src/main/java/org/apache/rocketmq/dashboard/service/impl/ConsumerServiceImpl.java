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
package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.dashboard.architecture.ClusterProvider;
import org.apache.rocketmq.dashboard.architecture.MetadataProvider;
import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.model.ConsumerGroupInfo;
import org.apache.rocketmq.dashboard.model.GroupConsumeInfo;
import org.apache.rocketmq.dashboard.model.SubscriptionInfo;
import org.apache.rocketmq.dashboard.model.request.ConsumerConfigInfo;
import org.apache.rocketmq.dashboard.service.ArchitectureBasedService;
import org.apache.rocketmq.dashboard.service.ConsumerService;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.heartbeat.ConsumeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;

/**
 * Consumer service implementation based on new architecture
 * Complete migration of v2.1.0 consumer group management capabilities
 */
@Service
public class ConsumerServiceImpl extends ArchitectureBasedService implements ConsumerService {

    private static final Logger log = LoggerFactory.getLogger(ConsumerServiceImpl.class);

    @Resource
    private MetadataProvider metadataProvider;

    @Resource
    private ClusterProvider clusterProvider;

    @Override
    public List<ConsumerGroupInfo> listConsumerGroups() {
        try {
            return metadataProvider.listConsumerGroups(Optional.empty());
        } catch (Exception e) {
            handleUnsupportedOperation("List consumer groups");
            return List.of();
        }
    }

    @Override
    public List<ConsumerGroupInfo> getConsumerGroupsByCluster(String clusterName) {
        try {
            // Filter consumer groups by cluster name from the full list
            return metadataProvider.listConsumerGroups(Optional.empty()).stream()
                .filter(g -> clusterName == null || clusterName.equals(g.getClusterName()))
                .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            handleUnsupportedOperation("Get consumer groups by cluster");
            return List.of();
        }
    }

    @Override
    public ConsumerGroupInfo getConsumerGroup(String groupName) {
        try {
            return metadataProvider.getConsumerGroup(groupName, Optional.empty())
                .orElse(null);
        } catch (Exception e) {
            handleUnsupportedOperation("Get consumer group");
            return null;
        }
    }

    @Override
    public boolean createConsumerGroup(ConsumerGroupInfo consumerGroup) {
        try {
            if (consumerGroup.getConsumerGroupName() == null || consumerGroup.getConsumerGroupName().trim().isEmpty()) {
                throw new IllegalArgumentException("Consumer group name cannot be empty");
            }
            metadataProvider.createConsumerGroup(consumerGroup);
            return true;
        } catch (Exception e) {
            handleUnsupportedOperation("Create consumer group");
            return false;
        }
    }

    @Override
    public boolean deleteConsumerGroup(String groupName) {
        try {
            metadataProvider.deleteConsumerGroup(groupName, Optional.empty());
            return true;
        } catch (Exception e) {
            handleUnsupportedOperation("Delete consumer group");
            return false;
        }
    }

    @Override
    public List<SubscriptionInfo> getSubscriptions(String groupName) {
        try {
            return metadataProvider.listSubscriptions(groupName);
        } catch (Exception e) {
            handleUnsupportedOperation("Get subscriptions");
            return List.of();
        }
    }

    @Override
    public boolean updateConsumerGroup(ConsumerGroupInfo consumerGroup) {
        try {
            metadataProvider.updateConsumerGroup(consumerGroup);
            return true;
        } catch (Exception e) {
            handleUnsupportedOperation("Update consumer group");
            return false;
        }
    }

    @Override
    public boolean resetConsumerGroupOffset(String groupName, String topic, long timestamp) {
        try {
            if (supports("CONSUMER_OFFSET_RESET")) {
                metadataProvider.resetConsumerGroupOffset(groupName, topic, timestamp);
                return true;
            }
            handleUnsupportedOperation("Reset consumer group offset - not supported in current cluster");
            return false;
        } catch (Exception e) {
            handleUnsupportedOperation("Reset consumer group offset");
            return false;
        }
    }

    @Override
    public ClusterCapability getClusterCapability() {
        return clusterCapability;
    }

    @Override
    public GroupConsumeInfo queryGroup(String groupName, String namespace) {
        try {
            GroupConsumeInfo info = adminClient.getGroupConsumeInfo(groupName);
            // Fix #380: Pop consumers may show NOT_CONSUME_YET (diffTotal == -1) as normal behavior.
            // Detect Pop consumers and clarify that this is expected, not an error.
            if (isPopConsumerNotConsumeYet(groupName)) {
                info.setSubGroupType("WAITING (Pop)");
            }
            return info;
        } catch (Exception e) {
            handleUnsupportedOperation("Query group consume info for " + groupName);
            return new GroupConsumeInfo();
        }
    }

    @Override
    public Object createAndUpdateSubscriptionGroupConfig(ConsumerConfigInfo consumerConfigInfo) {
        if (consumerConfigInfo == null || consumerConfigInfo.getSubscriptionGroupConfig() == null) {
            throw new IllegalArgumentException("ConsumerConfigInfo and subscriptionGroupConfig cannot be null");
        }
        ConsumerGroupInfo groupInfo = new ConsumerGroupInfo();
        groupInfo.setConsumerGroupName(consumerConfigInfo.getSubscriptionGroupConfig().getGroupName());
        try {
            metadataProvider.createConsumerGroup(groupInfo);
            return true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create/update consumer group: "
                + consumerConfigInfo.getSubscriptionGroupConfig().getGroupName(), e);
        }
    }

    /**
     * Fix #380: Check if a consumer group uses Pop consumption mode and has no pending messages.
     * Pop consumers do not maintain traditional consume offsets, so diffTotal may remain -1.
     * This is normal Pop consumer behavior, not an error condition.
     *
     * @param consumerGroup the consumer group name to check
     * @return true if the group is a Pop consumer with no pending messages
     */
    private boolean isPopConsumerNotConsumeYet(String consumerGroup) {
        try {
            ConsumerConnection connection = adminClient.getConsumerConnection(consumerGroup);
            if (connection != null) {
                ConsumeType consumeType = connection.getConsumeType();
                if (consumeType == ConsumeType.CONSUME_POP) {
                    // Pop consumers don't maintain traditional consume offsets;
                    // 0 or -1 diffTotal is normal behavior for newly started Pop consumers.
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to check Pop consumer status for {}: {}", consumerGroup, e.getMessage());
        }
        return false;
    }

    /**
     * Check if consumer group exists
     */
    private boolean consumerGroupExists(String groupName) {
        try {
            return metadataProvider.getConsumerGroup(groupName, Optional.empty()).isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validate consumer group configuration
     */
    private void validateConsumerGroup(ConsumerGroupInfo consumerGroup) {
        if (consumerGroup == null) {
            throw new IllegalArgumentException("Consumer group cannot be null");
        }
        if (consumerGroup.getConsumerGroupName() == null || consumerGroup.getConsumerGroupName().trim().isEmpty()) {
            throw new IllegalArgumentException("Consumer group name cannot be empty");
        }
        if (consumerGroup.getConsumerGroupName().length() > 255) {
            throw new IllegalArgumentException("Consumer group name too long");
        }
    }
}