package org.apache.rocketmq.dashboard.service;

import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.model.ConsumerGroupInfo;
import org.apache.rocketmq.dashboard.model.SubscriptionInfo;

import java.util.List;

/**
 * Consumer group management service interface
 */
public interface ConsumerService {

    /**
     * List all consumer groups
     */
    List<ConsumerGroupInfo> listConsumerGroups();

    /**
     * Get consumer groups by cluster name
     */
    List<ConsumerGroupInfo> getConsumerGroupsByCluster(String clusterName);

    /**
     * Get consumer group by name
     */
    ConsumerGroupInfo getConsumerGroup(String groupName);

    /**
     * Create consumer group
     */
    boolean createConsumerGroup(ConsumerGroupInfo consumerGroup);

    /**
     * Delete consumer group
     */
    boolean deleteConsumerGroup(String groupName);

    /**
     * Update consumer group
     */
    boolean updateConsumerGroup(ConsumerGroupInfo consumerGroup);

    /**
     * Get subscriptions for consumer group
     */
    List<SubscriptionInfo> getSubscriptions(String groupName);

    /**
     * Reset consumer group offset
     */
    boolean resetConsumerGroupOffset(String groupName, String topic, long timestamp);

    /**
     * Get cluster capability
     */
    ClusterCapability getClusterCapability();
}