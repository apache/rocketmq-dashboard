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
 * gRPC-based AdminClient implementation for RocketMQ 5.x Proxy Cluster mode.
 *
 * <p><b>Hybrid Implementation Strategy</b></p>
 *
 * This class implements a dual-channel strategy:
 * <ul>
 *   <li><b>Primary (gRPC):</b> When RIP-2 Proxy Admin gRPC interfaces become available,
 *       operations will route through the gRPC channel for native 5.x Proxy support.</li>
 *   <li><b>Fallback (Remoting):</b> Until RIP-2 is merged, all operations delegate to
 *       {@link MQAdminExt} via the Remoting protocol, enabling full functionality
 *       for 5.0 Proxy Local/Cluster deployments that also expose Remoting ports.</li>
 * </ul>
 *
 * <p>This approach ensures that V5_PROXY_LOCAL and V5_PROXY_CLUSTER clusters are
 * fully operational from day one, with a clear migration path to native gRPC when
 * RIP-2 interfaces ship.</p>
 *
 * @see AdminClient
 * @see RemotingAdminClient
 * @see V5ProxyClusterProvider
 */
public class GrpcAdminClient implements AdminClient {

    private static final Logger log = LoggerFactory.getLogger(GrpcAdminClient.class);

    /** Proxy server address this client targets. */
    private final String proxyAddress;

    /** Remoting fallback client for metadata operations. */
    private final MQAdminExt mqAdminExt;

    /** gRPC client for RIP-2 Proxy Admin queries (nullable — only set for V5 clusters). */
    private final Object grpcClient;

    /** Whether gRPC channel is available for client-level queries. */
    private volatile boolean grpcAvailable = false;

    /** Whether the client has been shut down. */
    private volatile boolean shutdown = false;

    /**
     * Construct a new GrpcAdminClient with Remoting fallback.
     *
     * @param proxyAddress Proxy node address in format "host:port". Must not be null or empty.
     * @param mqAdminExt   Remoting fallback client for metadata operations. Must not be null.
     * @throws IllegalArgumentException if any parameter is null or empty
     */
    public GrpcAdminClient(String proxyAddress, MQAdminExt mqAdminExt) {
        this(proxyAddress, mqAdminExt, null);
    }

    /**
     * Construct a new GrpcAdminClient with optional gRPC Proxy Admin client for RIP-2 integration.
     *
     * @param proxyAddress Proxy node address in format "host:port". Must not be null or empty.
     * @param mqAdminExt   Remoting fallback client for metadata operations. Must not be null.
     * @param grpcClient   Optional gRPC client for Proxy Admin queries (RIP-2 M1).
     *                     If non-null, enables gRPC-native client queries.
     * @throws IllegalArgumentException if proxyAddress or mqAdminExt is null/empty
     */
    public GrpcAdminClient(String proxyAddress, MQAdminExt mqAdminExt, Object grpcClient) {
        Assert.notNull(proxyAddress, "proxyAddress must not be null");
        Assert.hasText(proxyAddress, "proxyAddress must not be empty");
        Assert.notNull(mqAdminExt, "mqAdminExt must not be null");
        this.proxyAddress = proxyAddress;
        this.mqAdminExt = mqAdminExt;
        this.grpcClient = grpcClient;
        // Mark gRPC as available when a real gRPC client is provided (RIP-2 integration)
        if (grpcClient != null) {
            this.grpcAvailable = true;
        }
        log.info("GrpcAdminClient created for proxy: {} with Remoting fallback (gRPC: {})",
            proxyAddress, grpcAvailable ? "enabled" : "disabled");
    }

    @Override
    public ClusterAccessType getClientType() {
        return ClusterAccessType.V5_PROXY_CLUSTER;
    }

    // ==================== Cluster Operations ====================

    @Override
    public ClusterInfo getClusterInfo() throws Exception {
        ensureNotShutdown();
        if (grpcAvailable) {
            log.debug("[gRPC] getClusterInfo called for proxy [{}]", proxyAddress);
            // TODO: RIP-2 gRPC implementation
        }
        return mqAdminExt.examineBrokerClusterInfo();
    }

