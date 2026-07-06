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
package org.apache.rocketmq.dashboard.service;

import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.model.TopicInfo;
import org.apache.rocketmq.dashboard.model.TopicType;
import org.apache.rocketmq.dashboard.model.request.SendTopicMessageRequest;
import org.apache.rocketmq.dashboard.model.request.TopicConfigInfo;

import java.util.List;

/**
 * Topic management service interface
 */
public interface TopicService {

    /**
     * List all topic names
     */
    List<String> getTopicList();

    /**
     * Get topics by cluster name
     */
    List<String> getTopicsByCluster(String clusterName);

    /**
     * Get topic info by name
     */
    TopicInfo getTopicInfo(String topic);

    /**
     * Get topic by name (alias for getTopicInfo)
     */
    default TopicInfo getTopic(String topic) {
        return getTopicInfo(topic);
    }

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
     * Get topic cluster list
     */
    List<String> getTopicClusterList(String topic);

    /**
     * Check if topic exists
     */
    boolean isTopicExist(String topic);

    /**
     * Get topic with namespace
     */
    TopicInfo getTopicWithNamespace(String topic, String namespace);

    /**
     * Get all topic list as TopicInfo objects
     */
    List<TopicInfo> getAllTopicList();

    /**
     * Get supported topic types
     */
    List<TopicType> getSupportedTopicTypes();

    /**
     * Get cluster capability
     */
    ClusterCapability getClusterCapability();

    // ==================== Controller-facing methods ====================

    /**
     * Fetch all topic list with filtering options.
     */
    default List<TopicInfo> fetchAllTopicList(boolean skipSysProcess, boolean skipRetryAndDlq) {
        return getAllTopicList();
    }

    /**
     * Examine all topic types.
     */
    default List<TopicType> examineAllTopicType() {
        return getSupportedTopicTypes();
    }

    /**
     * Get topic stats.
     */
    default Object stats(String topic) {
        throw new UnsupportedOperationException("stats not supported");
    }

    /**
     * Get topic route info.
     */
    default Object route(String topic) {
        throw new UnsupportedOperationException("route not supported");
    }

    /**
     * Create or update topic from config info.
     */
    default void createOrUpdate(TopicConfigInfo topicConfigInfo) {
        throw new UnsupportedOperationException("createOrUpdate not supported");
    }

    /**
     * Query topic consumer info.
     */
    default Object queryTopicConsumerInfo(String topic) {
        throw new UnsupportedOperationException("queryTopicConsumerInfo not supported");
    }

    /**
     * Examine topic config.
     */
    default Object examineTopicConfig(String topic) {
        throw new UnsupportedOperationException("examineTopicConfig not supported");
    }

    /**
     * Send topic message.
     */
    default Object sendTopicMessageRequest(SendTopicMessageRequest sendTopicMessageRequest) {
        throw new UnsupportedOperationException("sendTopicMessageRequest not supported");
    }

    /**
     * Delete topic with cluster name.
     */
    default boolean deleteTopic(String topic, String clusterName) {
        return deleteTopic(topic);
    }

    /**
     * Delete topic in specific broker.
     */
    default boolean deleteTopicInBroker(String brokerName, String topic) {
        throw new UnsupportedOperationException("deleteTopicInBroker not supported");
    }
}