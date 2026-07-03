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

import org.apache.rocketmq.dashboard.model.NamespaceInfo;
import org.apache.rocketmq.dashboard.model.TopicInfo;

import java.util.List;
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

    // Removed

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

}