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

import org.apache.rocketmq.dashboard.architecture.ClusterAccessType;
import org.apache.rocketmq.dashboard.architecture.ClusterProvider;
import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.model.ClusterTopology;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 *
 */
public class V4ClusterProvider implements ClusterProvider {

    private static final Logger log = LoggerFactory.getLogger(V4ClusterProvider.class);

    private final MQAdminExt mqAdminExt;

    public V4ClusterProvider(MQAdminExt mqAdminExt) {
        this.mqAdminExt = mqAdminExt;
    }

    @Override
    public ClusterAccessType getAccessType() {
        return ClusterAccessType.V4_NAMESRV;
    }

    @Override
    public ClusterTopology getClusterTopology() throws Exception {
        ClusterInfo clusterInfo = mqAdminExt.examineBrokerClusterInfo();
        return convertToClusterTopology(clusterInfo);
    }

    @Override
    public ClusterCapability getClusterCapability() throws Exception {
        ClusterCapability capability = new ClusterCapability();
        capability.setArchitectureVersion("4.0");
        capability.setNamespaceSupported(false); // Removed
        capability.setLiteTopicSupported(false); // Removed
        capability.setPopConsumeSupported(false); // Removed
        capability.setGrpcClientSupported(false); // Removed
        capability.setAclV2Supported(false);      // Removed
        capability.setDelayMessageSupported(true); // Removed
        capability.setTransactionMessageSupported(true); // Removed
        capability.setFifoMessageSupported(true); // Removed
        
        Set<String> extended = new HashSet<>();
        extended.add("MESSAGE_QUERY");
        extended.add("MESSAGE_QUERY_BY_KEY");
        extended.add("MESSAGE_QUERY_BY_GROUP");
        extended.add("MESSAGE_QUERY_BY_ID");
        extended.add("MESSAGE_QUERY_BY_OFFSET");
        extended.add("OFFSET_SEARCH_BY_TIMESTAMP");
        extended.add("MAX_OFFSET_QUERY");
        extended.add("MIN_OFFSET_QUERY");
        extended.add("MESSAGE_DELETE");
        extended.add("MESSAGE_RESEND");
        extended.add("MESSAGE_CONSUME_DIRECTLY");

        extended.add("TOPIC_CREATE");
        extended.add("TOPIC_DELETE");
        extended.add("TOPIC_UPDATE");
        extended.add("TOPIC_QUERY");

        extended.add("CONSUMER_GROUP_QUERY");
        extended.add("CONSUMER_GROUP_MANAGE");

        extended.add("METRICS_EXPORT");
        extended.add("BROKER_METRICS");
        extended.add("TOPIC_METRICS");
        extended.add("CONSUMER_GROUP_METRICS");
        extended.add("ALL_BROKERS_METRICS");
        extended.add("ALL_TOPICS_METRICS");
        extended.add("CLIENT_METRICS");
        extended.add("SYSTEM_METRICS");
        extended.add("CUSTOM_METRICS");
        extended.add("METRICS_CONFIGURATION");
        capability.setExtendedCapabilities(extended);
        return capability;
    }

    @Override
    public List<String> getNodeList() throws Exception {
        ClusterInfo clusterInfo = mqAdminExt.examineBrokerClusterInfo();
        return clusterInfo.getBrokerAddrTable().keySet().stream().toList();
    }

    @Override
    public boolean isClusterHealthy() throws Exception {
        try {
            mqAdminExt.examineBrokerClusterInfo();
            return true;
        } catch (Exception e) {
            log.warn("Failed to check cluster health", e);
            return false;
        }
    }

    @Override
    public void initialize() throws Exception {
        log.info("Initializing V4 Cluster Provider");
        // Removed
    }

    @Override
    public void shutdown() {
        log.info("Shutting down V4 Cluster Provider");
        // Removed
    }

    /**
 *
     */
    private ClusterTopology convertToClusterTopology(ClusterInfo clusterInfo) {
        ClusterTopology topology = new ClusterTopology();
        topology.setClusterName("default-cluster"); // Removed
        topology.setNamesrvAddresses(clusterInfo.getBrokerAddrTable().values().stream()
            .findFirst()
            .map(brokerData -> brokerData.getBrokerAddrs().values().iterator().next())
            .map(addr -> Collections.singletonList("127.0.0.1:9876")) // Removed
            .orElse(Collections.emptyList()));

        // Removed
        clusterInfo.getBrokerAddrTable().forEach((brokerName, brokerData) -> {
            brokerData.getBrokerAddrs().forEach((brokerId, brokerAddr) -> {
                topology.addNode(brokerName, brokerId, brokerAddr, "BROKER");
            });
        });

        return topology;
    }
}