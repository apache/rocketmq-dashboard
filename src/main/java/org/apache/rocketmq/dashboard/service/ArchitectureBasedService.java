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
package org.apache.rocketmq.dashboard.service;

import jakarta.annotation.PostConstruct;
import org.apache.rocketmq.dashboard.architecture.AdminClient;
import org.apache.rocketmq.dashboard.architecture.ClusterProvider;
import org.apache.rocketmq.dashboard.architecture.MetadataProvider;
import org.apache.rocketmq.dashboard.config.ArchitectureConfig;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.model.ClusterTopology;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service base class based on new architecture
 * Provides architecture abstraction layer access and capability-driven functionality
 */
@Service
public abstract class ArchitectureBasedService {

    @Autowired
    protected RMQConfigure rmqConfigure;

    @Autowired
    protected ClusterProvider clusterProvider;

    @Autowired
    protected AdminClient adminClient;

    @Autowired
    protected MetadataProvider metadataProvider;

    @Autowired(required = false)
    protected ArchitectureConfig.ArchitectureAdaptationManager adaptationManager;

    protected ClusterCapability clusterCapability;

    @PostConstruct
    public void init() {
        try {
            this.clusterCapability = clusterProvider.getClusterCapability();
        } catch (Exception e) {
            // Use default capability set
            this.clusterCapability = new ClusterCapability();
        }
    }

    /**
     * Check if cluster supports specific capability
     */
    protected boolean supports(String capability) {
        ClusterCapability clusterCapability = resolveClusterCapability();
        if (clusterCapability == null) {
            return false;
        }
        return clusterCapability.hasCapability(capability);
    }

    /**
     * Resolve cluster capability
     * @return
     */
    private ClusterCapability resolveClusterCapability() {
        if (adaptationManager != null) {
            try {
                ClusterCapability dynamic = adaptationManager.getCurrentCapability();
                if (dynamic != null) {
                    return dynamic;
                }
            } catch (Exception e) {
                // fail back to other sources
            }
        }
        if (clusterProvider != null) {
            try {
                ClusterCapability live = clusterProvider.getClusterCapability();
                if (live != null) {
                    this.clusterCapability = live;
                    return live;
                }
            } catch (Exception e) {
                // fail back to other sources
            }
        }
        return clusterCapability;
    }

    /**
     * Check if namespace is supported
     */
    protected boolean supportsNamespace() {
        ClusterCapability cap = resolveClusterCapability();
        return cap != null && cap.isNamespaceSupported();
    }

    /**
 *
 */
    protected boolean supportsLiteTopic() {
        // Resolve live capability: @PostConstruct cached clusterCapability is stale
        // until the user switches architecture (e.g. to V5_PROXY_LOCAL). Reading the
        // cached field here caused LiteTopic to report "not supported" even on 5.x.
        ClusterCapability cap = resolveClusterCapability();
        return cap != null && cap.isLiteTopicSupported();
    }

    /**
 *
 */
    protected boolean supportsPopConsume() {
        ClusterCapability cap = resolveClusterCapability();
        return cap != null && cap.isPopConsumeSupported();
    }

    /**
 *
 */
    protected boolean supportsGrpcClient() {
        ClusterCapability cap = resolveClusterCapability();
        return cap != null && cap.isGrpcClientSupported();
    }

    /**
 *
 */
    protected boolean supportsAclV2() {
        ClusterCapability cap = resolveClusterCapability();
        return cap != null && cap.isAclV2Supported();
    }

    /**
 *
 */
    protected boolean isV4Architecture() {
        ClusterCapability cap = resolveClusterCapability();
        return cap != null && "4.0".equals(cap.getArchitectureVersion());
    }

    /**
 *
 */
    protected boolean isV5Architecture() {
        ClusterCapability cap = resolveClusterCapability();
        return cap != null && "5.0".equals(cap.getArchitectureVersion());
    }

    /**
 *
     */
    protected ClusterTopology getClusterTopology() throws Exception {
        return clusterProvider.getClusterTopology();
    }

    /**
     * Get cluster capability
     * @return
     */
    protected ClusterCapability getClusterCapability() {
        return resolveClusterCapability();
    }

    /**
     * Get metadata provider
     * @return
     */
    protected MetadataProvider getMetadataProvider() {
        if (adaptationManager != null && adaptationManager.getCurrentCapability() != null) {
            return adaptationManager.getMetadataProvider();
        }
        return this.metadataProvider;
    }

    /**
     * Get cluster provider
     * @return
     */
    protected ClusterProvider getClusterProvider() {
        if (adaptationManager != null && adaptationManager.getCurrentCapability() != null) {
            return adaptationManager.getClusterProvider();
        }
        return this.clusterProvider;
    }

    /**
 *
     */
    protected void handleUnsupportedOperation(String operation) {
        ClusterCapability current = resolveClusterCapability();
        throw new UnsupportedOperationException(
            String.format("Operation '%s' is not supported in current cluster architecture (version: %s)",
                operation, current != null ? current.getArchitectureVersion() : "unknown"));
    }

    /**
 *
     */
    protected String getDefaultNamespace() {
        return "DEFAULT";
    }
}