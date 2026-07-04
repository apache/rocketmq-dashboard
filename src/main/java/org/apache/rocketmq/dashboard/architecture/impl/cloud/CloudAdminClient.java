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

import org.apache.rocketmq.client.QueryResult;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.dashboard.architecture.AdminClient;
import org.apache.rocketmq.dashboard.architecture.ClusterAccessType;
import org.apache.rocketmq.dashboard.model.AccessControlList;
import org.apache.rocketmq.dashboard.model.CloudProviderConfig;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Cloud AdminClient implementation that delegates to cloud provider OpenAPI.
 *
 * <p>Per RIP-1 ARCH-01 §5.1 M1, cloud-hosted RocketMQ instances do not expose
 * Remoting or gRPC protocols directly. Instead, management operations are
 * performed via cloud provider-specific OpenAPI (e.g., Aliyun ONS API,
 * Tencent Cloud TDMQ API, Huawei Cloud DMS API).</p>
 *
 * <h3>Design Rationale</h3>
 * <p>Unlike V4 Remoting or V5 gRPC AdminClients that directly communicate
 * with brokers/namesrv/proxy, the cloud AdminClient provides a best-effort
 * mapping of the standard {@link AdminClient} SPI interface to cloud OpenAPI
 * calls. Operations that require direct broker access throw
 * {@link UnsupportedOperationException}.</p>
 *
 * <h3>Unsupported Operations</h3>
 * <p>Operations requiring direct broker/namesrv access are not available
 * in cloud environments and throw {@link UnsupportedOperationException}:</p>
 * <ul>
 *   <li>Broker runtime stats and config management</li>
 *   <li>NameServer config management</li>
 *   <li>Direct message consumption/replay</li>
 *   <li>Producer connection inspection</li>
 * </ul>
 */
public class CloudAdminClient implements AdminClient {

    private static final Logger log = LoggerFactory.getLogger(CloudAdminClient.class);

    /** Cloud provider configuration. */
    private final CloudProviderConfig config;

    /** Whether this client has been initialized. */
    private volatile boolean initialized = false;

