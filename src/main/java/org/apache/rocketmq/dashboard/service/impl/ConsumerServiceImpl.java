package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.dashboard.architecture.ClusterProvider;
import org.apache.rocketmq.dashboard.architecture.MetadataProvider;
import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.model.ConsumerGroupInfo;
import org.apache.rocketmq.dashboard.model.SubscriptionInfo;
import org.apache.rocketmq.dashboard.service.ArchitectureBasedService;
import org.apache.rocketmq.dashboard.service.ConsumerService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * Consumer service implementation based on new architecture
 * Complete migration of v2.1.0 consumer group management capabilities
 */
@Service
public class ConsumerServiceImpl extends ArchitectureBasedService implements ConsumerService {

    @Resource
    private MetadataProvider metadataProvider;

    @Resource
    private ClusterProvider clusterProvider;

    @Override
    public List<ConsumerGroupInfo> listConsumerGroups() {
        try {
            return metadataProvider.listConsumerGroups();
        } catch (Exception e) {
            handleUnsupportedOperation("List consumer groups");
            return List.of();
        }
    }

    @Override
    public List<ConsumerGroupInfo> getConsumerGroupsByCluster(String clusterName) {
        try {
            if (supports("CONSUMER_GROUP_PER_CLUSTER")) {
                return metadataProvider.getConsumerGroupsByCluster(clusterName);
            }
            // V4 architecture returns all consumer groups
            return metadataProvider.listConsumerGroups();
        } catch (Exception e) {
            handleUnsupportedOperation("Get consumer groups by cluster");
            return List.of();
        }
    }

    @Override
    public ConsumerGroupInfo getConsumerGroup(String groupName) {
        try {
            return metadataProvider.getConsumerGroup(groupName)
                .orElse(null);
        } catch (Exception e) {
            handleUnsupportedOperation("Get consumer group");
            return null;
        }
    }

    @Override
    public boolean createConsumerGroup(ConsumerGroupInfo consumerGroup) {
        try {
            if (consumerGroup.getGroupName() == null || consumerGroup.getGroupName().trim().isEmpty()) {
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
            metadataProvider.deleteConsumerGroup(groupName);
            return true;
        } catch (Exception e) {
            handleUnsupportedOperation("Delete consumer group");
            return false;
        }
    }

    @Override
    public List<SubscriptionInfo> getSubscriptions(String groupName) {
        try {
            return metadataProvider.getSubscriptions(groupName);
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

    /**
     * Check if consumer group exists
     */
    private boolean consumerGroupExists(String groupName) {
        try {
            return metadataProvider.getConsumerGroup(groupName).isPresent();
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
        if (consumerGroup.getGroupName() == null || consumerGroup.getGroupName().trim().isEmpty()) {
            throw new IllegalArgumentException("Consumer group name cannot be empty");
        }
        if (consumerGroup.getGroupName().length() > 255) {
            throw new IllegalArgumentException("Consumer group name too long");
        }
    }
}