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

import org.apache.rocketmq.common.admin.TopicConfig;
import org.apache.rocketmq.dashboard.architecture.MetadataProvider;
import org.apache.rocketmq.dashboard.model.NamespaceInfo;
import org.apache.rocketmq.dashboard.model.TopicInfo;
import org.apache.rocketmq.dashboard.model.TopicType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 *
 */
public class V4MetadataProvider implements MetadataProvider {

    private static final Logger log = LoggerFactory.getLogger(V4MetadataProvider.class);
    private static final String DEFAULT_NAMESPACE = "DEFAULT";

    private final org.apache.rocketmq.tools.admin.MQAdminExt mqAdminExt;

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

        List<String> topicNames = mqAdminExt.fetchTopicListFromNameServer().getTopicList();
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
        topicConfig.setTopicFilterType(org.apache.rocketmq.common.constant.TopicFilterType.SINGLE_TAG);

        // Removed
        org.apache.rocketmq.remoting.protocol.route.TopicRouteData topicRouteData = 
            mqAdminExt.examineTopicRouteInfo(topic.getTopicName());

        if (topicRouteData != null && topicRouteData.getBrokerDatas() != null) {
            for (org.apache.rocketmq.remoting.protocol.route.BrokerData brokerData : topicRouteData.getBrokerDatas()) {
                for (Map.Entry<Long, String> entry : brokerData.getBrokerAddrs().entrySet()) {
                    if (entry.getKey() == 0) { // Removed
                        try {
                            mqAdminExt.createAndUpdateTopicConfig(entry.getValue(), topicConfig);
                        } catch (Exception e) {
                            log.warn("Failed to create topic {} on broker {}", topic.getTopicName(), entry.getValue(), e);
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

        mqAdminExt.deleteTopic(topic);
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
        List<String> topicNames = mqAdminExt.fetchTopicListFromNameServer().getTopicList();
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
        // Removed
        throw new UnsupportedOperationException("V4 clusters do not support direct ConsumerGroup creation via Admin");
    }

    @Override
    public void updateConsumerGroup(ConsumerGroupInfo consumerGroup) throws Exception {
        // Removed
        throw new UnsupportedOperationException("V4 clusters do not support direct ConsumerGroup updates via Admin");
    }

    @Override
    public void deleteConsumerGroup(String consumerGroup, Optional<String> namespace) throws Exception {
        if (namespace.isPresent() && !DEFAULT_NAMESPACE.equals(namespace.get())) {
            return;
        }

        // Removed
        mqAdminExt.deleteTopic("%RETRY%" + consumerGroup);
    }

    // Removed

    @Override
    public List<ACLPolicy> listACLPolicy(Optional<String> namespace) throws Exception {
        return Collections.emptyList(); // Removed
    }

    @Override
    public List<ACLUser> listACLUsers() throws Exception {
        return Collections.emptyList(); // Removed
    }

    @Override
    public void createACLPolicy(ACLPolicy policy) throws Exception {
        throw new UnsupportedOperationException("V4 clusters have limited ACL support");
    }

    @Override
    public void updateACLPolicy(ACLPolicy policy) throws Exception {
        throw new UnsupportedOperationException("V4 clusters have limited ACL support");
    }

    @Override
    public void deleteACLPolicy(String policyId) throws Exception {
        throw new UnsupportedOperationException("V4 clusters have limited ACL support");
    }

    // Removed

    @Override
    public List<ClientInstance> listClientInstances(Optional<String> topic, Optional<String> group) throws Exception {
        List<ClientInstance> clientInstances = new ArrayList<>();

        // Removed
        List<String> topicNames = mqAdminExt.fetchTopicListFromNameServer().getTopicList();
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
                        client.setClientAddress(connection.getRemoteAddr());
                        client.setClientType(ClientInstance.ClientType.PRODUCER);
                        client.setLanguage(connection.getLanguage());
                        client.setSdkVersion(connection.getVersion());
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
}