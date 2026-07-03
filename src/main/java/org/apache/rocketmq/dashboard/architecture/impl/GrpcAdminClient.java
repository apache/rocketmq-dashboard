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

import org.apache.rocketmq.dashboard.architecture.AdminClient;
import org.apache.rocketmq.dashboard.architecture.ClusterAccessType;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.body.ProducerConnection;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Properties;

/**
 * gRPC-based AdminClient implementation for RocketMQ 5.x Proxy Cluster mode.
 *
 * <p><b>IMPORTANT: Placeholder Framework Implementation</b></p>
 *
 * This class defines the skeleton structure for a gRPC-based admin client that would communicate
 * directly with the RocketMQ 5.x Proxy's Admin gRPC service interface. However, the RIP-2
 * Proxy Admin gRPC interfaces have not yet been merged into the main RocketMQ repository,
 * so <b>all method implementations currently throw {@link UnsupportedOperationException}</b>.
 *
 * <p>The purpose of this file is to:
 * <ul>
 *   <li>Maintain architectural completeness -- when {@code accessType == V5_PROXY_CLUSTER},
 *       the AdminClient factory can return a {@code GrpcAdminClient} instance rather than
 *       {@link RemotingAdminClient}, preventing NPE in type-dependent code paths.</li>
 *   <li>Provide a clear migration target -- once RIP-2 interfaces are available in the
 *       rocketmq-client-grpc package, each method body can be replaced with actual gRPC calls.</li>
 *   <li>Preserve exception handling patterns and logging conventions consistent with the
 *       rest of the dashboard architecture.</li>
 * </ul>
 *
 * <p><b>Usage guidance:</b></p>
 * <ul>
 *   <li>Until RIP-2 is merged, prefer {@link RemotingAdminClient} for actual operations
 *       through the Remoting channel (via MQAdminExt).</li>
 *   <li>This client is suitable for capability detection, cluster health checks, and
 *       topology queries (see {@link V5ProxyClusterProvider}).</li>
 * </ul>
 *
 * @see AdminClient
 * @see RemotingAdminClient
 * @see V5ProxyClusterProvider
 * @see <a href="https://github.com/apache/rocketmq/wiki/RIP-1-Control-Plane">RIP-1 Control Plane Specification</a>
 */
public class GrpcAdminClient implements AdminClient {

    private static final Logger log = LoggerFactory.getLogger(GrpcAdminClient.class);

    /**
     * Unsupported operation message shared across all methods.
     */
    private static final String UNAVAILABLE_MESSAGE =
        "RIP-2 Proxy Admin interface not yet available. Please use RemotingAdminClient "
        + "or wait for RIP-2 merge.";

    /**
     * Proxy server address this client targets.
     * Saved for future gRPC channel initialization.
     */
    private final String proxyAddress;

    /**
     * Placeholder reference to a gRPC ManagedChannel.
     * Not initialized until RIP-2 interfaces become available.
     */
    private volatile Object grpcChannel;

    /**
     * Whether the client has been shut down.
     */
    private volatile boolean shutdown = false;

    /**
     * Construct a new GrpcAdminClient targeting the given Proxy address.
     *
     * @param proxyAddress Proxy node address in format "host:port". Must not be null or empty.
     * @throws IllegalArgumentException if proxyAddress is empty or null
     */
    public GrpcAdminClient(String proxyAddress) {
        Assert.notNull(proxyAddress, "proxyAddress must not be null");
        Assert.notEmpty(proxyAddress.trim(), "proxyAddress must not be empty");
        this.proxyAddress = proxyAddress;
        log.info("GrpcAdminClient created for proxy: {}", proxyAddress);
    }

    @Override
    public ClusterAccessType getClientType() {
        return ClusterAccessType.V5_PROXY_CLUSTER;
    }

    // ==================== Cluster Operations ====================

    @Override
    public ClusterInfo getClusterInfo() throws Exception {
        logInfo("getClusterInfo");
        throw new UnsupportedOperationException(UNAVAILABLE_MESSAGE);
    }

    @Override
    public KVTable getBrokerRuntimeStats(String brokerAddr) throws Exception {
        logInfo("getBrokerRuntimeStats(brokerAddr=" + brokerAddr + ")");
        throw new UnsupportedOperationException(UNAVAILABLE_MESSAGE);
    }

    @Override
    public void updateBrokerConfig(String brokerAddr, Properties properties) throws Exception {
        logInfo("updateBrokerConfig(brokerAddr=" + brokerAddr + ")");
        throw new UnsupportedOperationException(UNAVAILABLE_MESSAGE);
    }

    // ==================== Topic Operations ====================

    @Override
    public List<String> getTopicList() throws Exception {
        logInfo("getTopicList");
        throw new UnsupportedOperationException(UNAVAILABLE_MESSAGE);
    }

    @Override
    public TopicRouteData getTopicRoute(String topic) throws Exception {
        logInfo("getTopicRoute(topic=" + topic + ")");
        throw new UnsupportedOperationException(UNAVAILABLE_MESSAGE);
    }

    @Override
    public TopicStatsTable getTopicStats(String topic) throws Exception {
        logInfo("getTopicStats(topic=" + topic + ")");
        throw new UnsupportedOperationException(UNAVAILABLE_MESSAGE);
    }

    @Override
    public void createOrUpdateTopic(String topic, TopicConfig topicConfig) throws Exception {
        logInfo("createOrUpdateTopic(topic=" + topic + ")");
        throw new UnsupportedOperationException(UNAVAILABLE_MESSAGE);
    }

    @Override
    public void deleteTopic(String topic, String clusterName) throws Exception {
        logInfo("deleteTopic(topic=" + topic + ", clusterName=" + clusterName + ")");
        throw new UnsupportedOperationException(UNAVAILABLE_MESSAGE);
    }

