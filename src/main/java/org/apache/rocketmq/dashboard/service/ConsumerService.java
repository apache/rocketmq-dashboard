package org.apache.rocketmq.dashboard.service;

import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.model.ConsumerGroupInfo;
import org.apache.rocketmq.dashboard.model.GroupConsumeInfo;
import org.apache.rocketmq.dashboard.model.SubscriptionInfo;
import org.apache.rocketmq.dashboard.model.request.ConsumerConfigInfo;
import org.apache.rocketmq.dashboard.model.request.DeleteSubGroupRequest;
import org.apache.rocketmq.dashboard.model.request.ResetOffsetRequest;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;

import java.util.List;
import java.util.Set;

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

    /**
     * Query consumer group list with filtering options.
     * This method is used by dashboard collection tasks.
     *
     * @param skipSystemGroup whether to skip system consumer groups
     * @param filter optional filter string
     * @return list of consumer group info
     */
    /**
     * Query consumer group list with filtering options.
     * This method is used by dashboard collection tasks.
     *
     * @param skipSystemGroup whether to skip system consumer groups
     * @param filter optional filter string
     * @return list of consumer group info
     */
    default List<ConsumerGroupInfo> queryGroupList(boolean skipSystemGroup, String filter) {
        return listConsumerGroups();
    }

    /**
     * Query consumer group consume info by group name.
     * This method is used by monitor tasks.
     *
     * @param groupName consumer group name
     * @param namespace optional namespace (can be null)
     * @return group consume info with monitoring details
     */
    GroupConsumeInfo queryGroup(String groupName, String namespace);

    // ==================== Controller-facing methods ====================

    /**
     * Refresh a specific consumer group's metadata.
     */
    default Object refreshGroup(String address, String consumerGroup) {
        throw new UnsupportedOperationException("refreshGroup not supported");
    }

    /**
     * Refresh all consumer groups' metadata.
     */
    default Object refreshAllGroup(String address) {
        throw new UnsupportedOperationException("refreshAllGroup not supported");
    }

    /**
     * Reset consumer offset based on request.
     */
    default Object resetOffset(ResetOffsetRequest resetOffsetRequest) {
        throw new UnsupportedOperationException("resetOffset not supported");
    }

    /**
     * Examine subscription group config for a consumer group.
     */
    default Object examineSubscriptionGroupConfig(String consumerGroup) {
        throw new UnsupportedOperationException("examineSubscriptionGroupConfig not supported");
    }

    /**
     * Delete a subscription group.
     */
    default Object deleteSubGroup(DeleteSubGroupRequest deleteSubGroupRequest) {
        throw new UnsupportedOperationException("deleteSubGroup not supported");
    }

    /**
     * Create or update subscription group config.
     */
    default Object createAndUpdateSubscriptionGroupConfig(ConsumerConfigInfo consumerConfigInfo) {
        throw new UnsupportedOperationException("createAndUpdateSubscriptionGroupConfig not supported");
    }

    /**
     * Fetch broker name set by subscription group.
     */
    default Set<String> fetchBrokerNameSetBySubscriptionGroup(String consumerGroup) {
        throw new UnsupportedOperationException("fetchBrokerNameSetBySubscriptionGroup not supported");
    }

    /**
     * Query consume stats list by group name.
     */
    default Object queryConsumeStatsListByGroupName(String consumerGroup, String address) {
        throw new UnsupportedOperationException("queryConsumeStatsListByGroupName not supported");
    }

    /**
     * Get consumer connection info.
     */
    default ConsumerConnection getConsumerConnection(String consumerGroup, String address) {
        throw new UnsupportedOperationException("getConsumerConnection not supported");
    }

    /**
     * Get consumer running info.
     */
    default Object getConsumerRunningInfo(String consumerGroup, String clientId, boolean jstack) {
        throw new UnsupportedOperationException("getConsumerRunningInfo not supported");
    }

    /**
     * Query consume stats list by topic name.
     */
    default Object queryConsumeStatsListByTopicName(String topic) {
        throw new UnsupportedOperationException("queryConsumeStatsListByTopicName not supported");
    }
}