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
package org.apache.rocketmq.dashboard.cli.context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.acl.common.AclClientRPCHook;
import org.apache.rocketmq.acl.common.SessionCredentials;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.dashboard.cli.RmqctlCommand;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper that encapsulates MQAdminExt connection lifecycle and provides
 * convenience methods for common RocketMQ admin operations.
 *
 * <p>Usage:</p>
 * <pre>
 * try (AdminClientHelper admin = AdminClientHelper.connect(clusterName, root)) {
 *     admin.createTopicOnAllBrokers(topicConfig);
 * }
 * </pre>
 */
public class AdminClientHelper implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AdminClientHelper.class);

    private final MQAdminExt mqAdminExt;
    private final String clusterName;
    private final String namesrvAddr;
    private volatile ClusterInfo cachedClusterInfo;

    private AdminClientHelper(MQAdminExt mqAdminExt, String clusterName, String namesrvAddr) {
        this.mqAdminExt = mqAdminExt;
        this.clusterName = clusterName;
        this.namesrvAddr = namesrvAddr;
    }

    /**
     * Create an AdminClientHelper connected to the specified cluster.
     * Resolves cluster name from: local option > root --cluster > current context.
     * Handles ACL credentials and connection lifecycle.
     *
     * @param clusterName cluster name (if null, uses current context)
     * @param root        root command (for --cluster override)
     * @return connected AdminClientHelper (caller must close)
     */
    public static AdminClientHelper connect(String clusterName, RmqctlCommand root) throws Exception {
        CliContext ctx = new CliContext();

        // Resolve cluster name: local option > root --cluster > current context
        String resolvedName = clusterName;
        if (resolvedName == null && root != null && root.getCluster() != null) {
            resolvedName = root.getCluster();
        }
        if (resolvedName == null) {
            CliConfig.ContextEntry contextEntry = ctx.resolveCurrentContext();
            if (contextEntry != null) {
                resolvedName = contextEntry.getCluster();
            }
        }
        if (resolvedName == null) {
            throw new IllegalStateException("No cluster specified and no current context set. Use 'rmqctl config use-context' or --cluster.");
        }

        // Get cluster configuration
        CliConfig.ClusterEntry cluster = ctx.getConfig().getClusters().get(resolvedName);
        if (cluster == null) {
            throw new IllegalStateException("Cluster '" + resolvedName + "' not found in configuration. Use 'rmqctl config add-cluster'.");
        }

        String nsAddr = cluster.getNamesrvAddr();
        if (StringUtils.isEmpty(nsAddr)) {
            throw new IllegalStateException("Cluster '" + resolvedName + "' has no namesrvAddr configured.");
        }

        // Resolve user credentials
        String userRef = null;
        CliConfig.ContextEntry contextEntry = ctx.resolveCurrentContext();
        if (contextEntry != null && resolvedName.equals(contextEntry.getCluster())) {
            userRef = contextEntry.getUser();
        }

        RPCHook rpcHook = null;
        if (userRef != null) {
            CliConfig.UserEntry user = ctx.getConfig().getUsers().get(userRef);
            if (user != null && StringUtils.isNotEmpty(user.getAccessKey()) && StringUtils.isNotEmpty(user.getSecretKey())) {
                rpcHook = new AclClientRPCHook(new SessionCredentials(user.getAccessKey(), user.getSecretKey()));
            }
        }

        // Create and start MQAdminExt
        DefaultMQAdminExt mqAdminExt = new DefaultMQAdminExt(rpcHook, 10000);
        mqAdminExt.setAdminExtGroup("rmqctl_cli_" + System.currentTimeMillis());
        mqAdminExt.setVipChannelEnabled(false);
        mqAdminExt.setNamesrvAddr(nsAddr);
        mqAdminExt.start();

        System.out.println("Connected to cluster: " + resolvedName + " (" + nsAddr + ")");
        return new AdminClientHelper(mqAdminExt, resolvedName, nsAddr);
    }

    /**
     * Create a raw MQAdminExt connection (for backward compatibility).
     * Caller is responsible for calling shutdown().
     *
     * @param clusterName cluster name (if null, uses current context)
     * @param root        root command (for --cluster override)
     * @return started MQAdminExt instance (caller must shutdown)
     */
    public static MQAdminExt connectRaw(String clusterName, RmqctlCommand root) throws Exception {
        CliContext ctx = new CliContext();

        String resolvedName = clusterName;
        if (resolvedName == null && root != null && root.getCluster() != null) {
            resolvedName = root.getCluster();
        }
        if (resolvedName == null) {
            CliConfig.ContextEntry contextEntry = ctx.resolveCurrentContext();
            if (contextEntry != null) {
                resolvedName = contextEntry.getCluster();
            }
        }
        if (resolvedName == null) {
            throw new IllegalStateException("No cluster specified and no current context set. Use 'rmqctl config use-context' or --cluster.");
        }

        CliConfig.ClusterEntry cluster = ctx.getConfig().getClusters().get(resolvedName);
        if (cluster == null) {
            throw new IllegalStateException("Cluster '" + resolvedName + "' not found in configuration. Use 'rmqctl config add-cluster'.");
        }

        String nsAddr = cluster.getNamesrvAddr();
        if (StringUtils.isEmpty(nsAddr)) {
            throw new IllegalStateException("Cluster '" + resolvedName + "' has no namesrvAddr configured.");
        }

        String userRef = null;
        CliConfig.ContextEntry contextEntry = ctx.resolveCurrentContext();
        if (contextEntry != null && resolvedName.equals(contextEntry.getCluster())) {
            userRef = contextEntry.getUser();
        }

        RPCHook rpcHook = null;
        if (userRef != null) {
            CliConfig.UserEntry user = ctx.getConfig().getUsers().get(userRef);
            if (user != null && StringUtils.isNotEmpty(user.getAccessKey()) && StringUtils.isNotEmpty(user.getSecretKey())) {
                rpcHook = new AclClientRPCHook(new SessionCredentials(user.getAccessKey(), user.getSecretKey()));
            }
        }

        DefaultMQAdminExt mqAdminExt = new DefaultMQAdminExt(rpcHook, 10000);
        mqAdminExt.setAdminExtGroup("rmqctl_cli_" + System.currentTimeMillis());
        mqAdminExt.setVipChannelEnabled(false);
        mqAdminExt.setNamesrvAddr(nsAddr);
        mqAdminExt.start();

        System.out.println("Connected to cluster: " + resolvedName + " (" + nsAddr + ")");
        return mqAdminExt;
    }

    /**
     * Resolve cluster name from local option or root --cluster.
     */
    public static String resolveClusterName(String localCluster, RmqctlCommand root) {
        if (localCluster != null) {
            return localCluster;
        }
        if (root != null && root.getCluster() != null) {
            return root.getCluster();
        }
        return null;
    }

    // ==================== Lifecycle ====================

    @Override
    public void close() {
        if (mqAdminExt != null) {
            mqAdminExt.shutdown();
        }
    }

    /**
     * Get the underlying MQAdminExt for advanced operations.
     */
    public MQAdminExt getMqAdminExt() {
        return mqAdminExt;
    }

    /**
     * Get the resolved cluster name.
     */
    public String getClusterName() {
        return clusterName;
    }

    /**
     * Get the NameServer address for this cluster.
     */
    public String getNamesrvAddr() {
        return namesrvAddr;
    }

    // ==================== Cluster Info ====================

    /**
     * Get cluster info (cached for the lifetime of this helper).
     */
    public ClusterInfo getClusterInfo() throws Exception {
        if (cachedClusterInfo == null) {
            cachedClusterInfo = mqAdminExt.examineBrokerClusterInfo();
        }
        return cachedClusterInfo;
    }

    /**
     * Collect all master broker addresses from the cluster.
     */
    public List<String> getMasterBrokerAddresses() throws Exception {
        List<String> addrs = new ArrayList<>();
        ClusterInfo clusterInfo = getClusterInfo();
        if (clusterInfo != null && clusterInfo.getBrokerAddrTable() != null) {
            for (Map.Entry<String, BrokerData> entry : clusterInfo.getBrokerAddrTable().entrySet()) {
                BrokerData bd = entry.getValue();
                HashMap<Long, String> brokerAddrs = bd.getBrokerAddrs();
                if (brokerAddrs != null) {
                    String masterAddr = brokerAddrs.get(0L);
                    if (masterAddr != null) {
                        addrs.add(masterAddr);
                    }
                }
            }
        }
        return addrs;
    }

    /**
     * Collect all broker addresses (master + slave) from the cluster.
     */
    public Set<String> getAllBrokerAddresses() throws Exception {
        Set<String> addrs = new HashSet<>();
        ClusterInfo clusterInfo = getClusterInfo();
        if (clusterInfo != null && clusterInfo.getBrokerAddrTable() != null) {
            for (BrokerData bd : clusterInfo.getBrokerAddrTable().values()) {
                HashMap<Long, String> brokerAddrs = bd.getBrokerAddrs();
                if (brokerAddrs != null) {
                    addrs.addAll(brokerAddrs.values());
                }
            }
        }
        return addrs;
    }

    /**
     * Collect all broker names from the cluster.
     */
    public List<String> getBrokerNames() throws Exception {
        List<String> names = new ArrayList<>();
        ClusterInfo clusterInfo = getClusterInfo();
        if (clusterInfo != null && clusterInfo.getBrokerAddrTable() != null) {
            names.addAll(clusterInfo.getBrokerAddrTable().keySet());
        }
        return names;
    }

    // ==================== Topic Operations ====================

    /**
     * Create or update a topic on all master brokers.
     *
     * @param topicConfig topic configuration
     * @return number of brokers the topic was created/updated on
     */
    public int createTopicOnAllBrokers(TopicConfig topicConfig) throws Exception {
        List<String> masterAddrs = getMasterBrokerAddresses();
        if (masterAddrs.isEmpty()) {
            throw new IllegalStateException("No master brokers found in the cluster.");
        }

        int successCount = 0;
        Exception lastError = null;
        for (String addr : masterAddrs) {
            try {
                mqAdminExt.createAndUpdateTopicConfig(addr, topicConfig);
                successCount++;
            } catch (Exception e) {
                log.warn("Failed to create/update topic {} on broker {}: {}",
                        topicConfig.getTopicName(), addr, e.getMessage());
                lastError = e;
            }
        }

        if (successCount == 0 && lastError != null) {
            throw lastError;
        }
        return successCount;
    }

    /**
     * Delete a topic from all brokers in the cluster.
     *
     * @param topicName topic name to delete
     * @return number of brokers the topic was deleted from
     */
    public int deleteTopicFromCluster(String topicName) throws Exception {
        Set<String> allAddrs = getAllBrokerAddresses();
        if (allAddrs.isEmpty()) {
            throw new IllegalStateException("No brokers found in the cluster.");
        }

        int successCount = 0;
        Exception lastError = null;
        for (String addr : allAddrs) {
            try {
                mqAdminExt.deleteTopicInBroker(allAddrs, topicName);
                successCount++;
                break; // deleteTopicInBroker handles all brokers at once
            } catch (Exception e) {
                log.warn("Failed to delete topic {} from broker {}: {}",
                        topicName, addr, e.getMessage());
                lastError = e;
            }
        }

        if (successCount == 0 && lastError != null) {
            throw lastError;
        }
        return successCount;
    }

    // ==================== Consumer Group Operations ====================

    /**
     * Create or update a consumer group on all master brokers.
     *
     * @param config subscription group configuration
     * @return number of brokers the group was created/updated on
     */
    public int createConsumerGroupOnAllBrokers(SubscriptionGroupConfig config) throws Exception {
        List<String> masterAddrs = getMasterBrokerAddresses();
        if (masterAddrs.isEmpty()) {
            throw new IllegalStateException("No master brokers found in the cluster.");
        }

        int successCount = 0;
        Exception lastError = null;
        for (String addr : masterAddrs) {
            try {
                mqAdminExt.createAndUpdateSubscriptionGroupConfig(addr, config);
                successCount++;
            } catch (Exception e) {
                log.warn("Failed to create/update consumer group {} on broker {}: {}",
                        config.getGroupName(), addr, e.getMessage());
                lastError = e;
            }
        }

        if (successCount == 0 && lastError != null) {
            throw lastError;
        }
        return successCount;
    }

    /**
     * Delete a consumer group from all brokers.
     *
     * @param groupName consumer group name
     * @return number of brokers the group was deleted from
     */
    public int deleteConsumerGroupFromAllBrokers(String groupName) throws Exception {
        ClusterInfo clusterInfo = getClusterInfo();
        if (clusterInfo == null || clusterInfo.getBrokerAddrTable() == null
                || clusterInfo.getBrokerAddrTable().isEmpty()) {
            throw new IllegalStateException("No brokers found in the cluster.");
        }

        int successCount = 0;
        for (Map.Entry<String, BrokerData> entry : clusterInfo.getBrokerAddrTable().entrySet()) {
            BrokerData brokerData = entry.getValue();
            String brokerAddr = brokerData.selectBrokerAddr();
            if (brokerAddr != null) {
                try {
                    mqAdminExt.deleteSubscriptionGroup(brokerAddr, groupName, true);
                    successCount++;
                } catch (Exception e) {
                    log.warn("Failed to delete consumer group {} on broker {}: {}",
                            groupName, brokerAddr, e.getMessage());
                }
            }
        }
        return successCount;
    }

    // ==================== Utility ====================

    /**
     * Get topic config from the first available broker.
     *
     * @param topicName topic name
     * @return TopicConfig or null if not found
     */
    public TopicConfig examineTopicConfig(String topicName) throws Exception {
        List<String> masterAddrs = getMasterBrokerAddresses();
        for (String addr : masterAddrs) {
            try {
                return mqAdminExt.examineTopicConfig(addr, topicName);
            } catch (Exception e) {
                log.debug("Topic {} not found on broker {}: {}", topicName, addr, e.getMessage());
            }
        }
        return null;
    }

    /**
     * Get subscription group config from the first available broker.
     *
     * @param groupName group name
     * @return SubscriptionGroupConfig or null if not found
     */
    public SubscriptionGroupConfig examineSubscriptionGroupConfig(String groupName) throws Exception {
        ClusterInfo clusterInfo = getClusterInfo();
        if (clusterInfo == null || clusterInfo.getBrokerAddrTable() == null) {
            return null;
        }
        for (Map.Entry<String, BrokerData> entry : clusterInfo.getBrokerAddrTable().entrySet()) {
            BrokerData brokerData = entry.getValue();
            String brokerAddr = brokerData.selectBrokerAddr();
            if (brokerAddr == null) {
                continue;
            }
            try {
                var wrapper = mqAdminExt.getAllSubscriptionGroup(brokerAddr, 10000);
                if (wrapper != null && wrapper.getSubscriptionGroupTable() != null) {
                    SubscriptionGroupConfig cfg = wrapper.getSubscriptionGroupTable().get(groupName);
                    if (cfg != null) {
                        return cfg;
                    }
                }
            } catch (Exception e) {
                log.debug("Failed to get subscription group {} from broker {}: {}",
                        groupName, brokerAddr, e.getMessage());
            }
        }
        return null;
    }
}