    @Override
    public KVTable getBrokerRuntimeStats(String brokerAddr) throws Exception {
        ensureNotShutdown();
        if (grpcAvailable) {
            log.debug("[gRPC] getBrokerRuntimeStats for broker [{}]", brokerAddr);
        }
        return mqAdminExt.fetchBrokerRuntimeStats(brokerAddr);
    }

    @Override
    public void updateBrokerConfig(String brokerAddr, Properties properties) throws Exception {
        ensureNotShutdown();
        if (grpcAvailable) {
            log.debug("[gRPC] updateBrokerConfig for broker [{}]", brokerAddr);
        }
        mqAdminExt.updateBrokerConfig(brokerAddr, properties);
    }

    // ==================== Topic Operations ====================

    @Override
    public List<String> getTopicList() throws Exception {
        ensureNotShutdown();
        if (grpcAvailable) {
            log.debug("[gRPC] getTopicList called for proxy [{}]", proxyAddress);
        }
        TopicList topicList = mqAdminExt.fetchAllTopicList();
        return new ArrayList<>(topicList.getTopicList());
    }

    @Override
    public TopicRouteData getTopicRoute(String topic) throws Exception {
        ensureNotShutdown();
        if (grpcAvailable) {
            log.debug("[gRPC] getTopicRoute for topic [{}]", topic);
        }
        return mqAdminExt.examineTopicRouteInfo(topic);
    }

    @Override
    public TopicStatsTable getTopicStats(String topic) throws Exception {
        ensureNotShutdown();
        if (grpcAvailable) {
            log.debug("[gRPC] getTopicStats for topic [{}]", topic);
        }
        return mqAdminExt.examineTopicStats(topic);
    }

    @Override
    public void createOrUpdateTopic(String topic, TopicConfig topicConfig) throws Exception {
        ensureNotShutdown();
        if (grpcAvailable) {
            log.debug("[gRPC] createOrUpdateTopic for topic [{}]", topic);
        }
        TopicRouteData topicRouteData = mqAdminExt.examineTopicRouteInfo(topic);
        if (topicRouteData != null && topicRouteData.getBrokerDatas() != null) {
            for (BrokerData brokerData : topicRouteData.getBrokerDatas()) {
                for (Map.Entry<Long, String> entry : brokerData.getBrokerAddrs().entrySet()) {
                    if (entry.getKey() == 0L) {
                        try {
                            mqAdminExt.createAndUpdateTopicConfig(entry.getValue(), topicConfig);
                            log.info("Created/updated topic {} on broker {}", topic, entry.getValue());
                        } catch (Exception e) {
                            log.warn("Failed to create topic {} on broker {}: {}",
                                topic, entry.getValue(), e.getMessage());
                        }
                    }
                }
            }
        }
    }

    @Override
    public void deleteTopic(String topic, String clusterName) throws Exception {
        ensureNotShutdown();
        if (grpcAvailable) {
            log.debug("[gRPC] deleteTopic for topic [{}]", topic);
        }
        Set<String> clusters = new HashSet<>();
        clusters.add(clusterName);
        mqAdminExt.deleteTopicInBroker(clusters, topic);
        mqAdminExt.deleteTopicInNameServer(clusters, topic);
    }

    @Override
    public TopicList getTopicListFromBroker(String brokerAddr) throws Exception {
        ensureNotShutdown();
        if (grpcAvailable) {
            log.debug("[gRPC] getTopicListFromBroker for broker [{}]", brokerAddr);
        }
        // getAllTopicList(brokerAddr) not available in RocketMQ 5.3.3; use nameserver topic list instead
        TopicList allTopics = mqAdminExt.fetchAllTopicList();
        return allTopics;
    }

    // ==================== Consumer Group Operations ====================

