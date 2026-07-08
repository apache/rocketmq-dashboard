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
package org.apache.rocketmq.dashboard.architecture.impl;

import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.TopicFilterType;
import org.apache.rocketmq.common.MQVersion;
import org.apache.rocketmq.dashboard.architecture.MetadataProvider;
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
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 *
 *
 */
public class V4MetadataProvider implements MetadataProvider {

    private static final Logger log = LoggerFactory.getLogger(V4MetadataProvider.class);
    private static final String DEFAULT_NAMESPACE = "DEFAULT";

    private final org.apache.rocketmq.tools.admin.MQAdminExt mqAdminExt;

    /** In-memory ACL user storage for V4 clusters. */
    private final ConcurrentHashMap<String, ACLUser> aclUserStore = new ConcurrentHashMap<>();

    /** In-memory ACL policy storage for V4 clusters. */
    private final ConcurrentHashMap<String, ACLPolicy> aclPolicyStore = new ConcurrentHashMap<>();

    public V4MetadataProvider(org.apache.rocketmq.tools.admin.MQAdminExt mqAdminExt) {
        this.mqAdminExt = mqAdminExt;
    }

    @Override
    public String getProviderType() {
        return "v4-namesrv";
    }

    @Override
    public boolean supportsCapability(String capability) {
        // Removed
        Set<String> unsupportedCapabilities = Set.of("namespace", "liteTopic", "popConsume", "grpcClient", "aclV2");
        return !unsupportedCapabilities.contains(capability);
    }

    // Removed

    @Override
    public List<NamespaceInfo> listNamespaces() throws Exception {
        // Removed
        NamespaceInfo defaultNamespace = new NamespaceInfo();
        defaultNamespace.setNamespaceName(DEFAULT_NAMESPACE);
        defaultNamespace.setDisplayName("Default Namespace");
        defaultNamespace.setDescription("Default namespace for V4 cluster");
        defaultNamespace.setStatus("ENABLED");
        defaultNamespace.setDefaultNamespace(true);
        return Collections.singletonList(defaultNamespace);
    }

    @Override
    public Optional<NamespaceInfo> getNamespace(String namespace) throws Exception {
        if (DEFAULT_NAMESPACE.equals(namespace)) {
            return listNamespaces().stream().findFirst();
        }
        return Optional.empty();
    }

    @Override
    public void createNamespace(NamespaceInfo namespace) throws Exception {
        throw new UnsupportedOperationException("V4 clusters do not support multiple namespaces");
    }

    @Override
    public void updateNamespace(NamespaceInfo namespace) throws Exception {
        throw new UnsupportedOperationException("V4 clusters do not support namespace updates");
    }

    @Override
    public void deleteNamespace(String namespace) throws Exception {
        throw new UnsupportedOperationException("V4 clusters do not support namespace deletion");
    }

    // Removed

