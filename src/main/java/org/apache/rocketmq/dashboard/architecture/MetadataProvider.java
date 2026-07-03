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
package org.apache.rocketmq.dashboard.architecture;

import org.apache.rocketmq.dashboard.model.ACLPolicy;
import org.apache.rocketmq.dashboard.model.ACLUser;
import org.apache.rocketmq.dashboard.model.ClientInstance;
import org.apache.rocketmq.dashboard.model.ConsumerGroupInfo;
import org.apache.rocketmq.dashboard.model.LiteTopicQuota;
import org.apache.rocketmq.dashboard.model.LiteTopicSession;
import org.apache.rocketmq.dashboard.model.LiteTopicSummary;
import org.apache.rocketmq.dashboard.model.MessageInfo;
import org.apache.rocketmq.dashboard.model.NamespaceInfo;
import org.apache.rocketmq.dashboard.model.SubscriptionInfo;
import org.apache.rocketmq.dashboard.model.TopicInfo;
import org.apache.rocketmq.dashboard.model.TopicType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 *
 *
 *
 */
public interface MetadataProvider {

    // Removed

    /**
 *
     */
    List<NamespaceInfo> listNamespaces() throws Exception;

    /**
 *
     */
    Optional<NamespaceInfo> getNamespace(String namespace) throws Exception;

    /**
 *
     */
    void createNamespace(NamespaceInfo namespace) throws Exception;

    /**
 *
     */
    void updateNamespace(NamespaceInfo namespace) throws Exception;

    /**
 *
     */
    void deleteNamespace(String namespace) throws Exception;

    // Removed

    /**
 *
     */
    List<TopicInfo> listTopics(Optional<String> namespace) throws Exception;

    /**
 *
     */
    Optional<TopicInfo> getTopic(String topic, Optional<String> namespace) throws Exception;

    /**
 *
     */
    void createTopic(TopicInfo topic) throws Exception;

    /**
 *
     */
    void updateTopic(TopicInfo topic) throws Exception;

    /**
 *
     */
    void deleteTopic(String topic, Optional<String> namespace) throws Exception;

    /**
 *
     */
    boolean validateTopicType(String topic, TopicType expectedType) throws Exception;

    // Removed

    /**
 *
     */
    List<LiteTopicSummary> listLiteTopics(String pattern, Optional<String> namespace) throws Exception;

    /**
 *
     */
    LiteTopicSession getLiteTopicSession(String sessionId) throws Exception;

    /**
 *
     */
    void extendLiteTopicTTL(String topicPattern, long newTTL) throws Exception;

    /**
 *
     */
    LiteTopicQuota getLiteTopicQuota(Optional<String> namespace) throws Exception;

    // Removed

    /**
 *
     */
    List<ConsumerGroupInfo> listConsumerGroups(Optional<String> namespace) throws Exception;

    /**
 *
     */
    Optional<ConsumerGroupInfo> getConsumerGroup(String consumerGroup, Optional<String> namespace) throws Exception;

    /**
 *
     */
    void createConsumerGroup(ConsumerGroupInfo consumerGroup) throws Exception;

    /**
 *
     */
    void updateConsumerGroup(ConsumerGroupInfo consumerGroup) throws Exception;

    /**
 *
     */
    void deleteConsumerGroup(String consumerGroup, Optional<String> namespace) throws Exception;

    /**
     * List subscriptions for a consumer group.
     */
    List<SubscriptionInfo> listSubscriptions(String groupName) throws Exception;

    /**
     * Reset consumer group offset for a topic.
     */
    void resetConsumerGroupOffset(String groupName, String topic, long timestamp) throws Exception;

    // Removed

    /**
 *
     */
    List<ACLPolicy> listACLPolicy(Optional<String> namespace) throws Exception;

    /**
 *
     */
    List<ACLUser> listACLUsers() throws Exception;

    /**
 *
     */
    void createACLPolicy(ACLPolicy policy) throws Exception;

    /**
 *
     */
    void updateACLPolicy(ACLPolicy policy) throws Exception;

    /**
 *
     */
    void deleteACLPolicy(String policyId) throws Exception;

    /**
     * List ACL policies for a specific user.
     */
    List<ACLPolicy> listACLPolicies(String username) throws Exception;

    /**
     * Create an ACL user.
     */
    void createACLUser(ACLUser user) throws Exception;

    /**
     * Update an ACL user.
     */
    void updateACLUser(ACLUser user) throws Exception;

    /**
     * Delete an ACL user by username.
     */
    void deleteACLUser(String username) throws Exception;

    /**
     * Add an ACL policy.
     */
    void addACLPolicy(ACLPolicy policy) throws Exception;

    /**
     * Remove an ACL policy for a specific user.
     */
    void removeACLPolicy(String username, String policyId) throws Exception;

    /**
     * Get an ACL user by username.
     */
    Optional<ACLUser> getACLUser(String username) throws Exception;

    /**
     * Check ACL permission for a user on a resource with a specific action.
     */
    boolean checkACLPermission(String username, String resource, String action) throws Exception;

    // Removed

    /**
     * Query messages by topic within a time range.
     */
    List<MessageInfo> queryMessageByTopic(String topic, long beginTime, long endTime, int maxNum) throws Exception;

    /**
     * Query messages by topic and message key.
     */
    List<MessageInfo> queryMessageByTopicAndKey(String topic, String key, long beginTime, long endTime) throws Exception;

    /**
     * Query messages by consumer group.
     */
    List<MessageInfo> queryMessageByGroup(String consumerGroup, String topic, long beginTime, long endTime) throws Exception;

    /**
     * Get a message by its ID.
     */
    Optional<MessageInfo> getMessageById(String msgId) throws Exception;