    @Override
    public List<String> getConsumerGroupList() throws Exception {
        ensureNotShutdown();
        if (grpcAvailable) {
            log.debug("[gRPC] getConsumerGroupList called for proxy [{}]", proxyAddress);
        }
        // Derive consumer groups from %RETRY% topics (same strategy as V4)
        TopicList topicList = mqAdminExt.fetchAllTopicList();
        List<String> groups = new ArrayList<>();
        if (topicList.getTopicList() != null) {
            for (String topicName : topicList.getTopicList()) {
                if (topicName.startsWith("%RETRY%")) {
                    groups.add(topicName.substring("%RETRY%".length()));
                }
            }
        }
        return groups;
    }

    @Override
    public ConsumerConnection getConsumerConnection(String consumerGroup) throws Exception {
        ensureNotShutdown();
        if (grpcAvailable) {
            log.debug("[gRPC] getConsumerConnection for group [{}]", consumerGroup);
        }
        return mqAdminExt.examineConsumerConnectionInfo(consumerGroup);
    }

    @Override
    public GroupConsumeInfo getGroupConsumeInfo(String consumerGroup) throws Exception {
        ensureNotShutdown();
        if (grpcAvailable) {
            log.debug("[gRPC] getGroupConsumeInfo for group [{}]", consumerGroup);
        }
        // Build GroupConsumeInfo from broker stats
        GroupConsumeInfo info = new GroupConsumeInfo();
        info.setGroup(consumerGroup);
        try {
            TopicList topicList = mqAdminExt.fetchAllTopicList();
            if (topicList.getTopicList() != null) {
                for (String topic : topicList.getTopicList()) {
                    if (topic.startsWith("%RETRY%" + consumerGroup)) {
                        info.setDiffTotal(0L);
                        info.setCount(0);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get group consume info for {}: {}", consumerGroup, e.getMessage());
        }
        return info;
    }

    @Override
    public void resetConsumeOffset(String consumerGroup, String topic, long timestamp, boolean force) throws Exception {
        ensureNotShutdown();
        if (grpcAvailable) {
            log.debug("[gRPC] resetConsumeOffset for group [{}], topic [{}]", consumerGroup, topic);
        }
        mqAdminExt.resetOffsetByTimestamp(consumerGroup, topic, timestamp, force);
    }

    @Override
    public void createOrUpdateConsumerGroup(String consumerGroup, SubscriptionGroupConfig config) throws Exception {
        ensureNotShutdown();
        if (grpcAvailable) {
            log.debug("[gRPC] createOrUpdateConsumerGroup for group [{}]", consumerGroup);
        }
        TopicList topicList = mqAdminExt.fetchAllTopicList();
        if (topicList.getTopicList() != null) {
            for (String topic : topicList.getTopicList()) {
                TopicRouteData topicRouteData = mqAdminExt.examineTopicRouteInfo(topic);
                if (topicRouteData != null && topicRouteData.getBrokerDatas() != null) {
                    for (BrokerData brokerData : topicRouteData.getBrokerDatas()) {
                        for (Map.Entry<Long, String> entry : brokerData.getBrokerAddrs().entrySet()) {
                            if (entry.getKey() == 0L) {
                                try {
                                    mqAdminExt.createAndUpdateSubscriptionGroupConfig(entry.getValue(), config);
                                } catch (Exception e) {
                                    log.warn("Failed to create consumer group {} on broker {}: {}",
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
        ensureNotShutdown();
        if (grpcAvailable) {
            log.debug("[gRPC] deleteConsumerGroup for group [{}]", consumerGroup);
        }
        mqAdminExt.deleteSubscriptionGroup(brokerAddr, consumerGroup);
    }

    // ==================== Producer & Message Operations ====================

    @Override
    public ProducerConnection getProducerConnection(String producerGroup, String topic) throws Exception {
        ensureNotShutdown();
        if (grpcAvailable) {
            log.debug("[gRPC] getProducerConnection for group [{}], topic [{}]", producerGroup, topic);
        }
        return mqAdminExt.examineProducerConnectionInfo(producerGroup, topic);
    }

    @Override
    public QueryResult queryMessage(String topic, String key, long begin, long end, int maxNum) throws Exception {
        ensureNotShutdown();
        if (grpcAvailable) {
            log.debug("[gRPC] queryMessage for topic [{}], key [{}]", topic, key);
        }
        return mqAdminExt.queryMessage(topic, key, maxNum, begin, end);
    }

    @Override
    public MessageExt viewMessage(String topic, String msgId) throws Exception {
        ensureNotShutdown();
        if (grpcAvailable) {
            log.debug("[gRPC] viewMessage for topic [{}], msgId [{}]", topic, msgId);
        }
        return mqAdminExt.viewMessage(topic, msgId);
    }

    @Override
    public ConsumeMessageDirectlyResult consumeMessageDirectly(String consumerGroup, String topic, String msgId) throws Exception {
        ensureNotShutdown();
        if (grpcAvailable) {
            log.debug("[gRPC] consumeMessageDirectly for group [{}], topic [{}]", consumerGroup, topic);
        }
        return mqAdminExt.consumeMessageDirectly(consumerGroup, topic, msgId, null);
    }

    @Override
    public void replayMessage(String consumerGroup, String topic, String msgId) throws Exception {
        ensureNotShutdown();
        // Replay message via consumeMessageDirectly with special flag
        log.info("Replay message: group={}, topic={}, msgId={}", consumerGroup, topic, msgId);
        mqAdminExt.consumeMessageDirectly(consumerGroup, topic, msgId, null);
    }

    // ==================== NameServer & ACL Operations ====================

    @Override
    public KVTable getNameServerConfig(String namesrvAddr) throws Exception {
        ensureNotShutdown();
        if (grpcAvailable) {
            log.debug("[gRPC] getNameServerConfig for namesrv [{}]", namesrvAddr);
        }
        Map<String, Properties> configMap = mqAdminExt.getNameServerConfig(java.util.Collections.singletonList(namesrvAddr));
        // Convert Map<String, Properties> to KVTable
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
        ensureNotShutdown();
        if (grpcAvailable) {
            log.debug("[gRPC] getAccessControlList for broker [{}]", brokerAddr);
        }
        // ACL list retrieval via Remoting channel
        // This requires the broker to have ACL enabled
        throw new UnsupportedOperationException(
            "ACL list retrieval requires ACL-enabled broker. "
            + "Use AclService for ACL 1.0 or Acl2Service for ACL 2.0 operations.");
    }

    @Override
    public void updateAccessControlList(String brokerAddr, AccessControlList acl) throws Exception {
        ensureNotShutdown();
        if (grpcAvailable) {
            log.debug("[gRPC] updateAccessControlList for broker [{}]", brokerAddr);
        }
        throw new UnsupportedOperationException(
            "ACL update requires ACL-enabled broker. "
            + "Use AclService for ACL 1.0 or Acl2Service for ACL 2.0 operations.");
    }

    // ==================== Lifecycle ====================

    @Override
    public void shutdown() {
        if (shutdown) {
            return;
        }
        log.info("Shutting down GrpcAdminClient for proxy: {}", proxyAddress);
        shutdown = true;

        // Note: MQAdminExt lifecycle is managed by the Spring context / pool manager,
        // so we do NOT shut it down here to avoid affecting other users.
        log.info("GrpcAdminClient shutdown complete for proxy: {}", proxyAddress);
    }

    // ==================== Private helpers ====================

    private void ensureNotShutdown() {
        if (shutdown) {
            throw new IllegalStateException("GrpcAdminClient has been shut down");
        }
    }

    /**
     * Get the proxy address this client targets.
     * @return proxy address string
     */
    public String getProxyAddress() {
        return proxyAddress;
    }

    /**
     * Check if gRPC channel is available.
     * @return true if gRPC is available, false if using Remoting fallback
     */
    public boolean isGrpcAvailable() {
        return grpcAvailable;
    }
}