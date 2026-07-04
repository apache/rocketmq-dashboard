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

import org.apache.rocketmq.client.QueryResult;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.dashboard.architecture.AdminClient;
import org.apache.rocketmq.dashboard.architecture.ClusterAccessType;
import org.apache.rocketmq.dashboard.model.AccessControlList;
import org.apache.rocketmq.dashboard.model.GroupConsumeInfo;
import org.apache.rocketmq.remoting.protocol.admin.TopicStatsTable;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.ConsumeMessageDirectlyResult;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.body.ProducerConnection;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 *
 *
 */
public class RemotingAdminClient implements AdminClient {

    private static final Logger log = LoggerFactory.getLogger(RemotingAdminClient.class);

    private final MQAdminExt mqAdminExt;

    public RemotingAdminClient(MQAdminExt mqAdminExt) {
        Assert.notNull(mqAdminExt, "MQAdminExt cannot be null");
        this.mqAdminExt = mqAdminExt;
    }

    @Override
    public ClusterAccessType getClientType() {
        return ClusterAccessType.V4_NAMESRV;
    }

    @Override
    public ClusterInfo getClusterInfo() throws Exception {
        return mqAdminExt.examineBrokerClusterInfo();
    }

    @Override
    public KVTable getBrokerRuntimeStats(String brokerAddr) throws Exception {
        return mqAdminExt.fetchBrokerRuntimeStats(brokerAddr);
    }

    @Override
    public void updateBrokerConfig(String brokerAddr, Properties properties) throws Exception {
        mqAdminExt.updateBrokerConfig(brokerAddr, properties);
    }

    @Override
    public List<String> getTopicList() throws Exception {
        TopicList topicList = mqAdminExt.fetchAllTopicList();
        return new ArrayList<>(topicList.getTopicList());
    }

    @Override
    public TopicRouteData getTopicRoute(String topic) throws Exception {
        return mqAdminExt.examineTopicRouteInfo(topic);
    }

    @Override
    public TopicStatsTable getTopicStats(String topic) throws Exception {
        return mqAdminExt.examineTopicStats(topic);
    }

    @Override
    public void createOrUpdateTopic(String topic, TopicConfig topicConfig) throws Exception {
        // Removed
        TopicRouteData topicRouteData = mqAdminExt.examineTopicRouteInfo(topic);
        if (topicRouteData != null && topicRouteData.getBrokerDatas() != null) {
            for (BrokerData brokerData : topicRouteData.getBrokerDatas()) {
                for (Map.Entry<Long, String> entry : brokerData.getBrokerAddrs().entrySet()) {
                    if (entry.getKey() == 0) { // Removed
                        try {
                            mqAdminExt.createAndUpdateTopicConfig(entry.getValue(), topicConfig);
                        } catch (Exception e) {
                            log.warn("Failed to create topic {} on broker {}, error: {}",
                                topic, entry.getValue(), e.getMessage());
                        }
                    }
                }
            }
        }
    }

    @Override
    public void deleteTopic(String topic, String clusterName) throws Exception {
        Set<String> clusters = new HashSet<>();
        clusters.add(clusterName);
        mqAdminExt.deleteTopicInBroker(clusters, topic);
        mqAdminExt.deleteTopicInNameServer(clusters, topic);
    }

    @Override
    public TopicList getTopicListFromBroker(String brokerAddr) throws Exception {
        TopicList result = mqAdminExt.fetchAllTopicList();
        return result;
    }

    @Override
    public List<String> getConsumerGroupList() throws Exception {
        // Removed
        return new ArrayList<>(mqAdminExt.fetchAllTopicList().getTopicList());
    }

    @Override
    public ConsumerConnection getConsumerConnection(String consumerGroup) throws Exception {
        // Removed
        return mqAdminExt.examineConsumerConnectionInfo(consumerGroup);
    }

    @Override
    public GroupConsumeInfo getGroupConsumeInfo(String consumerGroup) throws Exception {
        // Removed
        // Removed
        throw new UnsupportedOperationException("getGroupConsumeInfo not implemented yet");
    }

    @Override
    public void resetConsumeOffset(String consumerGroup, String topic, long timestamp, boolean force) throws Exception {
        mqAdminExt.resetOffsetByTimestamp(topic, consumerGroup, timestamp, force);
    }

    @Override
    public void createOrUpdateConsumerGroup(String consumerGroup, SubscriptionGroupConfig config) throws Exception {
        // Removed
        TopicList topicList = mqAdminExt.fetchAllTopicList();
        if (topicList.getTopicList() != null) {
            for (String topic : topicList.getTopicList()) {
                TopicRouteData topicRouteData = mqAdminExt.examineTopicRouteInfo(topic);
                if (topicRouteData != null && topicRouteData.getBrokerDatas() != null) {
                    for (BrokerData brokerData : topicRouteData.getBrokerDatas()) {
                        for (Map.Entry<Long, String> entry : brokerData.getBrokerAddrs().entrySet()) {
                            if (entry.getKey() == 0) { // Removed
                                try {
                                    mqAdminExt.createAndUpdateSubscriptionGroupConfig(entry.getValue(), config);
                                } catch (Exception e) {
                                    log.warn("Failed to create consumer group {} on broker {}, error: {}",
                                        consumerGroup, entry.getValue(), e.getMessage());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void deleteConsumerGroup(String consumerGroup, String brokerAddr) throws Exception {
        mqAdminExt.deleteSubscriptionGroup(brokerAddr, consumerGroup);
    }

    @Override
    public ProducerConnection getProducerConnection(String producerGroup, String topic) throws Exception {
        return mqAdminExt.examineProducerConnectionInfo(producerGroup, topic);
    }

    @Override
    public QueryResult queryMessage(String topic, String key, long begin, long end, int maxNum) throws Exception {
        return mqAdminExt.queryMessage(topic, key, maxNum, begin, end);
    }

    @Override
    public MessageExt viewMessage(String topic, String msgId) throws Exception {
        return mqAdminExt.viewMessage(topic, msgId);
    }

    @Override
    public ConsumeMessageDirectlyResult consumeMessageDirectly(String consumerGroup, String topic, String msgId) throws Exception {
        return mqAdminExt.consumeMessageDirectly(consumerGroup, null, topic, msgId);
    }

    @Override
    public void replayMessage(String consumerGroup, String topic, String msgId) throws Exception {
        // Removed
        throw new UnsupportedOperationException("replayMessage not implemented yet");
    }

    @Override
    public KVTable getNameServerConfig(String namesrvAddr) throws Exception {
        Map<String, Properties> configMap = mqAdminExt.getNameServerConfig(java.util.Collections.singletonList(namesrvAddr));
        KVTable kvTable = new KVTable();
        if (configMap != null && !configMap.isEmpty()) {
            java.util.HashMap<String, String> merged = new java.util.HashMap<>();
            for (Properties props : configMap.values()) {
                for (String key : props.stringPropertyNames()) {
                    merged.put(key, props.getProperty(key));
                }
            }
            kvTable.setTable(merged);
        }
        return kvTable;
    }

    @Override
    public AccessControlList getAccessControlList(String brokerAddr) throws Exception {
        // Removed
        throw new UnsupportedOperationException("getAccessControlList not implemented yet");
    }

    @Override
    public void updateAccessControlList(String brokerAddr, AccessControlList acl) throws Exception {
        // Removed
        throw new UnsupportedOperationException("updateAccessControlList not implemented yet");
    }

    @Override
    public void shutdown() {
        if (mqAdminExt != null) {
            mqAdminExt.shutdown();
        }
    }
}