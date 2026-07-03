package org.apache.rocketmq.dashboard.service;

import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.model.TopicInfo;
import org.apache.rocketmq.dashboard.model.TopicType;

import java.util.List;

/**
 * Topic management service interface
 */
public interface TopicService {

    /**
     * List all topics
     */
    List<TopicInfo> listTopics();

    /**
     * Get topics by cluster name
     */
    List<TopicInfo> getTopicsByCluster(String clusterName);

    /**
     * Get topic by name
     */
    TopicInfo getTopic(String topic);

    /**
     * Create topic
     */
    boolean createTopic(String topic, int readQueueNums, int writeQueueNums, int perm);

    /**
     * Create topic with type
     */
    boolean createTopicWithType(String topic, int readQueueNums, int writeQueueNums, int perm, TopicType topicType);

    /**
     * Update topic
     */
    boolean updateTopic(String topic, int readQueueNums, int writeQueueNums, int perm);

    /**
     * Delete topic
     */
    boolean deleteTopic(String topic);

    /**
     * Get topic stats
     */
    boolean getTopicStats(String topic);

    /**
     * Get cluster capability
     */
    ClusterCapability getClusterCapability();
}