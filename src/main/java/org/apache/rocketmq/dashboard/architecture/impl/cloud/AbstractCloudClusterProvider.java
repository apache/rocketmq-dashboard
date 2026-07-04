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

import org.apache.rocketmq.dashboard.architecture.ClusterAccessType;
import org.apache.rocketmq.dashboard.architecture.ClusterProvider;
import org.apache.rocketmq.dashboard.model.CloudProviderConfig;
import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.model.ClusterTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Abstract base class for cloud provider cluster implementations.
 *
 * <p>Per RIP-1 ARCH-01 §5.2.2, cloud providers implement the same {@link ClusterProvider}
 * SPI interface as V4/V5 providers, enabling unified multi-cluster management.</p>
 *
 * <p>Subclasses must implement cloud-specific API calls for:
 * <ul>
 *   <li>Cluster topology discovery</li>
 *   <li>Node health checking</li>
 *   <li>Capability detection</li>
 * </ul>
 * </p>
 *
 * <p>Credential handling:
 * <ul>
 *   <li>AK/SK are received from {@link CloudProviderConfig} and should be used
 *       only for API calls, never logged or persisted in plain text.</li>
 *   <li>Per AUTH-01 §5.4.1, credentials should be encrypted at rest.</li>
 * </ul>
 * </p>
 */
public abstract class AbstractCloudClusterProvider implements ClusterProvider {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** Cloud provider configuration including credentials. */
    protected final CloudProviderConfig config;

    /** Cached cluster topology, refreshed on initialize() or explicitly. */
    protected volatile ClusterTopology cachedTopology;

    /** Cached cluster capability, refreshed on initialize() or explicitly. */
    protected volatile ClusterCapability cachedCapability;

    /** Whether this provider has been initialized. */
    protected volatile boolean initialized = false;

    protected AbstractCloudClusterProvider(CloudProviderConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("CloudProviderConfig must not be null");
        }
        config.validate();
        this.config = config;
    }

    @Override
    public ClusterAccessType getAccessType() {
        return ClusterAccessType.valueOf(config.getProviderType().toUpperCase().replace("-", "_"));
    }

    @Override
    public ClusterTopology getClusterTopology() throws Exception {
        ensureInitialized();
        if (cachedTopology == null) {
            cachedTopology = doDiscoverTopology();
        }
        return cachedTopology;
    }

    @Override
    public ClusterCapability getClusterCapability() throws Exception {
        ensureInitialized();
        if (cachedCapability == null) {
            cachedCapability = doDetectCapability();
        }
        return cachedCapability;
    }

    @Override
    public List<String> getNodeList() throws Exception {
        ensureInitialized();
        return doListNodeIds();
    }

    @Override
    public boolean isClusterHealthy() throws Exception {
        ensureInitialized();
        return doHealthCheck();
    }

    @Override
    public void initialize() throws Exception {
        if (initialized) {
            log.debug("Cloud provider already initialized: {}", config.getProviderType());
            return;
        }
        log.info("Initializing cloud provider: type={}, instance={}, region={}",
            config.getProviderType(), config.getInstanceId(), config.getRegionId());
        doInitialize();
        initialized = true;
        log.info("Cloud provider initialized successfully: {}", config.getProviderType());
    }

    @Override
    public void shutdown() {
        log.info("Shutting down cloud provider: type={}, instance={}",
            config.getProviderType(), config.getInstanceId());
        doShutdown();
        initialized = false;
        cachedTopology = null;
        cachedCapability = null;
    }

    // ==================== Template methods for subclasses ====================

    /**
     * Cloud-specific initialization logic.
     * Subclasses should set up API clients, validate credentials, etc.
     */
    protected abstract void doInitialize() throws Exception;

    /**
     * Cloud-specific shutdown logic.
     * Subclasses should release API client resources.
     */
    protected abstract void doShutdown();

    /**
     * Discover cluster topology from cloud API.
     */
    protected abstract ClusterTopology doDiscoverTopology() throws Exception;

    /**
     * Detect cluster capabilities from cloud API.
     */
    protected abstract ClusterCapability doDetectCapability() throws Exception;

    /**
     * List node identifiers from cloud API.
     */
    protected abstract List<String> doListNodeIds() throws Exception;

    /**
     * Perform health check against cloud API.
     */
    protected abstract boolean doHealthCheck() throws Exception;

    // ==================== Helper methods ====================

    private void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException(
                "Cloud provider not initialized. Call initialize() first. Provider: "
                    + config.getProviderType());
        }
    }

    /**
     * Get the cloud provider config (for subclasses).
     */
    protected CloudProviderConfig getConfig() {
        return config;
    }

    /**
     * Invalidate cached topology and capability, forcing re-fetch on next access.
     */
    public void invalidateCache() {
        this.cachedTopology = null;
        this.cachedCapability = null;
    }
}