    /**
     * Get messages by queue offset.
     */
    List<MessageInfo> getMessagesByOffset(String topic, String brokerName, int queueId, long offset, int count) throws Exception;

    /**
     * Search offset by timestamp.
     */
    long searchOffset(String topic, String brokerName, int queueId, long timestamp) throws Exception;

    /**
     * Get maximum offset for a queue.
     */
    long getMaxOffset(String topic, String brokerName, int queueId) throws Exception;

    /**
     * Get minimum offset for a queue.
     */
    long getMinOffset(String topic, String brokerName, int queueId) throws Exception;

    /**
     * Delete a message.
     */
    void deleteMessage(String topic, String msgId) throws Exception;

    /**
     * Resend a message to a new topic.
     */
    void resendMessage(String msgId, String newTopic) throws Exception;

    /**
 *
     */
    List<ClientInstance> listClientInstances(Optional<String> topic, Optional<String> group) throws Exception;

    /**
 *
     */
    Optional<ClientInstance> getClientInstance(String clientId) throws Exception;

    /**
 *
     */
    List<SubscriptionInfo> getClientSubscriptions(String clientId) throws Exception;

    // Removed

    /**
 *
     */
    String getProviderType();

    /**
 *
     */
    boolean supportsCapability(String capability);

    // ==================== Metrics Operations ====================

    /**
     * Get cluster-level metrics.
     */
    default Map<String, Object> getClusterMetrics() throws Exception {
        throw new UnsupportedOperationException("Cluster metrics not supported");
    }

    /**
     * Get broker-level metrics.
     */
    default Map<String, Object> getBrokerMetrics(String brokerName) throws Exception {
        throw new UnsupportedOperationException("Broker metrics not supported");
    }

    /**
     * Get topic-level metrics.
     */
    default Map<String, Object> getTopicMetrics(String topic) throws Exception {
        throw new UnsupportedOperationException("Topic metrics not supported");
    }

    /**
     * Get consumer group metrics.
     */
    default Map<String, Object> getConsumerGroupMetrics(String groupName) throws Exception {
        throw new UnsupportedOperationException("Consumer group metrics not supported");
    }

    /**
     * Get all brokers metrics.
     */
    default List<Map<String, Object>> getAllBrokersMetrics() throws Exception {
        throw new UnsupportedOperationException("All brokers metrics not supported");
    }

    /**
     * Get all topics metrics.
     */
    default List<Map<String, Object>> getAllTopicsMetrics() throws Exception {
        throw new UnsupportedOperationException("All topics metrics not supported");
    }

    /**
     * Get client metrics.
     */
    default Map<String, Object> getClientMetrics() throws Exception {
        throw new UnsupportedOperationException("Client metrics not supported");
    }

    /**
     * Get system metrics.
     */
    default Map<String, Object> getSystemMetrics() throws Exception {
        throw new UnsupportedOperationException("System metrics not supported");
    }

    /**
     * Get custom metrics.
     */
    default Map<String, Object> getCustomMetrics(String metricType) throws Exception {
        throw new UnsupportedOperationException("Custom metrics not supported");
    }

    /**
     * Configure metrics export.
     */
    default void configureMetricsExport(String config) throws Exception {
        throw new UnsupportedOperationException("Metrics configuration not supported");
    }

    // ==================== Client convenience methods ====================

    /**
     * List all clients (convenience method delegating to listClientInstances).
     */
    default List<ClientInstance> listClients() throws Exception {
        return listClientInstances(Optional.empty(), Optional.empty());
    }

    /**
     * List clients by protocol type.
     */
    default List<ClientInstance> listClientsByProtocol(String protocol) throws Exception {
        throw new UnsupportedOperationException("List clients by protocol not supported");
    }

    /**
     * List clients by type.
     */
    default List<ClientInstance> listClientsByType(String clientType) throws Exception {
        throw new UnsupportedOperationException("List clients by type not supported");
    }

    /**
     * List clients by cluster.
     */
    default List<ClientInstance> listClientsByCluster(String clusterName) throws Exception {
        throw new UnsupportedOperationException("List clients by cluster not supported");
    }

    /**
     * Get client by ID (convenience method delegating to getClientInstance).
     */
    default Optional<ClientInstance> getClient(String clientId) throws Exception {
        return getClientInstance(clientId);
    }

    /**
     * Kill a client connection.
     */
    default void killClient(String clientId, String reason) throws Exception {
        throw new UnsupportedOperationException("Kill client not supported");
    }

    /**
     * Update client configuration.
     */
    default void updateClientConfig(String clientId, String configKey, String configValue) throws Exception {
        throw new UnsupportedOperationException("Update client config not supported");
    }

    /**
     * Get connected clients for a broker.
     */
    default List<ClientInstance> getConnectedClients(String brokerAddress) throws Exception {
        throw new UnsupportedOperationException("Get connected clients not supported");
    }

    /**
     * Get idle clients based on threshold.
     */
    default List<ClientInstance> getIdleClients(long idleTimeThreshold) throws Exception {
        throw new UnsupportedOperationException("Get idle clients not supported");
    }

    /**
     * Get clients with specific issues.
     */
    default List<ClientInstance> getClientsWithIssue(String issueType) throws Exception {
        throw new UnsupportedOperationException("Get clients with issue not supported");
    }

    /**
     * Diagnose a client.
     */
    default void diagnoseClient(String clientId) throws Exception {
        throw new UnsupportedOperationException("Diagnose client not supported");
    }

    // ==================== Message convenience methods ====================

    /**
     * Consume message directly for testing.
     */
    default org.apache.rocketmq.remoting.protocol.body.ConsumeMessageDirectlyResult consumeMessageDirectly(
            String topic, String msgId, String consumerGroup, String clientId) throws Exception {
        throw new UnsupportedOperationException("Consume message directly not supported");
    }
}