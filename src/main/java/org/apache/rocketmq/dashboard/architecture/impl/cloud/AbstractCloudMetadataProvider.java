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
package org.apache.rocketmq.dashboard.architecture.impl.cloud;

import org.apache.rocketmq.dashboard.architecture.MetadataProvider;
import org.apache.rocketmq.dashboard.model.ACLPolicy;
import org.apache.rocketmq.dashboard.model.ACLUser;
import org.apache.rocketmq.dashboard.model.ClientInstance;
import org.apache.rocketmq.dashboard.model.CloudProviderConfig;
import org.apache.rocketmq.dashboard.model.ConsumerGroupInfo;
import org.apache.rocketmq.dashboard.model.LiteTopicQuota;
import org.apache.rocketmq.dashboard.model.LiteTopicSession;
import org.apache.rocketmq.dashboard.model.LiteTopicSummary;
import org.apache.rocketmq.dashboard.model.MessageInfo;
import org.apache.rocketmq.dashboard.model.NamespaceInfo;
import org.apache.rocketmq.dashboard.model.SubscriptionInfo;
import org.apache.rocketmq.dashboard.model.TopicInfo;
import org.apache.rocketmq.dashboard.model.TopicType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Abstract base class for cloud provider metadata implementations.
 *
 * <p>Per RIP-1 META-01 §5.3 M3, cloud providers implement the same
 * {@link MetadataProvider} SPI interface as V4/V5 providers, enabling
 * unified metadata management across self-hosted and cloud-hosted clusters.</p>
 *
 * <p>Subclasses must implement cloud-specific API calls for Topic/Group/Namespace
 * CRUD operations. Methods that are not supported by a particular cloud provider
 * should throw {@link UnsupportedOperationException} with a descriptive message.</p>
 *
 * <h3>Design Notes</h3>
 * <ul>
 *   <li>Namespace is always supported for cloud providers (5.0 feature)</li>
 *   <li>LiteTopic support depends on the specific cloud provider</li>
 *   <li>ACL management may use cloud-specific IAM rather than RocketMQ ACL</li>
 *   <li>Message query operations may have limitations in cloud environments</li>
 *   <li>Direct broker operations (offset, message delete/resend) are unsupported</li>
 * </ul>
 */
public abstract class AbstractCloudMetadataProvider implements MetadataProvider {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** Cloud provider configuration. */
    protected final CloudProviderConfig config;

