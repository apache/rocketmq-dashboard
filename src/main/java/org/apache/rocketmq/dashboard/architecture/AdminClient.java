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

import org.apache.rocketmq.client.QueryResult;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.dashboard.model.AccessControlList;
import org.apache.rocketmq.dashboard.model.GroupConsumeInfo;
import org.apache.rocketmq.remoting.protocol.admin.TopicStatsTable;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.ConsumeMessageDirectlyResult;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.body.ProducerConnection;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;

import java.util.List;
import java.util.Properties;

/**
 *
 *
 *
 */
public interface AdminClient {

    // Removed

    /**
 *
     */
    ClusterInfo getClusterInfo() throws Exception;

    /**
 *
     */
    KVTable getBrokerRuntimeStats(String brokerAddr) throws Exception;

    /**
 *
     */
    void updateBrokerConfig(String brokerAddr, Properties properties) throws Exception;

    // Removed

    /**
 *
     */
    List<String> getTopicList() throws Exception;

    /**
 *
     */
    TopicRouteData getTopicRoute(String topic) throws Exception;

    /**
 *
     */
    TopicStatsTable getTopicStats(String topic) throws Exception;

    /**
 *
     */
    void createOrUpdateTopic(String topic, TopicConfig topicConfig) throws Exception;

    /**
 *
     */
    void deleteTopic(String topic, String clusterName) throws Exception;

    /**
 *
     */
    TopicList getTopicListFromBroker(String brokerAddr) throws Exception;

    // Removed

    /**
 *
     */
    List<String> getConsumerGroupList() throws Exception;

    /**
 *
     */
    ConsumerConnection getConsumerConnection(String consumerGroup) throws Exception;

    /**
 *
     */
    GroupConsumeInfo getGroupConsumeInfo(String consumerGroup) throws Exception;

    /**
 *
     */
    void resetConsumeOffset(String consumerGroup, String topic, long timestamp, boolean force) throws Exception;

    /**
 *
     */
    void createOrUpdateConsumerGroup(String consumerGroup, SubscriptionGroupConfig config) throws Exception;

    /**
 *
     */
    void deleteConsumerGroup(String consumerGroup, String brokerAddr) throws Exception;

    // Removed

    /**
 *
     */
    ProducerConnection getProducerConnection(String producerGroup, String topic) throws Exception;

    // Removed

    /**
 *
     */
    QueryResult queryMessage(String topic, String key, long begin, long end, int maxNum) throws Exception;

    /**
 *
     */
    MessageExt viewMessage(String topic, String msgId) throws Exception;

    /**
 *
     */
    ConsumeMessageDirectlyResult consumeMessageDirectly(String consumerGroup, String topic, String msgId) throws Exception;

    /**
 *
     */
    void replayMessage(String consumerGroup, String topic, String msgId) throws Exception;

    // Removed

    /**
 *
     */
    KVTable getNameServerConfig(String namesrvAddr) throws Exception;

    // Removed

    /**
 *
     */
    AccessControlList getAccessControlList(String brokerAddr) throws Exception;

    /**
 *
     */
    void updateAccessControlList(String brokerAddr, AccessControlList acl) throws Exception;

    // Removed

    /**
 *
     */
    ClusterAccessType getClientType();

    /**
 *
     */
    void shutdown();

}