    @Override
    public List<TopicInfo> listTopics(Optional<String> namespace) throws Exception {
        if (namespace.isPresent() && !DEFAULT_NAMESPACE.equals(namespace.get())) {
            return Collections.emptyList();
        }

        List<String> topicNames = new ArrayList<>(mqAdminExt.fetchAllTopicList().getTopicList());
        List<TopicInfo> topicInfos = new ArrayList<>();

        for (String topicName : topicNames) {
            if (topicName.startsWith("%RETRY%")) {
                continue; // Removed
            }

            TopicInfo topicInfo = new TopicInfo();
            topicInfo.setTopicName(topicName);
            topicInfo.setNamespace(DEFAULT_NAMESPACE);
            topicInfo.setTopicType(TopicType.NORMAL); // Removed
            topicInfo.setCreateTime(new Date());
            topicInfo.setUpdateTime(new Date());

            try {
                TopicRouteData routeData = mqAdminExt.examineTopicRouteInfo(topicName);
                if (routeData != null) {
                    // Removed
                    if (routeData.getQueueDatas() != null && !routeData.getQueueDatas().isEmpty()) {
                        org.apache.rocketmq.remoting.protocol.route.QueueData queueData = routeData.getQueueDatas().get(0);
                        topicInfo.setReadQueueNums(queueData.getReadQueueNums());
                        topicInfo.setWriteQueueNums(queueData.getWriteQueueNums());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to get topic route info for {}", topicName, e);
            }

            topicInfos.add(topicInfo);
        }

        return topicInfos;
    }

    @Override
    public Optional<TopicInfo> getTopic(String topic, Optional<String> namespace) throws Exception {
        if (namespace.isPresent() && !DEFAULT_NAMESPACE.equals(namespace.get())) {
            return Optional.empty();
        }

        if (!topicExists(topic)) {
            return Optional.empty();
        }

        return listTopics(namespace).stream()
            .filter(t -> t.getTopicName().equals(topic))
            .findFirst();
    }

    private boolean topicExists(String topic) {
        try {
            mqAdminExt.examineTopicRouteInfo(topic);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void createTopic(TopicInfo topic) throws Exception {
        TopicConfig topicConfig = new TopicConfig();
        topicConfig.setTopicName(topic.getTopicName());
        topicConfig.setReadQueueNums(topic.getReadQueueNums());
        topicConfig.setWriteQueueNums(topic.getWriteQueueNums());
        topicConfig.setPerm(topic.getPerm() != null ? topic.getPerm() : 6);
        topicConfig.setTopicFilterType(TopicFilterType.SINGLE_TAG);

        // Use examineBrokerClusterInfo() to get all master broker addresses
        // (examineTopicRouteInfo() would fail for a new topic that doesn't exist yet)
        org.apache.rocketmq.remoting.protocol.body.ClusterInfo clusterInfo =
            mqAdminExt.examineBrokerClusterInfo();
        if (clusterInfo != null && clusterInfo.getBrokerAddrTable() != null) {
            for (org.apache.rocketmq.remoting.protocol.route.BrokerData brokerData
                : clusterInfo.getBrokerAddrTable().values()) {
                if (brokerData.getBrokerAddrs() != null) {
                    String masterAddr = brokerData.getBrokerAddrs().get(0L);
                    if (masterAddr != null) {
                        try {
                            mqAdminExt.createAndUpdateTopicConfig(masterAddr, topicConfig);
                        } catch (Exception e) {
                            log.warn("Failed to create topic {} on broker {}",
                                topic.getTopicName(), masterAddr, e);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void updateTopic(TopicInfo topic) throws Exception {
        createTopic(topic); // Removed
    }

    @Override
    public void deleteTopic(String topic, Optional<String> namespace) throws Exception {
        if (namespace.isPresent() && !DEFAULT_NAMESPACE.equals(namespace.get())) {
            return;
        }

        TopicRouteData routeData = mqAdminExt.examineTopicRouteInfo(topic);
        if (routeData != null && routeData.getBrokerDatas() != null) {
            Set<String> clusters = new HashSet<>();
            for (org.apache.rocketmq.remoting.protocol.route.BrokerData brokerData : routeData.getBrokerDatas()) {
                String cluster = brokerData.getCluster();
                if (cluster != null) {
                    clusters.add(cluster);
                }
            }
            for (String clusterName : clusters) {
                mqAdminExt.deleteTopic(topic, clusterName);
            }
        }
    }

    @Override
    public boolean validateTopicType(String topic, TopicType expectedType) throws Exception {
        // Removed
        return TopicType.NORMAL.equals(expectedType);
    }

    // Removed

    @Override
    public List<LiteTopicSummary> listLiteTopics(String pattern, Optional<String> namespace) throws Exception {
        return Collections.emptyList(); // Removed
    }

    @Override
    public LiteTopicSession getLiteTopicSession(String sessionId) throws Exception {
        throw new UnsupportedOperationException("V4 clusters do not support LiteTopic sessions");
    }

    @Override
    public void extendLiteTopicTTL(String topicPattern, long newTTL) throws Exception {
        throw new UnsupportedOperationException("V4 clusters do not support LiteTopic TTL extension");
    }

    @Override
    public LiteTopicQuota getLiteTopicQuota(Optional<String> namespace) throws Exception {
        throw new UnsupportedOperationException("V4 clusters do not support LiteTopic quotas");
    }

    // Removed

    @Override
    public List<ConsumerGroupInfo> listConsumerGroups(Optional<String> namespace) throws Exception {
        if (namespace.isPresent() && !DEFAULT_NAMESPACE.equals(namespace.get())) {
            return Collections.emptyList();
        }

        // Removed
        List<String> topicNames = new ArrayList<>(mqAdminExt.fetchAllTopicList().getTopicList());
        Set<String> groupNames = new HashSet<>();

        for (String topicName : topicNames) {
            if (topicName.startsWith("%RETRY%")) {
                String groupName = topicName.substring("%RETRY%".length());
                groupNames.add(groupName);
            }
        }

        return groupNames.stream()
            .map(groupName -> {
                ConsumerGroupInfo groupInfo = new ConsumerGroupInfo();
                groupInfo.setConsumerGroupName(groupName);
                groupInfo.setNamespace(DEFAULT_NAMESPACE);
                groupInfo.setCreateTime(new Date());
                groupInfo.setUpdateTime(new Date());
                return groupInfo;
            })
            .collect(Collectors.toList());
    }

    @Override
    public Optional<ConsumerGroupInfo> getConsumerGroup(String consumerGroup, Optional<String> namespace) throws Exception {
        if (namespace.isPresent() && !DEFAULT_NAMESPACE.equals(namespace.get())) {
            return Optional.empty();
        }

        List<ConsumerGroupInfo> allGroups = listConsumerGroups(namespace);
        return allGroups.stream()
            .filter(g -> g.getConsumerGroupName().equals(consumerGroup))
            .findFirst();
    }

    @Override
    public void createConsumerGroup(ConsumerGroupInfo consumerGroup) throws Exception {
        if (consumerGroup == null || consumerGroup.getConsumerGroupName() == null) {
            throw new IllegalArgumentException("ConsumerGroupInfo must have a valid consumerGroupName");
        }
        org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig config =
            new org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig();
        config.setGroupName(consumerGroup.getConsumerGroupName());
        config.setConsumeEnable(true);
        config.setRetryMaxTimes(16);

        // Use examineBrokerClusterInfo() to get master broker addresses directly
        // (previously iterated all topics which was very inefficient)
        org.apache.rocketmq.remoting.protocol.body.ClusterInfo clusterInfo =
            mqAdminExt.examineBrokerClusterInfo();
        if (clusterInfo != null && clusterInfo.getBrokerAddrTable() != null) {
            for (org.apache.rocketmq.remoting.protocol.route.BrokerData brokerData
                : clusterInfo.getBrokerAddrTable().values()) {
                if (brokerData.getBrokerAddrs() != null) {
                    String masterAddr = brokerData.getBrokerAddrs().get(0L);
                    if (masterAddr != null) {
                        try {
                            mqAdminExt.createAndUpdateSubscriptionGroupConfig(masterAddr, config);
                        } catch (Exception e) {
                            log.warn("Failed to create consumer group {} on broker {}",
                                consumerGroup.getConsumerGroupName(), masterAddr, e);
                        }
                    }
                }
            }
        }
        log.info("Created consumer group: {}", consumerGroup.getConsumerGroupName());
    }

    @Override
    public void updateConsumerGroup(ConsumerGroupInfo consumerGroup) throws Exception {
        if (consumerGroup == null || consumerGroup.getConsumerGroupName() == null) {
            throw new IllegalArgumentException("ConsumerGroupInfo must have a valid consumerGroupName");
        }
        createConsumerGroup(consumerGroup);
    }

    @Override
    public void deleteConsumerGroup(String consumerGroup, Optional<String> namespace) throws Exception {
        if (namespace.isPresent() && !DEFAULT_NAMESPACE.equals(namespace.get())) {
            return;
        }

        // Delete subscription group config from all brokers, then delete the retry topic
        org.apache.rocketmq.remoting.protocol.body.ClusterInfo clusterInfo =
            mqAdminExt.examineBrokerClusterInfo();
        if (clusterInfo != null && clusterInfo.getBrokerAddrTable() != null) {
            for (org.apache.rocketmq.remoting.protocol.route.BrokerData brokerData
                : clusterInfo.getBrokerAddrTable().values()) {
                if (brokerData.getBrokerAddrs() != null) {
                    String brokerAddr = brokerData.selectBrokerAddr();
                    if (brokerAddr != null) {
                        try {
                            mqAdminExt.deleteSubscriptionGroup(brokerAddr, consumerGroup, true);
                        } catch (Exception e) {
                            log.warn("Failed to delete subscription group {} on broker {}",
                                consumerGroup, brokerAddr, e);
                        }
                    }
                }
            }
        }

        // Also delete the retry topic
        String retryTopic = "%RETRY%" + consumerGroup;
        try {
            TopicRouteData routeData = mqAdminExt.examineTopicRouteInfo(retryTopic);
            if (routeData != null && routeData.getBrokerDatas() != null) {
                Set<String> clusters = new HashSet<>();
                for (org.apache.rocketmq.remoting.protocol.route.BrokerData bd : routeData.getBrokerDatas()) {
                    String cluster = bd.getCluster();
                    if (cluster != null) {
                        clusters.add(cluster);
                    }
                }
                for (String clusterName : clusters) {
                    mqAdminExt.deleteTopic(retryTopic, clusterName);
                }
            }
        } catch (Exception e) {
            log.debug("Retry topic {} not found or already deleted: {}", retryTopic, e.getMessage());
        }
    }

    @Override
    public List<SubscriptionInfo> listSubscriptions(String groupName) throws Exception {
        List<SubscriptionInfo> subscriptions = new ArrayList<>();
        try {
            org.apache.rocketmq.remoting.protocol.body.ConsumerConnection connection =
                mqAdminExt.examineConsumerConnectionInfo(groupName);
            if (connection != null && connection.getSubscriptionTable() != null) {
                for (org.apache.rocketmq.remoting.protocol.heartbeat.SubscriptionData subData
                    : connection.getSubscriptionTable().values()) {
                    SubscriptionInfo info = new SubscriptionInfo();
                    info.setTopic(subData.getTopic());
                    info.setSubExpression(subData.getSubString() != null ? subData.getSubString() : "*");
                    subscriptions.add(info);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list subscriptions for group {}: {}", groupName, e.getMessage());
        }
        return subscriptions;
    }

    @Override
    public void resetConsumerGroupOffset(String groupName, String topic, long timestamp) throws Exception {
        mqAdminExt.resetOffsetByTimestamp(groupName, topic, timestamp, false);
        log.info("Reset consumer group offset: group={}, topic={}, timestamp={}", groupName, topic, timestamp);
    }

    // Removed

    @Override
    public List<ACLPolicy> listACLPolicy(Optional<String> namespace) throws Exception {
        return listACLPolicies(namespace.orElse(null));
    }

    @Override
    public List<ACLUser> listACLUsers() throws Exception {
        return new ArrayList<>(aclUserStore.values());
    }

    @Override
    public void createACLPolicy(ACLPolicy policy) throws Exception {
        addACLPolicy(policy);
    }

    @Override
    public void updateACLPolicy(ACLPolicy policy) throws Exception {
        if (policy == null || policy.getPolicyId() == null) {
            throw new IllegalArgumentException("ACL policy must have a valid policyId");
        }
        if (!aclPolicyStore.containsKey(policy.getPolicyId())) {
            throw new IllegalArgumentException("ACL policy '" + policy.getPolicyId() + "' not found");
        }
        policy.setUpdateTime(new Date());
        aclPolicyStore.put(policy.getPolicyId(), policy);
        log.info("Updated ACL policy: {}", policy.getPolicyId());
    }

    @Override
    public void deleteACLPolicy(String policyId) throws Exception {
        removeACLPolicy(null, policyId);
    }

    // Removed

    @Override
    public List<ClientInstance> listClientInstances(Optional<String> topic, Optional<String> group) throws Exception {
        List<ClientInstance> clientInstances = new ArrayList<>();

        // Removed
        List<String> topicNames = new ArrayList<>(mqAdminExt.fetchAllTopicList().getTopicList());
        for (String topicName : topicNames) {
            if (topic.isPresent() && !topic.get().equals(topicName)) {
                continue;
            }

            try {
                org.apache.rocketmq.remoting.protocol.body.ProducerConnection producerConnection =
                    mqAdminExt.examineProducerConnectionInfo("DEFAULT_PRODUCER", topicName);

                if (producerConnection != null && producerConnection.getConnectionSet() != null) {
                    producerConnection.getConnectionSet().forEach(connection -> {
                        ClientInstance client = new ClientInstance();
                        client.setClientId(connection.getClientId());
                        client.setClientAddress(connection.getClientAddr());
                        client.setClientType(ClientInstance.ClientType.PRODUCER);
                        client.setLanguage(connection.getLanguage().name());
                        client.setSdkVersion(MQVersion.getVersionDesc(connection.getVersion()));
                        client.setProtocolType(ClientInstance.ProtocolType.REMOTING);
                        client.setConnectTime(new Date());
                        client.setActive(true);
                        client.setProducerGroup("DEFAULT_PRODUCER");
                        clientInstances.add(client);
                    });
                }
            } catch (Exception e) {
                log.debug("No producer info for topic: {}", topicName);
            }
        }

        return clientInstances;
    }

    @Override
    public Optional<ClientInstance> getClientInstance(String clientId) throws Exception {
        List<ClientInstance> allClients = listClientInstances(Optional.empty(), Optional.empty());
        return allClients.stream()
            .filter(c -> c.getClientId().equals(clientId))
            .findFirst();
    }

    @Override
    public List<SubscriptionInfo> getClientSubscriptions(String clientId) throws Exception {
        // Removed
        return Collections.emptyList();
    }

    @Override
    public List<MessageInfo> queryMessageByTopic(String topic, long beginTime, long endTime, int maxNum) throws Exception {
        List<MessageInfo> result = new ArrayList<>();
        try {
            org.apache.rocketmq.client.QueryResult queryResult = mqAdminExt.queryMessage(
                    topic, null, maxNum, beginTime, endTime);
            if (queryResult != null && queryResult.getMessageList() != null) {
                for (org.apache.rocketmq.common.message.MessageExt msg : queryResult.getMessageList()) {
                    result.add(convertToMessageInfo(msg));
                }
            }
        } catch (org.apache.rocketmq.client.exception.MQClientException e) {
            if (e.getResponseCode() != 200) {
                throw e;
            }
            log.error("queryMessageByTopic no message for topic: {}, error: {}", topic, e.getMessage());
        }
        return result;
    }

    @Override
    public List<MessageInfo> queryMessageByTopicAndKey(String topic, String key, long beginTime, long endTime) throws Exception {
        List<MessageInfo> result = new ArrayList<>();
        org.apache.rocketmq.client.QueryResult queryResult = mqAdminExt.queryMessage(
            topic, key, 100, beginTime, endTime);
        if (queryResult != null && queryResult.getMessageList() != null) {
            for (org.apache.rocketmq.common.message.MessageExt msg : queryResult.getMessageList()) {
                result.add(convertToMessageInfo(msg));
            }
        }
        return result;
    }

    @Override
    public List<MessageInfo> queryMessageByGroup(String consumerGroup, String topic, long beginTime, long endTime) throws Exception {
        List<MessageInfo> result = new ArrayList<>();
        String retryTopic = "%RETRY%" + consumerGroup;
        org.apache.rocketmq.client.QueryResult queryResult = mqAdminExt.queryMessage(
            retryTopic, null, 100, beginTime, endTime);
        if (queryResult != null && queryResult.getMessageList() != null) {
            for (org.apache.rocketmq.common.message.MessageExt msg : queryResult.getMessageList()) {
                if (topic == null || topic.equals(msg.getTopic())) {
                    result.add(convertToMessageInfo(msg));
                }
            }
        }
        return result;
    }

    @Override
    public Optional<MessageInfo> getMessageById(String msgId) throws Exception {
        return Optional.empty();
    }

    @Override
    public List<MessageInfo> getMessagesByOffset(String topic, String brokerName, int queueId, long offset, int count) throws Exception {
        log.debug("getMessagesByOffset: topic={}, broker={}, queue={}, offset={}, count={} - partial support",
            topic, brokerName, queueId, offset, count);
        return new ArrayList<>();
    }

    @Override
    public long searchOffset(String topic, String brokerName, int queueId, long timestamp) throws Exception {
        return mqAdminExt.searchOffset(brokerName, topic, queueId, timestamp, 3000L);
    }

    @Override
    public long getMaxOffset(String topic, String brokerName, int queueId) throws Exception {
        return mqAdminExt.maxOffset(new org.apache.rocketmq.common.message.MessageQueue(topic, brokerName, queueId));
    }

    @Override
    public long getMinOffset(String topic, String brokerName, int queueId) throws Exception {
        return mqAdminExt.minOffset(new org.apache.rocketmq.common.message.MessageQueue(topic, brokerName, queueId));
    }

    @Override
    public void deleteMessage(String topic, String msgId) throws Exception {
        log.info("deleteMessage: topic={}, msgId={}", topic, msgId);
    }

    @Override
    public void resendMessage(String msgId, String newTopic) throws Exception {
        log.info("resendMessage: msgId={}, newTopic={}", msgId, newTopic);
    }

    private MessageInfo convertToMessageInfo(org.apache.rocketmq.common.message.MessageExt msg) {
        MessageInfo info = new MessageInfo();
        info.setMsgId(msg.getMsgId());
        info.setTopic(msg.getTopic());
        info.setBornTimestamp(msg.getBornTimestamp());
        info.setStoreTimestamp(msg.getStoreTimestamp());
        info.setBody(new String(msg.getBody(), java.nio.charset.StandardCharsets.UTF_8));
        info.setTags(msg.getTags());
        info.setKeys(msg.getKeys());
        return info;
    }

    // ==================== Client Convenience Methods (RIP-1 CLIENT-01) ====================

    @Override
    public List<ClientInstance> listClientsByProtocol(String protocol) throws Exception {
        List<ClientInstance> all = listClientInstances(Optional.empty(), Optional.empty());
        return all.stream()
            .filter(c -> protocol.equalsIgnoreCase(
                c.getProtocolType() != null ? c.getProtocolType().name() : "REMOTING"))
            .collect(Collectors.toList());
    }

    @Override
    public List<ClientInstance> listClientsByType(String clientType) throws Exception {
        List<ClientInstance> all = listClientInstances(Optional.empty(), Optional.empty());
        return all.stream()
            .filter(c -> clientType.equalsIgnoreCase(
                c.getClientType() != null ? c.getClientType().name() : ""))
            .collect(Collectors.toList());
    }

    @Override
    public List<ClientInstance> listClientsByCluster(String clusterName) throws Exception {
        // V4 returns all clients since cluster-level filtering is done at a higher level
        return listClientInstances(Optional.empty(), Optional.empty());
    }

    @Override
    public List<ClientInstance> getConnectedClients(String brokerAddress) throws Exception {
        List<ClientInstance> result = new ArrayList<>();
        List<ClientInstance> all = listClientInstances(Optional.empty(), Optional.empty());
        for (ClientInstance c : all) {
            if (c.getClientAddress() != null && c.getClientAddress().contains(brokerAddress)) {
                result.add(c);
            }
        }
        return result;
    }

    @Override
    public List<ClientInstance> getIdleClients(long idleTimeThreshold) throws Exception {
        List<ClientInstance> result = new ArrayList<>();
        long now = System.currentTimeMillis();
        List<ClientInstance> all = listClientInstances(Optional.empty(), Optional.empty());
        for (ClientInstance c : all) {
            if (c.getLastHeartbeatTime() != null) {
                long idleTime = now - c.getLastHeartbeatTime().getTime();
                if (idleTime > idleTimeThreshold) {
                    result.add(c);
                }
            }
        }
        return result;
    }

    @Override
    public List<ClientInstance> getClientsWithIssue(String issueType) throws Exception {
        // V4 Remoting clients don't expose issue diagnostics (gRPC-only feature)
        return Collections.emptyList();
    }

    @Override
    public void killClient(String clientId, String reason) throws Exception {
        log.info("Kill client requested: clientId={}, reason={} (not supported in V4 Remoting mode)", clientId, reason);
    }

    @Override
    public void updateClientConfig(String clientId, String configKey, String configValue) throws Exception {
        log.info("Update client config requested: clientId={}, key={}, value={} (not supported in V4 Remoting mode)",
            clientId, configKey, configValue);
    }

    // ==================== ACL Implementations (RIP-1 AUTH-01) ====================

    @Override
    public List<ACLPolicy> listACLPolicies(String namespace) throws Exception {
        List<ACLPolicy> result = new ArrayList<>();
        for (ACLPolicy policy : aclPolicyStore.values()) {
            if (namespace == null || namespace.isEmpty()
                || (policy.getNamespace() != null && namespace.equals(policy.getNamespace()))) {
                result.add(policy);
            }
        }
        log.debug("listACLPolicies for namespace={}: {} policies", namespace, result.size());
        return result;
    }

    @Override
    public void createACLUser(ACLUser user) throws Exception {
        if (user == null || user.getUserName() == null || user.getUserName().trim().isEmpty()) {
            throw new IllegalArgumentException("ACL user must have a valid username");
        }
        if (aclUserStore.containsKey(user.getUserName())) {
            throw new IllegalArgumentException("ACL user '" + user.getUserName() + "' already exists");
        }
        user.setCreateTime(new Date());
        user.setUpdateTime(new Date());
        aclUserStore.put(user.getUserName(), user);
        log.info("Created ACL user: {}", user.getUserName());
    }

    @Override
    public void updateACLUser(ACLUser user) throws Exception {
        if (user == null || user.getUserName() == null || user.getUserName().trim().isEmpty()) {
            throw new IllegalArgumentException("ACL user must have a valid username");
        }
        if (!aclUserStore.containsKey(user.getUserName())) {
            throw new IllegalArgumentException("ACL user '" + user.getUserName() + "' not found");
        }
        user.setUpdateTime(new Date());
        aclUserStore.put(user.getUserName(), user);
        log.info("Updated ACL user: {}", user.getUserName());
    }

    @Override
    public void deleteACLUser(String username) throws Exception {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        ACLUser removed = aclUserStore.remove(username);
        if (removed == null) {
            throw new IllegalArgumentException("ACL user '" + username + "' not found");
        }
        // Also remove policies associated with this user
        aclPolicyStore.values().removeIf(p -> p.getUsers() != null && p.getUsers().contains(username));
        log.info("Deleted ACL user: {}", username);
    }

    @Override
    public void addACLPolicy(ACLPolicy policy) throws Exception {
        if (policy == null) {
            throw new IllegalArgumentException("ACL policy cannot be null");
        }
        if (policy.getPolicyId() == null || policy.getPolicyId().isEmpty()) {
            policy.setPolicyId(UUID.randomUUID().toString());
        }
        policy.setCreateTime(new Date());
        policy.setUpdateTime(new Date());
        aclPolicyStore.put(policy.getPolicyId(), policy);
        log.info("Added ACL policy: {} for users: {}", policy.getPolicyId(), policy.getUsers());
    }

    @Override
    public void removeACLPolicy(String namespace, String policyName) throws Exception {
        if (policyName == null || policyName.trim().isEmpty()) {
            throw new IllegalArgumentException("Policy name/ID cannot be empty");
        }
        ACLPolicy removed = aclPolicyStore.remove(policyName);
        if (removed == null) {
            // Try to find by policy name match
            for (Map.Entry<String, ACLPolicy> entry : aclPolicyStore.entrySet()) {
                if (policyName.equals(entry.getValue().getPolicyName())) {
                    aclPolicyStore.remove(entry.getKey());
                    log.info("Removed ACL policy by name: {}", policyName);
                    return;
                }
            }
            throw new IllegalArgumentException("ACL policy '" + policyName + "' not found");
        }
        log.info("Removed ACL policy: {}", policyName);
    }

    @Override
    public Optional<ACLUser> getACLUser(String username) throws Exception {
        if (username == null || username.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(aclUserStore.get(username));
    }

    @Override
    public boolean checkACLPermission(String username, String resource, String action) throws Exception {
        if (username == null || resource == null || action == null) {
            return false;
        }
        // Check user exists
        if (!aclUserStore.containsKey(username)) {
            log.debug("ACL check failed: user '{}' not found", username);
            return false;
        }
        // Check policies for matching resource and action
        for (ACLPolicy policy : aclPolicyStore.values()) {
            if (policy.getUsers() != null && policy.getUsers().contains(username)) {
                if (policy.getResources() != null && matchesResource(policy.getResources(), resource)) {
                    if (policy.getActions() != null && matchesAction(policy.getActions(), action)) {
                        if ("DENY".equalsIgnoreCase(policy.getPolicyType())) {
                            log.debug("ACL check: DENY for user={} resource={} action={}", username, resource, action);
                            return false;
                        }
                        log.debug("ACL check: ALLOW for user={} resource={} action={}", username, resource, action);
                        return true;
                    }
                }
            }
        }
        log.debug("ACL check: no matching policy for user={} resource={} action={}", username, resource, action);
        return false;
    }

    private boolean matchesResource(Set<String> policyResources, String resource) {
        if (policyResources.contains("*") || policyResources.contains(resource)) {
            return true;
        }
        for (String pr : policyResources) {
            if (pr.endsWith("*") && resource.startsWith(pr.substring(0, pr.length() - 1))) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAction(Set<String> policyActions, String action) {
        return policyActions.contains("*") || policyActions.contains(action);
    }
}