    protected AbstractCloudMetadataProvider(CloudProviderConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("CloudProviderConfig must not be null");
        }
        this.config = config;
    }

    // ==================== Provider Identity ====================

    @Override
    public String getProviderType() {
        return config.getProviderType();
    }

    @Override
    public boolean supportsCapability(String capability) {
        // Cloud providers generally support namespace, liteTopic (varies), popConsume, aclV2, grpcClient
        // Subclasses can override for more precise capability reporting
        switch (capability) {
            case "namespace":
            case "popConsume":
            case "aclV2":
            case "grpcClient":
                return true;
            case "liteTopic":
            case "remotingClient":
                return false;  // Subclasses should override
            default:
                return false;
        }
    }

    // ==================== Namespace Operations ====================

    @Override
    public List<NamespaceInfo> listNamespaces() throws Exception {
        return doListNamespaces();
    }

    @Override
    public Optional<NamespaceInfo> getNamespace(String namespace) throws Exception {
        return doGetNamespace(namespace);
    }

    @Override
    public void createNamespace(NamespaceInfo namespace) throws Exception {
        doCreateNamespace(namespace);
    }

    @Override
    public void updateNamespace(NamespaceInfo namespace) throws Exception {
        doUpdateNamespace(namespace);
    }

    @Override
    public void deleteNamespace(String namespace) throws Exception {
        doDeleteNamespace(namespace);
    }

    // ==================== Topic Operations ====================

    @Override
    public List<TopicInfo> listTopics(Optional<String> namespace) throws Exception {
        return doListTopics(namespace);
    }

    @Override
    public Optional<TopicInfo> getTopic(String topic, Optional<String> namespace) throws Exception {
        return doGetTopic(topic, namespace);
    }

    @Override
    public void createTopic(TopicInfo topic) throws Exception {
        doCreateTopic(topic);
    }

    @Override
    public void updateTopic(TopicInfo topic) throws Exception {
        doUpdateTopic(topic);
    }

    @Override
    public void deleteTopic(String topic, Optional<String> namespace) throws Exception {
        doDeleteTopic(topic, namespace);
    }

    @Override
    public boolean validateTopicType(String topic, TopicType expectedType) throws Exception {
        return doValidateTopicType(topic, expectedType);
    }

    // ==================== LiteTopic Operations ====================

    @Override
    public List<LiteTopicSummary> listLiteTopics(String pattern, Optional<String> namespace) throws Exception {
        return doListLiteTopics(pattern, namespace);
    }

    @Override
    public LiteTopicSession getLiteTopicSession(String sessionId) throws Exception {
        return doGetLiteTopicSession(sessionId);
    }

    @Override
    public void extendLiteTopicTTL(String topicPattern, long newTTL) throws Exception {
        doExtendLiteTopicTTL(topicPattern, newTTL);
    }

    @Override
    public LiteTopicQuota getLiteTopicQuota(Optional<String> namespace) throws Exception {
        return doGetLiteTopicQuota(namespace);
    }

    // ==================== Consumer Group Operations ====================

    @Override
    public List<ConsumerGroupInfo> listConsumerGroups(Optional<String> namespace) throws Exception {
        return doListConsumerGroups(namespace);
    }

    @Override
    public Optional<ConsumerGroupInfo> getConsumerGroup(String consumerGroup, Optional<String> namespace) throws Exception {
        return doGetConsumerGroup(consumerGroup, namespace);
    }

    @Override
    public void createConsumerGroup(ConsumerGroupInfo consumerGroup) throws Exception {
        doCreateConsumerGroup(consumerGroup);
    }

    @Override
    public void updateConsumerGroup(ConsumerGroupInfo consumerGroup) throws Exception {
        doUpdateConsumerGroup(consumerGroup);
    }

    @Override
    public void deleteConsumerGroup(String consumerGroup, Optional<String> namespace) throws Exception {
        doDeleteConsumerGroup(consumerGroup, namespace);
    }

    @Override
    public List<SubscriptionInfo> listSubscriptions(String groupName) throws Exception {
        return doListSubscriptions(groupName);
    }

    @Override
    public void resetConsumerGroupOffset(String groupName, String topic, long timestamp) throws Exception {
        doResetConsumerGroupOffset(groupName, topic, timestamp);
    }

    // ==================== ACL Operations ====================

    @Override
    public List<ACLPolicy> listACLPolicy(Optional<String> namespace) throws Exception {
        return doListACLPolicy(namespace);
    }

    @Override
    public List<ACLUser> listACLUsers() throws Exception {
        return doListACLUsers();
    }

    @Override
    public void createACLPolicy(ACLPolicy policy) throws Exception {
        doCreateACLPolicy(policy);
    }

    @Override
    public void updateACLPolicy(ACLPolicy policy) throws Exception {
        doUpdateACLPolicy(policy);
    }

    @Override
    public void deleteACLPolicy(String policyId) throws Exception {
        doDeleteACLPolicy(policyId);
    }

    @Override
    public List<ACLPolicy> listACLPolicies(String username) throws Exception {
        return doListACLPolicies(username);
    }

    @Override
    public void createACLUser(ACLUser user) throws Exception {
        doCreateACLUser(user);
    }

    @Override
    public void updateACLUser(ACLUser user) throws Exception {
        doUpdateACLUser(user);
    }

    @Override
    public void deleteACLUser(String username) throws Exception {
        doDeleteACLUser(username);
    }

    @Override
    public void addACLPolicy(ACLPolicy policy) throws Exception {
        doCreateACLPolicy(policy);
    }

    @Override
    public void removeACLPolicy(String username, String policyId) throws Exception {
        doRemoveACLPolicy(username, policyId);
    }

    @Override
    public Optional<ACLUser> getACLUser(String username) throws Exception {
        return doGetACLUser(username);
    }

    @Override
    public boolean checkACLPermission(String username, String resource, String action) throws Exception {
        return doCheckACLPermission(username, resource, action);
    }

    // ==================== Message Operations ====================

    @Override
    public List<MessageInfo> queryMessageByTopic(String topic, long beginTime, long endTime, int maxNum) throws Exception {
        return doQueryMessageByTopic(topic, beginTime, endTime, maxNum);
    }

    @Override
    public List<MessageInfo> queryMessageByTopicAndKey(String topic, String key, long beginTime, long endTime) throws Exception {
        return doQueryMessageByTopicAndKey(topic, key, beginTime, endTime);
    }

    @Override
    public List<MessageInfo> queryMessageByGroup(String consumerGroup, String topic, long beginTime, long endTime) throws Exception {
        return doQueryMessageByGroup(consumerGroup, topic, beginTime, endTime);
    }

    @Override
    public Optional<MessageInfo> getMessageById(String msgId) throws Exception {
        return doGetMessageById(msgId);
    }

    @Override
    public List<MessageInfo> getMessagesByOffset(String topic, String brokerName, int queueId, long offset, int count) throws Exception {
        throw new UnsupportedOperationException(
            "Direct offset-based message query not available in cloud environment: " + config.getProviderType());
    }

    @Override
    public long searchOffset(String topic, String brokerName, int queueId, long timestamp) throws Exception {
        throw new UnsupportedOperationException(
            "Offset search not available in cloud environment: " + config.getProviderType());
    }

    @Override
    public long getMaxOffset(String topic, String brokerName, int queueId) throws Exception {
        throw new UnsupportedOperationException(
            "Max offset query not available in cloud environment: " + config.getProviderType());
    }

    @Override
    public long getMinOffset(String topic, String brokerName, int queueId) throws Exception {
        throw new UnsupportedOperationException(
            "Min offset query not available in cloud environment: " + config.getProviderType());
    }

    @Override
    public void deleteMessage(String topic, String msgId) throws Exception {
        throw new UnsupportedOperationException(
            "Message deletion not available in cloud environment: " + config.getProviderType());
    }

    @Override
    public void resendMessage(String msgId, String newTopic) throws Exception {
        throw new UnsupportedOperationException(
            "Message resend not available in cloud environment: " + config.getProviderType());
    }

    // ==================== Client Operations ====================

    @Override
    public List<ClientInstance> listClientInstances(Optional<String> topic, Optional<String> group) throws Exception {
        return doListClientInstances(topic, group);
    }

    @Override
    public Optional<ClientInstance> getClientInstance(String clientId) throws Exception {
        return doGetClientInstance(clientId);
    }

    @Override
    public List<SubscriptionInfo> getClientSubscriptions(String clientId) throws Exception {
        return doGetClientSubscriptions(clientId);
    }

    // ==================== Metrics Operations (default from interface) ====================
    // Default implementations from MetadataProvider interface throw UnsupportedOperationException

    // ==================== Template methods for subclasses ====================

    protected abstract List<NamespaceInfo> doListNamespaces() throws Exception;
    protected abstract Optional<NamespaceInfo> doGetNamespace(String namespace) throws Exception;
    protected abstract void doCreateNamespace(NamespaceInfo namespace) throws Exception;
    protected abstract void doUpdateNamespace(NamespaceInfo namespace) throws Exception;
    protected abstract void doDeleteNamespace(String namespace) throws Exception;

    protected abstract List<TopicInfo> doListTopics(Optional<String> namespace) throws Exception;
    protected abstract Optional<TopicInfo> doGetTopic(String topic, Optional<String> namespace) throws Exception;
    protected abstract void doCreateTopic(TopicInfo topic) throws Exception;
    protected abstract void doUpdateTopic(TopicInfo topic) throws Exception;
    protected abstract void doDeleteTopic(String topic, Optional<String> namespace) throws Exception;
    protected abstract boolean doValidateTopicType(String topic, TopicType expectedType) throws Exception;

    protected List<LiteTopicSummary> doListLiteTopics(String pattern, Optional<String> namespace) throws Exception {
        log.warn("LiteTopic listing not supported by cloud provider: {}", config.getProviderType());
        return Collections.emptyList();
    }

    protected LiteTopicSession doGetLiteTopicSession(String sessionId) throws Exception {
        throw new UnsupportedOperationException(
            "LiteTopic session not supported by cloud provider: " + config.getProviderType());
    }

    protected void doExtendLiteTopicTTL(String topicPattern, long newTTL) throws Exception {
        throw new UnsupportedOperationException(
            "LiteTopic TTL extension not supported by cloud provider: " + config.getProviderType());
    }

    protected LiteTopicQuota doGetLiteTopicQuota(Optional<String> namespace) throws Exception {
        throw new UnsupportedOperationException(
            "LiteTopic quota not supported by cloud provider: " + config.getProviderType());
    }

    protected abstract List<ConsumerGroupInfo> doListConsumerGroups(Optional<String> namespace) throws Exception;
    protected abstract Optional<ConsumerGroupInfo> doGetConsumerGroup(String consumerGroup, Optional<String> namespace) throws Exception;
    protected abstract void doCreateConsumerGroup(ConsumerGroupInfo consumerGroup) throws Exception;
    protected abstract void doUpdateConsumerGroup(ConsumerGroupInfo consumerGroup) throws Exception;
    protected abstract void doDeleteConsumerGroup(String consumerGroup, Optional<String> namespace) throws Exception;
    protected abstract List<SubscriptionInfo> doListSubscriptions(String groupName) throws Exception;
    protected abstract void doResetConsumerGroupOffset(String groupName, String topic, long timestamp) throws Exception;

    protected List<ACLPolicy> doListACLPolicy(Optional<String> namespace) throws Exception {
        log.warn("ACL policy listing not directly supported by cloud provider: {}. Use cloud IAM instead.", config.getProviderType());
        return Collections.emptyList();
    }

    protected List<ACLUser> doListACLUsers() throws Exception {
        log.warn("ACL user listing not directly supported by cloud provider: {}. Use cloud IAM instead.", config.getProviderType());
        return Collections.emptyList();
    }

    protected void doCreateACLPolicy(ACLPolicy policy) throws Exception {
        throw new UnsupportedOperationException("ACL policy management via cloud IAM: " + config.getProviderType());
    }

    protected void doUpdateACLPolicy(ACLPolicy policy) throws Exception {
        throw new UnsupportedOperationException("ACL policy management via cloud IAM: " + config.getProviderType());
    }

    protected void doDeleteACLPolicy(String policyId) throws Exception {
        throw new UnsupportedOperationException("ACL policy management via cloud IAM: " + config.getProviderType());
    }

    protected List<ACLPolicy> doListACLPolicies(String username) throws Exception {
        return Collections.emptyList();
    }

    protected void doCreateACLUser(ACLUser user) throws Exception {
        throw new UnsupportedOperationException("ACL user management via cloud IAM: " + config.getProviderType());
    }

    protected void doUpdateACLUser(ACLUser user) throws Exception {
        throw new UnsupportedOperationException("ACL user management via cloud IAM: " + config.getProviderType());
    }

    protected void doDeleteACLUser(String username) throws Exception {
        throw new UnsupportedOperationException("ACL user management via cloud IAM: " + config.getProviderType());
    }

    protected void doRemoveACLPolicy(String username, String policyId) throws Exception {
        throw new UnsupportedOperationException("ACL policy management via cloud IAM: " + config.getProviderType());
    }

    protected Optional<ACLUser> doGetACLUser(String username) throws Exception {
        return Optional.empty();
    }

    protected boolean doCheckACLPermission(String username, String resource, String action) throws Exception {
        // Cloud providers use IAM for permission checks, not RocketMQ ACL
        log.warn("ACL permission check delegated to cloud IAM for provider: {}", config.getProviderType());
        return false;
    }

    protected List<MessageInfo> doQueryMessageByTopic(String topic, long beginTime, long endTime, int maxNum) throws Exception {
        log.warn("Message query by topic not fully supported by cloud provider: {}", config.getProviderType());
        return Collections.emptyList();
    }

    protected List<MessageInfo> doQueryMessageByTopicAndKey(String topic, String key, long beginTime, long endTime) throws Exception {
        log.warn("Message query by key not fully supported by cloud provider: {}", config.getProviderType());
        return Collections.emptyList();
    }

    protected List<MessageInfo> doQueryMessageByGroup(String consumerGroup, String topic, long beginTime, long endTime) throws Exception {
        log.warn("Message query by group not fully supported by cloud provider: {}", config.getProviderType());
        return Collections.emptyList();
    }

    protected Optional<MessageInfo> doGetMessageById(String msgId) throws Exception {
        throw new UnsupportedOperationException(
            "Message get by ID not available in cloud environment: " + config.getProviderType());
    }

    protected abstract List<ClientInstance> doListClientInstances(Optional<String> topic, Optional<String> group) throws Exception;
    protected abstract Optional<ClientInstance> doGetClientInstance(String clientId) throws Exception;

    protected List<SubscriptionInfo> doGetClientSubscriptions(String clientId) throws Exception {
        log.warn("Client subscriptions not directly available in cloud environment: {}", config.getProviderType());
        return Collections.emptyList();
    }
}