    @Override
    public TopicList getTopicListFromBroker(String brokerAddr) throws Exception {
        logInfo("getTopicListFromBroker(brokerAddr=" + brokerAddr + ")");
        throw new UnsupportedOperationException(UNAVAILABLE_MESSAGE);
    }

    // ==================== Consumer Group Operations ====================

    @Override
    public List<String> getConsumerGroupList() throws Exception {
        logInfo("getConsumerGroupList");
        throw new UnsupportedOperationException(UNAVAILABLE_MESSAGE);
    }

    @Override
    public ConsumerConnection getConsumerConnection(String consumerGroup) throws Exception {
        logInfo("getConsumerConnection(consumerGroup=" + consumerGroup + ")");
        throw new UnsupportedOperationException(UNAVAILABLE_MESSAGE);
    }

    @Override
    public GroupConsumeInfo getGroupConsumeInfo(String consumerGroup) throws Exception {
        logInfo("getGroupConsumeInfo(consumerGroup=" + consumerGroup + ")");
        throw new UnsupportedOperationException(UNAVAILABLE_MESSAGE);
    }

    @Override
    public void resetConsumeOffset(String consumerGroup, String topic, long timestamp, boolean force) throws Exception {
        logInfo("resetConsumeOffset(consumerGroup=" + consumerGroup + ", topic=" + topic + ")");
        throw new UnsupportedOperationException(UNAVAILABLE_MESSAGE);
    }

    @Override
    public void createOrUpdateConsumerGroup(String consumerGroup, SubscriptionGroupConfig config) throws Exception {
        logInfo("createOrUpdateConsumerGroup(consumerGroup=" + consumerGroup + ")");
        throw new UnsupportedOperationException(UNAVAILABLE_MESSAGE);
    }

    @Override
    public void deleteConsumerGroup(String consumerGroup, String brokerAddr) throws Exception {
        logInfo("deleteConsumerGroup(consumerGroup=" + consumerGroup + ")");
        throw new UnsupportedOperationException(UNAVAILABLE_MESSAGE);
    }

    // ==================== Producer & Message Operations ====================

    @Override
    public ProducerConnection getProducerConnection(String producerGroup, String topic) throws Exception {
        logInfo("getProducerConnection(producerGroup=" + producerGroup + ", topic=" + topic + ")");
        throw new UnsupportedOperationException(UNAVAILABLE_MESSAGE);
    }

    @Override
    public QueryResult queryMessage(String topic, String key, long begin, long end, int maxNum) throws Exception {
        logInfo("queryMessage(topic=" + topic + ", key=" + key + ")");
        throw new UnsupportedOperationException(UNAVAILABLE_MESSAGE);
    }

    @Override
    public MessageExt viewMessage(String topic, String msgId) throws Exception {
        logInfo("viewMessage(topic=" + topic + ", msgId=" + msgId + ")");
        throw new UnsupportedOperationException(UNAVAILABLE_MESSAGE);
    }

    @Override
    public ConsumeMessageDirectlyResult consumeMessageDirectly(String consumerGroup, String topic, String msgId) throws Exception {
        logInfo("consumeMessageDirectly(consumerGroup=" + consumerGroup + ", topic=" + topic + ")");
        throw new UnsupportedOperationException(UNAVAILABLE_MESSAGE);
    }

    @Override
    public void replayMessage(String consumerGroup, String topic, String msgId) throws Exception {
        logInfo("replayMessage(consumerGroup=" + consumerGroup + ", topic=" + topic + ")");
        throw new UnsupportedOperationException(UNAVAILABLE_MESSAGE);
    }

    // ==================== NameServer & ACL Operations ====================

    @Override
    public KVTable getNameServerConfig(String namesrvAddr) throws Exception {
        logInfo("getNameServerConfig(namesrvAddr=" + namesrvAddr + ")");
        throw new UnsupportedOperationException(UNAVAILABLE_MESSAGE);
    }

    @Override
    public AccessControlList getAccessControlList(String brokerAddr) throws Exception {
        logInfo("getAccessControlList(brokerAddr=" + brokerAddr + ")");
        throw new UnsupportedOperationException(UNAVAILABLE_MESSAGE);
    }

    @Override
    public void updateAccessControlList(String brokerAddr, AccessControlList acl) throws Exception {
        logInfo("updateAccessControlList(brokerAddr=" + brokerAddr + ")");
        throw new UnsupportedOperationException(UNAVAILABLE_MESSAGE);
    }

    // ==================== Lifecycle ====================

    @Override
    public void shutdown() {
        if (shutdown) {
            return;
        }
        log.info("Shutting down GrpcAdminClient for proxy: {}", proxyAddress);
        shutdown = true;

        // Clean up gRPC channel resources
        // TODO: When RIP-2 is merged, properly close the ManagedChannel:
        // if (grpcChannel instanceof io.grpc.ManagedChannel) {
        //     ((io.grpc.ManagedChannel) grpcChannel).shutdown();
        // }
        this.grpcChannel = null;

        log.info("GrpcAdminClient shutdown complete for proxy: {}", proxyAddress);
    }

    // ==================== Private helpers ====================

    /**
     * Log an INFO-level message indicating that a method was called but is not yet implemented.
     * Includes the calling method name and targeted proxy address.
     *
     * @param methodName the name of the unimplemented method
     */
    private void logInfo(String methodName) {
        log.info("[GrpcAdminClient][PLACEHOLDER] {} called for proxy [{}]. {} Returning UnsupportedOperationException.",
            methodName, proxyAddress, UNAVAILABLE_MESSAGE);
    }
}