    public CloudAdminClient(CloudProviderConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("CloudProviderConfig must not be null");
        }
        this.config = config;
    }

    /**
     * Initialize the cloud admin client.
     * Not part of the AdminClient interface - called explicitly during setup.
     */
    public void initialize() {
        if (initialized) {
            log.warn("CloudAdminClient already initialized for instance: {}", config.getInstanceId());
            return;
        }
        log.info("Initializing CloudAdminClient for provider: {}, instance: {}",
            config.getProviderType(), config.getInstanceId());
        initialized = true;
    }

    @Override
    public void shutdown() {
        log.info("Shutting down CloudAdminClient for instance: {}", config.getInstanceId());
        initialized = false;
    }

    @Override
    public ClusterAccessType getClientType() {
        return ClusterAccessType.fromValue(config.getProviderType());
    }

    // ==================== Cluster Operations ====================

    @Override
    public ClusterInfo getClusterInfo() throws Exception {
        checkInitialized();
        log.debug("[Cloud] Getting cluster info for instance: {}", config.getInstanceId());
        // Cloud environments don't expose raw cluster topology
        // Return an empty ClusterInfo - the actual topology is managed by the cloud provider
        return new ClusterInfo();
    }

    // ==================== Broker Operations (Unsupported) ====================

    @Override
    public KVTable getBrokerRuntimeStats(String brokerAddr) throws Exception {
        throw new UnsupportedOperationException(
            "Broker runtime stats not available in cloud environment. Use cloud monitoring console instead.");
    }

    @Override
    public void updateBrokerConfig(String brokerAddr, Properties properties) throws Exception {
        throw new UnsupportedOperationException(
            "Broker config update not available in cloud environment. Use cloud console instead.");
    }

    // ==================== Topic Operations ====================

    @Override
    public List<String> getTopicList() throws Exception {
        checkInitialized();
        log.debug("[Cloud] Getting topic list for instance: {}", config.getInstanceId());
        // TODO: Delegate to cloud MetadataProvider when wired
        return Collections.emptyList();
    }

    @Override
    public TopicRouteData getTopicRoute(String topic) throws Exception {
        checkInitialized();
        log.debug("[Cloud] Getting topic route: {} for instance: {}", topic, config.getInstanceId());
        // Cloud environments manage routing internally
        return new TopicRouteData();
    }

    @Override
    public TopicStatsTable getTopicStats(String topic) throws Exception {
        checkInitialized();
        log.debug("[Cloud] Getting topic stats: {} for instance: {}", topic, config.getInstanceId());
        // Cloud APIs may not expose raw topic stats
        return new TopicStatsTable();
    }

    @Override
    public void createOrUpdateTopic(String topic, TopicConfig topicConfig) throws Exception {
        checkInitialized();
        log.debug("[Cloud] Creating/updating topic: {} for instance: {}", topic, config.getInstanceId());
        // TODO: Delegate to cloud MetadataProvider.createTopic()
        throw new UnsupportedOperationException(
            "Topic creation via cloud API is not yet implemented. Use cloud console.");
    }

    @Override
    public void deleteTopic(String topic, String clusterName) throws Exception {
        checkInitialized();
        log.debug("[Cloud] Deleting topic: {} for instance: {}", topic, config.getInstanceId());
        // TODO: Delegate to cloud MetadataProvider.deleteTopic()
        throw new UnsupportedOperationException(
            "Topic deletion via cloud API is not yet implemented. Use cloud console.");
    }

    @Override
    public TopicList getTopicListFromBroker(String brokerAddr) throws Exception {
        throw new UnsupportedOperationException(
            "Direct broker topic list not available in cloud environment.");
    }

    // ==================== Consumer Group Operations ====================

    @Override
    public List<String> getConsumerGroupList() throws Exception {
        checkInitialized();
        log.debug("[Cloud] Getting consumer group list for instance: {}", config.getInstanceId());
        // TODO: Delegate to cloud MetadataProvider.listConsumerGroups()
        return Collections.emptyList();
    }

    @Override
    public ConsumerConnection getConsumerConnection(String consumerGroup) throws Exception {
        checkInitialized();
        log.debug("[Cloud] Getting consumer connection: {} for instance: {}", consumerGroup, config.getInstanceId());
        // Cloud environments may not expose raw consumer connections
        return new ConsumerConnection();
    }

    @Override
    public GroupConsumeInfo getGroupConsumeInfo(String consumerGroup) throws Exception {
        checkInitialized();
        log.debug("[Cloud] Getting group consume info: {} for instance: {}", consumerGroup, config.getInstanceId());
        // TODO: Map cloud API response to GroupConsumeInfo
        return new GroupConsumeInfo();
    }

    @Override
    public void resetConsumeOffset(String consumerGroup, String topic, long timestamp, boolean force) throws Exception {
        checkInitialized();
        log.debug("[Cloud] Resetting consume offset: group={}, topic={} for instance: {}",
            consumerGroup, topic, config.getInstanceId());
        // TODO: Delegate to cloud API for offset reset
        throw new UnsupportedOperationException(
            "Consume offset reset via cloud API is not yet implemented. Use cloud console.");
    }

    @Override
    public void createOrUpdateConsumerGroup(String consumerGroup, SubscriptionGroupConfig config) throws Exception {
        checkInitialized();
        log.debug("[Cloud] Creating/updating consumer group: {} for instance: {}", consumerGroup, config.getGroupName());
        throw new UnsupportedOperationException(
            "Consumer group creation via cloud API is not yet implemented. Use cloud console.");
    }

    @Override
    public void deleteConsumerGroup(String consumerGroup, String brokerAddr) throws Exception {
        checkInitialized();
        log.debug("[Cloud] Deleting consumer group: {} for instance: {}", consumerGroup, config.getInstanceId());
        throw new UnsupportedOperationException(
            "Consumer group deletion via cloud API is not yet implemented. Use cloud console.");
    }

    // ==================== Producer Operations (Unsupported) ====================

    @Override
    public ProducerConnection getProducerConnection(String producerGroup, String topic) throws Exception {
        throw new UnsupportedOperationException(
            "Producer connection inspection not available in cloud environment.");
    }

    // ==================== Message Operations ====================

    @Override
    public QueryResult queryMessage(String topic, String key, long begin, long end, int maxNum) throws Exception {
        checkInitialized();
        log.debug("[Cloud] Querying message: topic={}, key={} for instance: {}", topic, key, config.getInstanceId());
        // TODO: Delegate to cloud MetadataProvider.queryMessagesByTopic()
        throw new UnsupportedOperationException(
            "Message query via cloud API is not yet implemented. Use cloud console.");
    }

    @Override
    public MessageExt viewMessage(String topic, String msgId) throws Exception {
        checkInitialized();
        log.debug("[Cloud] Viewing message: topic={}, msgId={} for instance: {}", topic, msgId, config.getInstanceId());
        // TODO: Delegate to cloud MetadataProvider.viewMessage()
        throw new UnsupportedOperationException(
            "Message view via cloud API is not yet implemented. Use cloud console.");
    }

    @Override
    public ConsumeMessageDirectlyResult consumeMessageDirectly(String consumerGroup, String topic, String msgId) throws Exception {
        throw new UnsupportedOperationException(
            "Direct message consumption not available in cloud environment.");
    }

    @Override
    public void replayMessage(String consumerGroup, String topic, String msgId) throws Exception {
        throw new UnsupportedOperationException(
            "Message replay not available in cloud environment.");
    }

    // ==================== NameServer Operations (Unsupported) ====================

    @Override
    public KVTable getNameServerConfig(String namesrvAddr) throws Exception {
        throw new UnsupportedOperationException(
            "NameServer config not available in cloud environment.");
    }

    // ==================== ACL Operations ====================

    @Override
    public AccessControlList getAccessControlList(String brokerAddr) throws Exception {
        checkInitialized();
        log.debug("[Cloud] Getting ACL for instance: {}", config.getInstanceId());
        // Cloud providers typically use cloud IAM, not RocketMQ ACL
        // Return an empty ACL list
        return new AccessControlList();
    }

    @Override
    public void updateAccessControlList(String brokerAddr, AccessControlList acl) throws Exception {
        throw new UnsupportedOperationException(
            "ACL update not available in cloud environment. Use cloud IAM instead.");
    }

    // ==================== Helper Methods ====================

    private void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException(
                "CloudAdminClient not initialized. Call initialize() first.");
        }
    }

    /**
     * Get the cloud provider configuration.
     */
    public CloudProviderConfig getConfig() {
        return config;
    }
}