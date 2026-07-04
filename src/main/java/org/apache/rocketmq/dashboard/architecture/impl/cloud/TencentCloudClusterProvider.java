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

import org.apache.rocketmq.dashboard.model.CloudProviderConfig;
import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.model.ClusterTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * Tencent Cloud (TDMQ) RocketMQ cluster provider implementation (SPI Stub).
 *
 * <p>Tencent Cloud TDMQ for RocketMQ provides a managed RocketMQ service
 * with OpenAPI access via tdmq.tencentcloudapi.com.</p>
 *
 * <h3>Tencent Cloud TDMQ API Mapping</h3>
 * <ul>
 *   <li>DescribeClusters → Cluster topology discovery</li>
 *   <li>DescribeNamespaces → Namespace listing</li>
 *   <li>DescribeTopics → Topic listing</li>
 *   <li>CreateTopic → Topic creation</li>
 *   <li>DeleteTopic → Topic deletion</li>
 *   <li>DescribeGroups → Consumer group listing</li>
 * </ul>
 *
 * <p>Per RIP-1 ARCH-01 §5.1 M1, this is a stub implementation awaiting
 * community contribution for Tencent Cloud SDK integration.</p>
 */
public class TencentCloudClusterProvider extends AbstractCloudClusterProvider {

    private static final Logger log = LoggerFactory.getLogger(TencentCloudClusterProvider.class);

    /** Tencent Cloud TDMQ API endpoint. */
    private static final String DEFAULT_ENDPOINT = "tdmq.tencentcloudapi.com";

    /** Tencent Cloud API version. */
    private static final String API_VERSION = "2022-09-01";

    // TODO: Tencent Cloud SDK client
    // private TdmqClient tencentClient;

    public TencentCloudClusterProvider(CloudProviderConfig config) {
        super(config);
        log.info("TencentCloudClusterProvider created for instance: {}", config.getInstanceId());
    }

    @Override
    protected void doInitialize() throws Exception {
        log.info("[STUB] Initializing Tencent Cloud TDMQ client for region: {}", config.getRegionId());
        // TODO: Initialize Tencent Cloud SDK client
        // Credential credential = new Credential(config.getAccessKey(), config.getSecretKey());
        // HttpProfile httpProfile = new HttpProfile();
        // httpProfile.setEndpoint(config.getEndpoint() != null ? config.getEndpoint() : DEFAULT_ENDPOINT);
        // ClientProfile clientProfile = new ClientProfile();
        // clientProfile.setHttpProfile(httpProfile);
        // this.tencentClient = new TdmqClient(credential, config.getRegionId(), clientProfile);
        log.warn("Tencent Cloud SDK client initialization is STUB - actual SDK integration pending");
    }

    @Override
    protected void doShutdown() {
        log.info("[STUB] Shutting down Tencent Cloud TDMQ client for instance: {}", config.getInstanceId());
        // No cleanup needed for stub
    }

    @Override
    protected ClusterTopology doDiscoverTopology() throws Exception {
        log.info("[STUB] Discovering topology for Tencent Cloud instance: {}", config.getInstanceId());
        // TODO: Call DescribeClusters API
        // DescribeClustersRequest request = new DescribeClustersRequest();
        // DescribeClustersResponse response = tencentClient.DescribeClusters(request);
        // return toClusterTopology(response);
        return new ClusterTopology();
    }

    @Override
    protected ClusterCapability doDetectCapability() throws Exception {
        log.info("[STUB] Detecting capability for Tencent Cloud instance: {}", config.getInstanceId());
        // Tencent Cloud TDMQ supports 5.0 features via OpenAPI
        ClusterCapability capability = new ClusterCapability();
        capability.setNamespaceSupported(true);
        capability.setLiteTopicSupported(true);
        capability.setPopConsumeSupported(true);
        capability.setAclV2Supported(true);
        capability.setGrpcClientSupported(false);       // Cloud uses OpenAPI, not gRPC
        capability.setDelayMessageSupported(true);
        capability.setTransactionMessageSupported(true);
        capability.setFifoMessageSupported(true);
        capability.setArchitectureVersion("5.x-cloud");
        capability.setRocketmqVersion("5.x-tencent");
        if (capability.getExtendedCapabilities() == null) {
            capability.setExtendedCapabilities(new java.util.HashSet<>());
        }
        capability.getExtendedCapabilities().add("topicTypeValidation");
        capability.getExtendedCapabilities().add("metricsQuery");
        capability.getExtendedCapabilities().add("clientTrace");
        return capability;
    }

    @Override
    protected List<String> doListNodeIds() throws Exception {
        log.info("[STUB] Listing node IDs for Tencent Cloud instance: {}", config.getInstanceId());
        // TODO: Call DescribeClusters and extract node IDs
        return Collections.emptyList();
    }

    @Override
    protected boolean doHealthCheck() throws Exception {
        log.info("[STUB] Health check for Tencent Cloud instance: {}", config.getInstanceId());
        // TODO: Call DescribeClusters with minimal parameters to verify connectivity
        return true;
    }
}